package com.example.activity.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.activity.domain.Activities;
import com.example.activity.domain.Comments;
import com.example.activity.dto.*;
import com.example.activity.es.activity.ActivityIndexService;
import com.example.activity.message.Messages;
import com.example.activity.feign.UserServiceClient;
import com.example.activity.pojo.ActivityESSave;
import com.example.activity.pojo.CommentESSave;
import com.example.activity.service.ActivitiesService;
import com.example.activity.vo.ActivityVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.example.activity.es.activity.ActivityIndexService.DEFAULT_PAGE_SIZE;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "活动管理")
public class ActivityController {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Resource
    private ActivitiesService activitiesService;

    @Resource
    private ActivityIndexService activityIndexService;

    @Resource
    private com.example.activity.service.RankingService rankingService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private UserServiceClient userServiceClient;

    @Resource
    private com.example.activity.service.ActivityPhotoService activityPhotoService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private com.example.activity.es.comments.CommentsIndexService commentsIndexService;

    @Resource
    private com.example.activity.service.CommentsService commentsService;

    

    private static final String ACTIVITY_KEY_PREFIX = "activity:";
    private static final String ACTIVITY_CHANGED_SET = "changed:activities";

    @jakarta.annotation.Resource
    private java.util.concurrent.ExecutorService executorService;
    private static final String ACTIVITY_BLOOM_FILTER_KEY = "activity:bloom";

    private RBloomFilter<String> bloomFilter;

    @PostConstruct
    /**
     * 初始化布隆过滤器
     * 作用：在应用启动后为活动ID建立布隆过滤器，用于快速判断ID是否可能存在，减少无效DB访问
     * 流程：构建过滤器 -> 设置容量与误判率 -> 后续新增活动时写入ID
     */
    public void init() {
        this.bloomFilter = redissonClient.getBloomFilter(ACTIVITY_BLOOM_FILTER_KEY);
        this.bloomFilter.tryInit(100000000L, 0.03); // 初始化布隆过滤器
    }

    @GetMapping("/activity/test")
    /**
     * 测试接口
     * 作用：验证网关与活动服务连通性及基础返回格式
     * 流程：直接返回固定成功消息，不依赖认证与业务数据
     */
    public MessageDTO test() {
        Map<String, Object> map = new HashMap<>();
        map.put("msg", "测试接口成功");
        return new SuccessDTO<>(map);
    }
    //逻辑：先添加到数据库-> redisbloom->redis缓存->es
    @PostMapping("/activity")
    /**
     * 新增活动
     * 作用：创建活动并写入缓存与索引，绑定当前用户为作者
     * 流程：参数校验 -> 提取用户ID -> 构建实体并服务层保存 -> 写入布隆过滤器与Redis哈希 -> 提交后异步索引ES -> 返回ID
     */
    public MessageDTO addActivity(@RequestBody ActivityVO activity) throws JsonProcessingException {
        //对活动进行一系列判断
        if (activity == null) {
            return new FailureDTO<>(Messages.BAD_REQUEST, null);
        }

        Activities activities = new Activities(activity);
        Long userId = currentUserId();
        if (userId == null) {
            return new FailureDTO<>(Messages.BAD_REQUEST, "未认证或令牌无效");
        }
        activities.setAuthorId(userId);
        activities.setTime(new Date());
        activities.setCountLikes(0);
        activities.setCountComments(0);
        activities.setCountViews(0);
        activities.setCountFavorite(0);
        // 使用服务层事务统一保存并可选更新几何位置
        Activities savedEntity = activitiesService.saveActivityAndUpdateGeo(activities);
        Map<String, Object> map = new HashMap<>();
        map.put("id", activities.getId());
        //保存失败，返回失败信息
        if (savedEntity == null) {
            return new FailureDTO<>(Messages.BAD_REQUEST, map);
        }
        // 添加到布隆过滤器
        bloomFilter.add(activities.getId().toString());

        boolean b = this.saveActivityHash(activities);// 保存到Redis缓存

        activityIndexService.indexAfterCommit(new ActivityESSave(activities));// 添加到ES
        if (b && savedEntity != null)
            return new SuccessDTO<>(map);
        return new FailureDTO<>(Messages.INTERNAL_SERVER_ERROR, null);
    }

    /**
     * 根据ID查询活动（先进入布隆过滤器，如果有再从缓存中查，如果redis中没有就从mysql查并添加到redis）
     */
    @GetMapping("/activity/{id}")
    /**
     * 根据ID查询活动（缓存优先，DB兜底）
     * 作用：统一从Redis哈希读取活动信息，未命中时加锁回源DB并回填缓存
     * 流程：布隆过滤器校验 -> 读缓存 -> 失败加分布式锁 -> 双检缓存 -> 查DB并补齐作者头像 -> 写回缓存 -> 返回结果
     */
    public MessageDTO getActivityById(@PathVariable Long id) throws JsonProcessingException {
        // 先检查布隆过滤器
        boolean exists = bloomFilter.contains(id.toString());
        if (!exists) {
            return new FailureDTO<>(Messages.NOT_FOUND, "布隆过滤器发力了");
        }

        String key = ACTIVITY_KEY_PREFIX + id;
        Map<Object, Object> cachedJson = stringRedisTemplate.opsForHash().entries(key);
        if (cachedJson != null && !cachedJson.isEmpty()) {
            try {
                Activities cachedActivity = new Activities();
                String sId = (String) cachedJson.get("id");
                if (sId != null) cachedActivity.setId(Integer.parseInt(sId));
                String sAuthorId = (String) cachedJson.get("authorId");
                if (sAuthorId != null) cachedActivity.setAuthorId(Long.valueOf(sAuthorId));
                cachedActivity.setTitle((String) cachedJson.get("title"));
                cachedActivity.setDescription((String) cachedJson.get("description"));
                String sLikes = (String) cachedJson.get("countLikes");
                if (sLikes != null) cachedActivity.setCountLikes(Integer.parseInt(sLikes));
                String sComments = (String) cachedJson.get("countComments");
                if (sComments != null) cachedActivity.setCountComments(Integer.parseInt(sComments));
                String sViews = (String) cachedJson.get("countViews");
                if (sViews != null) cachedActivity.setCountViews(Integer.parseInt(sViews));
                String sFav = (String) cachedJson.get("countFavorite");
                if (sFav != null) cachedActivity.setCountFavorite(Integer.parseInt(sFav));
                String sLat = (String) cachedJson.get("latitude");
                if (sLat != null) cachedActivity.setLatitude(Double.parseDouble(sLat));
                String sLng = (String) cachedJson.get("longitude");
                if (sLng != null) cachedActivity.setLongitude(Double.parseDouble(sLng));
                String sTime = (String) cachedJson.get("time");
                if (sTime != null) {
                    try { cachedActivity.setTime(new java.util.Date(java.time.Instant.parse(sTime).toEpochMilli())); } catch (Exception ignore) {}
                }
                cachedActivity.setTags((String) cachedJson.get("tags"));
                String sTop = (String) cachedJson.get("isTop");
                if (sTop != null) cachedActivity.setIsTop(Integer.parseInt(sTop));
                String sTopTime = (String) cachedJson.get("topTime");
                if (sTopTime != null) {
                    try { cachedActivity.setTopTime(new java.util.Date(java.time.Instant.parse(sTopTime).toEpochMilli())); } catch (Exception ignore) {}
                }
                String cachedImg = (String) cachedJson.get("authorImage");
                cachedActivity.setAuthorImage(cachedImg);
                if ((cachedImg == null || cachedImg.isBlank()) && cachedActivity.getAuthorId() != null) {
                    SuccessDTO<java.util.List<UserBasicDTO>> resp = userServiceClient.basics(String.valueOf(cachedActivity.getAuthorId()));
                    if (resp != null && resp.getData() != null && resp.getData().length > 0) {
                        java.util.List<UserBasicDTO> ul = resp.getData()[0];
                        if (ul != null && !ul.isEmpty()) {
                            cachedActivity.setAuthorImage(ul.get(0).getAvatarUrl());
                        }
                    }
                }
                String sPhotos = (String) cachedJson.get("countPhotos");
                if (sPhotos != null) cachedActivity.setCountPhotos(Integer.parseInt(sPhotos));
                return new SuccessDTO<>(cachedActivity);
            } catch (Exception e) {
                log.warn("Redis缓存反序列化失败，回退DB。key={}, err={}", key, e.getMessage());
            }
        }

        // 进入数据库查询的逻辑
        RLock lock = redissonClient.getLock("activity_lock:" + id); // 创建锁，确保同一时间只有一个线程访问数据库
        try {
            // 响应中断的加锁方式，避免线程无法及时退出
            lock.lockInterruptibly();

            // 加锁后做二次检查（双检），避免并发下重复回源
            Map<Object, Object> cachedAfterLock = stringRedisTemplate.opsForHash().entries(key);
            if (cachedAfterLock != null && !cachedAfterLock.isEmpty()) {
                try {
                    Activities cachedActivity = new Activities();
                    String sId = (String) cachedAfterLock.get("id");
                    if (sId != null) cachedActivity.setId(Integer.parseInt(sId));
                    cachedActivity.setTitle((String) cachedAfterLock.get("title"));
                    cachedActivity.setDescription((String) cachedAfterLock.get("description"));
                    String sLikes = (String) cachedAfterLock.get("countLikes");
                    if (sLikes != null) cachedActivity.setCountLikes(Integer.parseInt(sLikes));
                    String sComments = (String) cachedAfterLock.get("countComments");
                    if (sComments != null) cachedActivity.setCountComments(Integer.parseInt(sComments));
                    String sViews = (String) cachedAfterLock.get("countViews");
                    if (sViews != null) cachedActivity.setCountViews(Integer.parseInt(sViews));
                    String sFav = (String) cachedAfterLock.get("countFavorite");
                    if (sFav != null) cachedActivity.setCountFavorite(Integer.parseInt(sFav));
                    String sLat = (String) cachedAfterLock.get("latitude");
                    if (sLat != null) cachedActivity.setLatitude(Double.parseDouble(sLat));
                    String sLng = (String) cachedAfterLock.get("longitude");
                    if (sLng != null) cachedActivity.setLongitude(Double.parseDouble(sLng));
                    String sTime = (String) cachedAfterLock.get("time");
                    if (sTime != null) {
                        try { cachedActivity.setTime(new java.util.Date(java.time.Instant.parse(sTime).toEpochMilli())); } catch (Exception ignore) {}
                    }
                    cachedActivity.setTags((String) cachedAfterLock.get("tags"));
                    String sTop = (String) cachedAfterLock.get("isTop");
                    if (sTop != null) cachedActivity.setIsTop(Integer.parseInt(sTop));
                    String sTopTime = (String) cachedAfterLock.get("topTime");
                    if (sTopTime != null) {
                        try { cachedActivity.setTopTime(new java.util.Date(java.time.Instant.parse(sTopTime).toEpochMilli())); } catch (Exception ignore) {}
                    }
                    cachedActivity.setAuthorImage((String) cachedAfterLock.get("authorImage"));
                    String sPhotos = (String) cachedAfterLock.get("countPhotos");
                    if (sPhotos != null) cachedActivity.setCountPhotos(Integer.parseInt(sPhotos));
                    return new SuccessDTO<>(cachedActivity);
                } catch (Exception ignore) {}
            }

            // 如果缓存仍未命中，从数据库查询
            Activities activity = activitiesService.getById(id);
            if (activity == null) {
                return new FailureDTO<>(Messages.NOT_FOUND, null);
            }
            if (activity.getAuthorId() != null) {
                try {
                    SuccessDTO<java.util.List<UserBasicDTO>> resp = userServiceClient.basics(String.valueOf(activity.getAuthorId()));
                    if (resp != null && resp.getData() != null && resp.getData().length > 0) {
                        java.util.List<UserBasicDTO> ul = resp.getData()[0];
                        if (ul != null && !ul.isEmpty()) {
                            activity.setAuthorImage(ul.get(0).getAvatarUrl());
                        }
                    }
                } catch (Exception ignore) {}
            }

            // 数据库查询到活动后，保存到Redis缓存
            boolean b = this.saveActivityHash(activity);

            // 返回查询结果
            if (b) {
                return new SuccessDTO<>(activity);
            } else {
                return new FailureDTO<>(Messages.INTERNAL_SERVER_ERROR, null);
            }
        } catch (InterruptedException e) {
            // 恢复中断标记并返回友好提示
            log.warn("获取锁被中断，key={}", key, e);
            Thread.currentThread().interrupt();
            return new FailureDTO<>(Messages.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后再试");
        } finally {
            // 仅在当前线程持有锁时释放
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            } catch (Exception ignore) {}
        }
    }

    /**
     * 根据标题和描述在 ES 中查询活动,只在es中进行查询，返回结果
     */
    @GetMapping("/activity/search")
    /**
     * 关键字搜索活动（ES）
     * 作用：在ES中按标题/描述/标签模糊匹配，返回列表
     * 流程：空关键字返回空 -> 调用ES检索 -> 提取命中源对象 -> 返回
     */
    public MessageDTO searchActivity(@RequestParam(required = false) String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new SuccessDTO<>(Collections.emptyList());
        }
        var results = activityIndexService.searchByKeyword(keyword.trim());
        if (results == null) {
            return new SuccessDTO<>(Collections.emptyList());
        }
        return new SuccessDTO<>(results);
    }

    /**
     * 删除活动，先查布隆过滤器，有的话继续进行，先从缓存删除，再从数据库删除，es删除，最后再删一次缓存
     */
    @DeleteMapping("/activity/{id}")
    /**
     * 删除活动
     * 作用：移除DB记录与相关缓存、索引与榜单数据
     * 流程：布隆过滤器校验 -> 删除Redis哈希 -> 删DB -> 事务后删ES索引 -> 删当日榜单条目 -> 清理图片与延迟删除缓存 -> 返回结果
     */
    public MessageDTO deleteActivity(@PathVariable Integer id) {
        boolean exists = bloomFilter.contains(id.toString());
        if (!exists) {
            return new FailureDTO<>(Messages.NOT_FOUND, "布隆过滤器发力了");
        }
        stringRedisTemplate.delete(ACTIVITY_KEY_PREFIX + id);

        boolean removed = activitiesService.removeById(id);// 删除MySQL数据
        if (!removed) {
            return new FailureDTO<>(Messages.NOT_FOUND, id);
        }

        activityIndexService.deleteAfterCommit(id);// 事务提交后删除 ES 索引
        rankingService.removeActivity(id);// 删除当日榜单中的条目
        try { activityPhotoService.deleteByActivityId(id); } catch (Exception ignore) {}
        activitiesService.delayedCacheDelete(ACTIVITY_KEY_PREFIX + id, 300L);

        return new SuccessDTO<>(id);
    }

    /**
     * 获取所有活动，先从ES中查询，ES未命中 -> 回退MySQL；统一返回MyBatis Page结构
     */
    @GetMapping("/activities")
    /**
     * 活动分页查询（ES优先，DB回退）
     * 作用：先查ES索引命中则返回，否则按ID倒序查询DB并转为统一分页结构
     * 流程：查ES -> 命中返回 -> 校正页码后查DB -> 转换成ES类似结构并批量补齐作者头像 -> 返回
     */
    public MessageDTO getAllActivity(@RequestParam(defaultValue = "1") Integer pageNum) {
        org.springframework.data.domain.Page<ActivityESSave> esPage = activityIndexService.findAllBy(pageNum);
        if (esPage != null && !esPage.isEmpty()) {
            return new SuccessDTO<>(springPageToMybatis(esPage));
        }

        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        Page<Activities> dbPage = new Page<>(pageNum, DEFAULT_PAGE_SIZE);
        log.info("ES未命中，回退到DB查询。pageNum={}", pageNum);

        IPage<Activities> result = activitiesService.page(
                dbPage,
                new QueryWrapper<Activities>()
                        .orderByDesc("id")
        );
        return new SuccessDTO<>(toEsLikePage(result));
    }

    /**
     * ES 未命中 -> 回退 MySQL；统一返回 MyBatis Page 结构
     */
    @GetMapping("/activities/category")
    /**
     * 按分类分页查询（ES优先，DB回退）
     * 作用：与全量分页一致，ES未命中时按分类ID查询DB
     * 流程：查ES -> 命中返回 -> 校正页码后查DB -> 统一分页结构 -> 返回
     */
    public MessageDTO getActivityByCategory(@RequestParam Integer id,
                                            @RequestParam(defaultValue = "1") Integer pageNum) {
        org.springframework.data.domain.Page<ActivityESSave> esPage =
                activityIndexService.findByCategoryId(id, pageNum);
        if (esPage != null && !esPage.isEmpty()) {
            return new SuccessDTO<>(springPageToMybatis(esPage));
        }

        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        Page<Activities> dbPage = new Page<>(pageNum, DEFAULT_PAGE_SIZE);
        log.info("ES未命中（按分类），回退到DB查询。categoryId={}, pageNum={}", id, pageNum);

        IPage<Activities> result = activitiesService.page(
                dbPage,
                new QueryWrapper<Activities>()
                        .eq("category_id", id)
                        .orderByDesc("id")
        );

        return new SuccessDTO<>(toEsLikePage(result));
    }

    /**
     * 将 DB 分页结果转换成与 ES 返回一致的 Page<ActivityESSave>（统一 MyBatis Page 结构）
     */
    private Page<ActivityESSave> toEsLikePage(IPage<Activities> dbPage) {
        /**
         * DB分页转ES风格分页
         * 作用：将MyBatis分页记录转换为ES一致的ActivityESSave结构，并批量补齐作者头像
         * 流程：映射记录 -> 收集作者ID -> 批量调用用户服务 -> 回填头像 -> 返回分页
         */
        Page<ActivityESSave> page = new Page<>(dbPage.getCurrent(), dbPage.getSize(), dbPage.getTotal());
        java.util.List<ActivityESSave> rec = dbPage.getRecords().stream().map(ActivityESSave::new).collect(Collectors.toList());
        java.util.Set<Long> ids = rec.stream().map(ActivityESSave::getAuthorId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!ids.isEmpty()) {
            SuccessDTO<java.util.List<UserBasicDTO>> resp = userServiceClient.basics(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
            java.util.Map<Long, String> m = new java.util.HashMap<>();
            if (resp != null && resp.getData() != null && resp.getData().length > 0) {
                java.util.List<UserBasicDTO> ul = resp.getData()[0];
                if (ul != null) {
                    for (UserBasicDTO u : ul) {
                        if (u.getUserId() != null) m.put(u.getUserId(), u.getAvatarUrl());
                    }
                }
            }
            for (ActivityESSave a : rec) {
                if (a.getAuthorId() != null) a.setAuthorImage(m.get(a.getAuthorId()));
            }
        }
        page.setRecords(rec);
        return page;
    }

    /**
     * 将 Spring Data Page 转换为 MyBatis 的 Page（统一给前端）
     */
    private Page<ActivityESSave> springPageToMybatis(
            org.springframework.data.domain.Page<ActivityESSave> esPage) {
        /**
         * ES分页转MyBatis分页
         * 作用：将ES Page结构统一为前端使用的 MyBatis Page
         * 流程：构造分页对象 -> 写入记录与总数 -> 返回
         */
        Page<ActivityESSave> p = new Page<>(
                esPage.getNumber() + 1L,
                esPage.getSize(),
                esPage.getTotalElements()
        );
        java.util.List<ActivityESSave> rec = new java.util.ArrayList<>(esPage.getContent());
        java.util.Set<Long> ids = rec.stream().map(ActivityESSave::getAuthorId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!ids.isEmpty()) {
            SuccessDTO<java.util.List<UserBasicDTO>> resp = userServiceClient.basics(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
            java.util.Map<Long, String> m = new java.util.HashMap<>();
            if (resp != null && resp.getData() != null && resp.getData().length > 0) {
                java.util.List<UserBasicDTO> ul = resp.getData()[0];
                if (ul != null) {
                    for (UserBasicDTO u : ul) {
                        if (u.getUserId() != null) m.put(u.getUserId(), u.getAvatarUrl());
                    }
                }
            }
            for (ActivityESSave a : rec) {
                if (a.getAuthorId() != null) a.setAuthorImage(m.get(a.getAuthorId()));
            }
        }
        p.setRecords(rec);
        return p;
    }

    /**
     *
     * 将activity存储为redishash结构
     */
    private boolean saveActivityHash(Activities activities) {
        /**
         * 写活动到Redis哈希
         * 作用：将活动主要字段序列化为哈希结构并设置过期，作为高频读取缓存
         * 流程：构建哈希Map -> 过滤空值 -> putAll -> 设置TTL -> 返回成功标识
         */
        String key = ACTIVITY_KEY_PREFIX + activities.getId();
        Map<String, String> hashData = new HashMap<>();
        hashData.put("id", String.valueOf(activities.getId()));
        hashData.put("authorId", String.valueOf(activities.getAuthorId()));
        hashData.put("title", activities.getTitle());
        hashData.put("description", activities.getDescription());
        hashData.put("categoryId", String.valueOf(activities.getCategoryId()));
        hashData.put("countLikes", String.valueOf(activities.getCountLikes()));
        hashData.put("countComments", String.valueOf(activities.getCountComments()));
        hashData.put("countViews", String.valueOf(activities.getCountViews()));
        hashData.put("countFavorite", String.valueOf(activities.getCountFavorite()));
        hashData.put("latitude", String.valueOf(activities.getLatitude()));
        hashData.put("longitude", String.valueOf(activities.getLongitude()));
        hashData.put("time", java.time.Instant.ofEpochMilli(activities.getTime().getTime()).toString());
        hashData.put("tags", activities.getTags());
        hashData.put("isTop", String.valueOf(activities.getIsTop()));
        hashData.put("topTime", activities.getTopTime() != null ? java.time.Instant.ofEpochMilli(activities.getTopTime().getTime()).toString() : null);
        hashData.put("authorImage", activities.getAuthorImage());
        hashData.put("countPhotos", String.valueOf(activities.getCountPhotos()));


        // 过滤掉null值
        Map<String, Object> filteredHashData = hashData.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        stringRedisTemplate.opsForHash().putAll(key, filteredHashData);
        Boolean expire = stringRedisTemplate.expire(key, 1, TimeUnit.HOURS);
        return expire != null && expire;
    }
    /*
    * 点赞活动：添加到redis中
    * */
    @GetMapping("/activity/like/{id}")
    /**
     * 点赞活动（原子）
     * 作用：使用Lua脚本保证原子递增，记录用户点赞集合并更新当日榜单
     * 流程：身份校验 -> 防重复检查 -> 读取或回退DB基数 -> 执行Lua：HINCRBY+SADD+TTL -> 标记changed集合 -> 榜单加分 -> 返回新计数
     */
    public MessageDTO likeActivity(@PathVariable Integer id) {
        String key = ACTIVITY_KEY_PREFIX + id;
        var h = stringRedisTemplate.opsForHash();
        Long userId = currentUserId();
        if (userId == null) {
            return new FailureDTO<>(Messages.BAD_REQUEST, id);
        }
        String ukey = "activity:liked:users:" + id;
        Boolean liked = stringRedisTemplate.opsForSet().isMember(ukey, String.valueOf(userId));
        if (Boolean.TRUE.equals(liked)) {
            return new FailureDTO<>(Messages.BAD_REQUEST, id);
        }
        Object v = h.get(key, "countLikes");
        String baseStr;
        try { baseStr = String.valueOf(Long.parseLong(String.valueOf(v))); } catch (Exception e) {
            Activities db = activitiesService.getById(id);
            if (db == null) return new FailureDTO<>(Messages.NOT_FOUND, id);
            baseStr = String.valueOf(db.getCountLikes() == null ? 0 : db.getCountLikes());
        }
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("local v=redis.call('HGET', KEYS[1], 'countLikes'); if redis.call('SISMEMBER', KEYS[2], ARGV[2])==1 then return -1 end; if not v or tonumber(v)==nil then redis.call('HSET', KEYS[1], 'countLikes', ARGV[1]) end; local new=redis.call('HINCRBY', KEYS[1], 'countLikes', 1); redis.call('EXPIRE', KEYS[1], ARGV[3]); redis.call('SADD', KEYS[2], ARGV[2]); redis.call('EXPIRE', KEYS[2], ARGV[4]); return new");
        script.setResultType(Long.class);
        Long res = stringRedisTemplate.execute(script, java.util.Arrays.asList(key, ukey), baseStr, String.valueOf(userId), String.valueOf(3600), String.valueOf(86400));
        if (res == null) return new FailureDTO<>(Messages.SERVER_ERROR, id);
        if (res == -1L) return new FailureDTO<>(Messages.BAD_REQUEST, id);
        stringRedisTemplate.opsForSet().add(ACTIVITY_CHANGED_SET, String.valueOf(id));
        rankingService.incLikeScore(id);
        try {
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("activityId", id);
            item.put("countLikes", res);
            java.util.List<Long> users = new java.util.ArrayList<>();
            users.add(userId);
            item.put("users", users);
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("batchId", java.util.UUID.randomUUID().toString());
            java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();
            items.add(item);
            payload.put("items", items);
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
            rabbitTemplate.convertAndSend(com.example.activity.config.RabbitMQConfig.EXCHANGE_NAME, com.example.activity.config.RabbitMQConfig.ROUTING_ACTIVITY, json);
        } catch (Exception ignore) {}
        return new SuccessDTO<>(res);
    }

    @GetMapping("/activity/unlike/{id}")
    /**
     * 取消点赞活动（原子）
     * 作用：Lua脚本原子递减并移除用户点赞集合成员
     * 流程：身份校验 -> 执行Lua：若未点赞返回-2 -> 保底置零 -> HINCRBY -1 + SREM + TTL -> 标记changed集合 -> 返回新计数
     */
    public MessageDTO unlikeActivity(@PathVariable Integer id) {
        String key = ACTIVITY_KEY_PREFIX + id;
        
        Long userId = currentUserId();
        if (userId == null) {
            return new FailureDTO<>(Messages.BAD_REQUEST, id);
        }
        String ukey = "activity:liked:users:" + id;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("if redis.call('SISMEMBER', KEYS[2], ARGV[1])==0 then return -2 end; local v=redis.call('HGET', KEYS[1], 'countLikes'); if not v or tonumber(v)==nil then redis.call('HSET', KEYS[1], 'countLikes', '0'); v='0' end; local nv=tonumber(v); if not nv or nv<=0 then redis.call('HSET', KEYS[1], 'countLikes', '0'); redis.call('SREM', KEYS[2], ARGV[1]); redis.call('EXPIRE', KEYS[1], ARGV[2]); redis.call('EXPIRE', KEYS[2], ARGV[3]); return 0 end; local new=redis.call('HINCRBY', KEYS[1], 'countLikes', -1); redis.call('SREM', KEYS[2], ARGV[1]); redis.call('EXPIRE', KEYS[1], ARGV[2]); redis.call('EXPIRE', KEYS[2], ARGV[3]); return new");
        script.setResultType(Long.class);
        Long res = stringRedisTemplate.execute(script, java.util.Arrays.asList(key, ukey), String.valueOf(userId), String.valueOf(3600), String.valueOf(86400));
        if (res == null) return new FailureDTO<>(Messages.SERVER_ERROR, id);
        if (res == -2L) return new FailureDTO<>(Messages.BAD_REQUEST, id);
        stringRedisTemplate.opsForSet().add(ACTIVITY_CHANGED_SET, String.valueOf(id));
        try {
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("activityId", id);
            item.put("countLikes", res);
            java.util.List<Long> unlikes = new java.util.ArrayList<>();
            unlikes.add(userId);
            item.put("unlikes", unlikes);
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("batchId", java.util.UUID.randomUUID().toString());
            java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();
            items.add(item);
            payload.put("items", items);
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
            rabbitTemplate.convertAndSend(com.example.activity.config.RabbitMQConfig.EXCHANGE_NAME, com.example.activity.config.RabbitMQConfig.ROUTING_ACTIVITY, json);
        } catch (Exception ignore) {}
        return new SuccessDTO<>(res);
    }

    private Long currentUserId() {
        /**
         * 解析当前用户ID
         * 作用：优先读取网关注入的 `X-User-Id`，否则解析JWT载荷中的 `sub/userId`
         * 流程：读头 -> 校验与转型 -> JWT解析 -> 返回null或用户ID
         */
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            String uidHeader = attrs.getRequest().getHeader("X-User-Id");
            if (uidHeader != null && !uidHeader.isBlank()) {
                try { return Long.valueOf(uidHeader); } catch (Exception ignore) {}
            }
            String auth = attrs.getRequest().getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String payload = new String(java.util.Base64.getUrlDecoder().decode(auth.substring(7).split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
            var tree = new ObjectMapper().readTree(payload);
            if (tree.has("sub")) return tree.get("sub").asLong();
            if (tree.has("userId")) return tree.get("userId").asLong();
            return null;
        } catch (Exception e) { return null; }
    }
    /**
     * 范围查询：查找附近指定半径（默认10公里）内的活动
     */
    @GetMapping("/activities/nearby")
    /**
     * 附近活动范围查询
     * 作用：按给定经纬度与半径（公里）检索附近活动，使用Haversine公式排序
     * 流程：参数校验 -> 半径转米 -> 调用服务层SQL -> 返回列表或空集合
     */
    public MessageDTO findNearby(@RequestParam Double latitude,
                                 @RequestParam Double longitude,
                                 @RequestParam(defaultValue = "10") Integer radiusKm,
                                 @RequestParam(defaultValue = "50") Integer limit) {
        if (latitude == null || longitude == null) {
            return new FailureDTO<>(Messages.BAD_REQUEST, "latitude/longitude 不能为空");
        }
        int meters = Math.max(1, radiusKm) * 1000;
        var list = activitiesService.findNearby(longitude, latitude, meters, Math.max(1, limit));
        return new SuccessDTO<>(list == null ? java.util.Collections.emptyList() : list);
    }
    @GetMapping("/activities/rank/top")
    /**
     * 活动排行榜TOP N
     * 作用：读取当日ZSet榜单分值，从高到低返回前N个，并尝试补充标题
     * 流程：计算key -> 读取ZSet区间 -> 构造DTO并从缓存取标题 -> 返回列表
     */
    public MessageDTO topRank(@RequestParam(defaultValue = "10") Integer n) {
        var list = rankingService.topN(Math.max(1, Math.min(50, n)));
        return new SuccessDTO<>(list);
    }
    //获取活动详情
    @GetMapping("/activity/detail/{id}")
    public MessageDTO activityDetail(@PathVariable Integer id) {
        CompletableFuture<Activities> f1 =
                CompletableFuture.supplyAsync(() -> loadActivityFromCacheOrDb(id), executorService);

        CompletableFuture<List<String>> f2 =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        List<String> names = activityPhotoService.listObjectNames(id);
                        if (names != null && !names.isEmpty()) {
                            names.sort((a, b) -> {
                                int ia = seqOf(a);
                                int ib = seqOf(b);
                                return Integer.compare(ia, ib);
                            });
                            List<String> urls = new ArrayList<>();
                            for (String name : names) {
                                urls.add(activityPhotoService.presignedUrl(name));
                            }
                            return urls;
                        }
                        return Collections.emptyList();
                    } catch (Exception e) {
                        return Collections.emptyList();
                    }
                }, executorService);

        CompletableFuture<List<CommentESSave>> f3 =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        List<CommentESSave> commentESSaves = commentsIndexService.findByActivityId(id);
                        if (commentESSaves != null) {
                            Set<Long> uids = commentESSaves.stream()
                                    .map(CommentESSave::getUserId)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet());
                            if (!uids.isEmpty()) {
                                var resp = userServiceClient.basics(
                                        uids.stream()
                                                .map(String::valueOf)
                                                .collect(Collectors.joining(","))
                                );
                                Map<Long, String> m = new HashMap<>();
                                if (resp != null && resp.getData() != null && resp.getData().length > 0) {
                                    List<UserBasicDTO> ul = resp.getData()[0];
                                    if (ul != null) {
                                        for (UserBasicDTO u : ul) {
                                            if (u.getUserId() != null) {
                                                m.put(u.getUserId(), u.getAvatarUrl());
                                            }
                                        }
                                    }
                                }
                                for (CommentESSave c : commentESSaves) {
                                    if (c.getUserId() != null) {
                                        c.setUserImage(m.get(c.getUserId()));
                                    }
                                }
                            }
                            return commentESSaves;
                        } else {
                            List<Comments> list = commentsService.list(
                                    new QueryWrapper<Comments>().eq("activity_id", id)
                            );
                            if (list != null) {
                                List<CommentESSave> out = new ArrayList<>();
                                Set<Long> uids = list.stream()
                                        .map(Comments::getUserId)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toSet());
                                Map<Long, String> m = new HashMap<>();
                                if (!uids.isEmpty()) {
                                    var resp = userServiceClient.basics(
                                            uids.stream()
                                                    .map(String::valueOf)
                                                    .collect(Collectors.joining(","))
                                    );
                                    if (resp != null && resp.getData() != null && resp.getData().length > 0) {
                                        List<UserBasicDTO> ul = resp.getData()[0];
                                        if (ul != null) {
                                            for (UserBasicDTO u : ul) {
                                                if (u.getUserId() != null) {
                                                    m.put(u.getUserId(), u.getAvatarUrl());
                                                }
                                            }
                                        }
                                    }
                                }
                                for (Comments c : list) {
                                    CommentESSave ce = new CommentESSave(c);
                                    ce.setUserImage(m.get(c.getUserId()));
                                    out.add(ce);
                                }
                                return out;
                            } else {
                                return Collections.emptyList();
                            }
                        }
                    } catch (Exception e) {
                        return Collections.emptyList();
                    }
                }, executorService);

        CompletableFuture.allOf(f1, f2, f3).join();

        Activities act = f1.join();
        if (act == null) {
            return new FailureDTO<>(Messages.NOT_FOUND, null);
        }

        List<String> photos = f2.join();
        List<CommentESSave> comments = f3.join();

        ActivityDetailDTO dto = new ActivityDetailDTO();
        dto.setActivity(act);
        dto.setPhotos(photos);
        dto.setComments(comments);

        return new SuccessDTO<>(dto);
    }

    private Activities loadActivityFromCacheOrDb(Integer id) {
        String key = ACTIVITY_KEY_PREFIX + id;
        Map<Object, Object> cachedJson = stringRedisTemplate.opsForHash().entries(key);
        if (cachedJson != null && !cachedJson.isEmpty()) {
            try {
                Activities cachedActivity = new Activities();
                String sId = (String) cachedJson.get("id");
                if (sId != null) cachedActivity.setId(Integer.parseInt(sId));
                String sAuthorId = (String) cachedJson.get("authorId");
                if (sAuthorId != null) cachedActivity.setAuthorId(Long.valueOf(sAuthorId));
                cachedActivity.setTitle((String) cachedJson.get("title"));
                cachedActivity.setDescription((String) cachedJson.get("description"));
                String sLikes = (String) cachedJson.get("countLikes");
                if (sLikes != null) cachedActivity.setCountLikes(Integer.parseInt(sLikes));
                String sComments = (String) cachedJson.get("countComments");
                if (sComments != null) cachedActivity.setCountComments(Integer.parseInt(sComments));
                String sViews = (String) cachedJson.get("countViews");
                if (sViews != null) cachedActivity.setCountViews(Integer.parseInt(sViews));
                String sFav = (String) cachedJson.get("countFavorite");
                if (sFav != null) cachedActivity.setCountFavorite(Integer.parseInt(sFav));
                String sLat = (String) cachedJson.get("latitude");
                if (sLat != null) cachedActivity.setLatitude(Double.parseDouble(sLat));
                String sLng = (String) cachedJson.get("longitude");
                if (sLng != null) cachedActivity.setLongitude(Double.parseDouble(sLng));
                String sTime = (String) cachedJson.get("time");
                if (sTime != null) {
                    try { cachedActivity.setTime(new java.util.Date(java.time.Instant.parse(sTime).toEpochMilli())); } catch (Exception ignore) {}
                }
                cachedActivity.setTags((String) cachedJson.get("tags"));
                String sTop = (String) cachedJson.get("isTop");
                if (sTop != null) cachedActivity.setIsTop(Integer.parseInt(sTop));
                String sTopTime = (String) cachedJson.get("topTime");
                if (sTopTime != null) {
                    try { cachedActivity.setTopTime(new java.util.Date(java.time.Instant.parse(sTopTime).toEpochMilli())); } catch (Exception ignore) {}
                }
                String cachedImg = (String) cachedJson.get("authorImage");
                cachedActivity.setAuthorImage(cachedImg);
                if ((cachedImg == null || cachedImg.isBlank()) && cachedActivity.getAuthorId() != null) {
                    SuccessDTO<java.util.List<UserBasicDTO>> resp = userServiceClient.basics(String.valueOf(cachedActivity.getAuthorId()));
                    if (resp != null && resp.getData() != null && resp.getData().length > 0) {
                        java.util.List<UserBasicDTO> ul = resp.getData()[0];
                        if (ul != null && !ul.isEmpty()) {
                            cachedActivity.setAuthorImage(ul.get(0).getAvatarUrl());
                        }
                    }
                }
                String sPhotos = (String) cachedJson.get("countPhotos");
                if (sPhotos != null) cachedActivity.setCountPhotos(Integer.parseInt(sPhotos));
                return cachedActivity;
            } catch (Exception ignore) {}
        }
        Activities activity = activitiesService.getById(id);
        if (activity != null && activity.getAuthorId() != null) {
            try {
                SuccessDTO<java.util.List<UserBasicDTO>> resp = userServiceClient.basics(String.valueOf(activity.getAuthorId()));
                if (resp != null && resp.getData() != null && resp.getData().length > 0) {
                    java.util.List<UserBasicDTO> ul = resp.getData()[0];
                    if (ul != null && !ul.isEmpty()) activity.setAuthorImage(ul.get(0).getAvatarUrl());
                }
            } catch (Exception ignore) {}
        }
        return activity;
    }

    @PostMapping(value = "/activity/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    /**
     * 上传活动图片
     * 作用：校验作者身份后按序号上传图片到对象存储，并更新图片数量
     * 流程：身份校验与活动存在检查 -> 收集文件列表 -> 按序号上传并收集URL -> 更新计数与DB -> 返回URL列表；失败清理已上传对象
     */
    public MessageDTO uploadPhotos(@PathVariable Integer id,
                                   @RequestHeader(value = "Authorization", required = false) String authorization,
                                   @RequestParam(value = "files", required = false) MultipartFile[] files,
                                   @RequestParam(value = "file", required = false) MultipartFile single) {
        Long uid = currentUserId();
        Activities act = activitiesService.getById(id);
        if (uid == null || act == null || act.getAuthorId() == null || !act.getAuthorId().equals(uid)) {
            return new FailureDTO<>(Messages.FORBIDDEN, null);
        }
        java.util.List<MultipartFile> toUpload = new java.util.ArrayList<>();
        if (files != null) for (MultipartFile f : files) if (f != null && !f.isEmpty()) toUpload.add(f);
        if (single != null && !single.isEmpty()) toUpload.add(single);
        if (toUpload.isEmpty()) return new FailureDTO<>(Messages.BAD_REQUEST, "无有效文件");
        int base = act.getCountPhotos() == null ? 0 : act.getCountPhotos();
        java.util.List<String> urls = new java.util.ArrayList<>();
        int i = 1;
        java.util.List<String> uploadedNames = new java.util.ArrayList<>();
        try {
            for (MultipartFile f : toUpload) {
                int seq = base + i;
                String url = activityPhotoService.putPhoto(id, seq, f.getContentType(), f.getInputStream(), f.getSize());
                urls.add(url);
                uploadedNames.add("activity-" + id + "-" + seq + ".jpg");
                i++;
            }
            act.setCountPhotos(base + toUpload.size());
            activitiesService.updateById(act);
            return new SuccessDTO<>(urls);
        } catch (Exception e) {
            e.printStackTrace();
            // 清理已上传的对象，避免脏数据
            try {
                for (String name : uploadedNames) {
                    activityPhotoService.deleteObject(name);
                }
            } catch (Exception ignore) {
                ignore.printStackTrace();
            }
            return new FailureDTO<>(Messages.SERVER_ERROR, "上传失败");
        }
    }

    @GetMapping("/activity/{id}/photos")
    /**
     * 列举活动图片
     * 作用：优先通过前缀列举真实存在的对象，按序号排序并生成预签名URL；不存在时按计数回退生成
     * 流程：查活动 -> 读对象列表并排序 -> 生成URL返回；异常返回失败
     */
    public MessageDTO listPhotos(@PathVariable Integer id) {
        Activities act = activitiesService.getById(id);
        if (act == null) return new FailureDTO<>(Messages.NOT_FOUND, null);
        int n = act.getCountPhotos() == null ? 0 : act.getCountPhotos();
        java.util.List<String> urls = new java.util.ArrayList<>();
        try {
            // 优先按前缀列举真实存在的对象，避免缺失导致异常
            java.util.List<String> names = activityPhotoService.listObjectNames(id);
            if (names != null && !names.isEmpty()) {
                // 按序号排序
                names.sort((a,b) -> {
                    int ia = seqOf(a); int ib = seqOf(b); return Integer.compare(ia, ib);
                });
                for (String name : names) {
                    urls.add(activityPhotoService.presignedUrl(name));
                }
                return new SuccessDTO<>(urls);
            }
            // 回退：按数量生成（可能缺失）
            for (int i = 1; i <= n; i++) {
                String name = "activity-" + id + "-" + i + ".jpg";
                try { urls.add(activityPhotoService.presignedUrl(name)); } catch (Exception ignore) {}
            }
            return new SuccessDTO<>(urls);
        } catch (Exception e) {
            return new FailureDTO<>(Messages.SERVER_ERROR, "获取失败");
        }
    }

    private int seqOf(String name) {
        /**
         * 解析对象名中的序号
         * 作用：从文件名如 `activity-<id>-<seq>.jpg` 提取 `<seq>` 用于排序
         * 流程：定位最后一个 `-` 与 `.` -> 截取并转为整数 -> 异常时返回最大值
         */
        try {
            int dash = name.lastIndexOf('-');
            int dot = name.lastIndexOf('.');
            return Integer.parseInt(name.substring(dash + 1, dot));
        } catch (Exception e) { return Integer.MAX_VALUE; }
    }
}

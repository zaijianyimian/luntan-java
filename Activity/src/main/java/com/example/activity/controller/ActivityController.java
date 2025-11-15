package com.example.activity.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.activity.domain.Activities;
import com.example.activity.dto.FailureDTO;
import com.example.activity.dto.MessageDTO;
import com.example.activity.dto.SuccessDTO;
import com.example.activity.es.activity.ActivityIndexService;
import com.example.activity.message.Messages;
import com.example.activity.pojo.ActivityESSave;
import com.example.activity.service.ActivitiesService;
import com.example.activity.vo.ActivityVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import com.example.filter.generator.mapper.SysUserMapper;
import com.example.filter.generator.domain.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
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
    private SysUserMapper sysUserMapper;

    @Resource
    private ActivitiesService activitiesService;

    @Resource
    private ActivityIndexService activityIndexService;

    @Resource
    private com.example.activity.service.RankingService rankingService;

    @Resource
    private RedissonClient redissonClient;

    

    private static final String ACTIVITY_KEY_PREFIX = "activity:";
    private static final String ACTIVITY_CHANGED_SET = "changed:activities";
    private static final String ACTIVITY_BLOOM_FILTER_KEY = "activity:bloom";

    private RBloomFilter<String> bloomFilter;

    @PostConstruct
    public void init() {
        this.bloomFilter = redissonClient.getBloomFilter(ACTIVITY_BLOOM_FILTER_KEY);
        this.bloomFilter.tryInit(100000000L, 0.03); // 初始化布隆过滤器
    }

    @GetMapping("/activity/test")
    public MessageDTO test() {
        Map<String, Object> map = new HashMap<>();
        map.put("msg", "测试接口成功");
        return new SuccessDTO<>(map);
    }

    @PostMapping("/activity")
    public MessageDTO addActivity(@RequestBody ActivityVO activity) throws JsonProcessingException {
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
     * 根据ID查询活动（Redis缓存 + MySQL兜底）
     */
    @GetMapping("/activity/{id}")
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
                cachedActivity.setAuthorImage((String) cachedJson.get("authorImage"));
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
     * 根据标题和描述在 ES 中查询活动
     */
    @GetMapping("/activity/search")
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
     * 删除活动，先查布隆过滤器，命中则删除缓存，否则返回404，删除成功则删除ES缓存，删除成功则删除Redis缓存，删除成功则返回成功
     */
    @DeleteMapping("/activity/{id}")
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
        activitiesService.delayedCacheDelete(ACTIVITY_KEY_PREFIX + id, 300L);

        return new SuccessDTO<>(id);
    }

    /**
     * ES 未命中 -> 回退 MySQL；统一返回 MyBatis Page 结构
     */
    @GetMapping("/activities")
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
        Page<ActivityESSave> page = new Page<>(dbPage.getCurrent(), dbPage.getSize(), dbPage.getTotal());
        page.setRecords(
                dbPage.getRecords().stream()
                        .map(ActivityESSave::new)
                        .collect(Collectors.toList())
        );
        return page;
    }

    /**
     * 将 Spring Data Page 转换为 MyBatis 的 Page（统一给前端）
     */
    private Page<ActivityESSave> springPageToMybatis(
            org.springframework.data.domain.Page<ActivityESSave> esPage) {
        Page<ActivityESSave> p = new Page<>(
                esPage.getNumber() + 1L,
                esPage.getSize(),
                esPage.getTotalElements()
        );
        p.setRecords(esPage.getContent());
        return p;
    }

    /**
     *
     * 将activity存储为redishash结构
     */
    private boolean saveActivityHash(Activities activities) {
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
    @GetMapping("/activity/like/{id}")
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
        return new SuccessDTO<>(res);
    }

    @GetMapping("/activity/unlike/{id}")
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
        return new SuccessDTO<>(res);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        String username = auth.getName();
        SysUser u = sysUserMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username).eq("deleted", 0));
        return u != null ? u.getId() : null;
    }
    /**
     * 范围查询：查找附近指定半径（默认10公里）内的活动
     */
    @GetMapping("/activities/nearby")
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
    public MessageDTO topRank(@RequestParam(defaultValue = "10") Integer n) {
        var list = rankingService.topN(Math.max(1, Math.min(50, n)));
        return new SuccessDTO<>(list);
    }
}

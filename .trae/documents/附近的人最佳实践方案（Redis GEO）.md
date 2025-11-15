## 目标
- 在 Activity 模块实现“活动排行榜”：评论+2分、点赞+1分，统计在 Redis ZSET 中，整个集合过期时间为24小时，返回前10名。

## Redis 设计
- Key：`rank:activities`
- 结构：ZSET，member=`activityId`（字符串），score=累计分数
- 过期策略：整个 ZSET 设置 TTL=24 小时；仅在首次创建或无 TTL 时设置 `EXPIRE`，避免每次写入刷新过期时间。

## 服务与接口
- 新增 `RankingService`（Activity）：
  - `incLikeScore(activityId)` → ZINCRBY 1；若 `TTL<0` 则 `EXPIRE 24h`
  - `incCommentScore(activityId)` → ZINCRBY 2；若 `TTL<0` 则 `EXPIRE 24h`
  - `topN(n)` → ZREVRANGE WITHSCORES，返回 `List<ActivityRankDTO>`（id, score, 可选title）
- 新增控制器接口：`GET /api/activities/rank/top?n=10`
  - 从 ZSET 取前 n 名；尝试从 `activity:{id}` Redis Hash 读取标题等展示字段；缺失时按需回退 DB 仅取标题（不影响性能的最小读取）。

## 事件挂接（加分入口）
- 点赞成功后加1分：
  - `ActivityController.likeActivity` 成功分支调用 `rankingService.incLikeScore(id)`
- 新增评论成功后加2分：
  - `CommentController.addComment` 成功分支调用 `rankingService.incCommentScore(vo.getActivityId())`
- 取消点赞/删除评论不扣分（保持简单与防刷），如需扣分可后续扩展。

## 细节与校验
- 并发与原子性：Redis ZINCRBY 原子，无需额外锁。
- TTL 管理：每次加分后检查 TTL，仅在 `ttl<0`（未设置）时设置 24h，保证“整个集合”在固定窗口内过期。
- 展示字段：优先从 Redis Hash `activity:{id}` 读取 `title`，减少 DB 读；不存在时仅返回 id 与 score，或轻量 DB 读取标题。

## 验证
- 构建与运行：`mvn -q -DskipTests -pl Activity -am package`
- 手动测试：对某活动执行点赞与评论，再调用 `/api/activities/rank/top`，检查排序与分数是否正确（评论+2、点赞+1）。

## 后续可选增强
- 窗口管理：按日分桶 `rank:activities:{yyyyMMdd}`，避免 TTL 与刷新窗口冲突。
- 防刷：每用户对同一活动的点赞/评论限频配合网关或业务规则。

## 下一步
- 我将添加 `RankingService` 与 `ActivityRankDTO`，新增排行榜接口，并在点赞与评论事件处调用加分方法，随后构建验证。
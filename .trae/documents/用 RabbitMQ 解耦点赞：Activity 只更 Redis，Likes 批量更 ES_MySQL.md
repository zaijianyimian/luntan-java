## 总体架构
- 事件流：Activity 接口只更新 Redis 并记录变更 → 定时批量推送到 RabbitMQ → Likes 批量消费并写 MySQL 与 ES。
- 读取路径：对外查询统一走 ES，满足“获取活动时同时获取评论及点赞数”。

## Activity 模块实现
- 点赞/取消点赞接口（评论与活动）：
  - 仅在 Redis 做原子 `HINCRBY`（含数值校验与防负），并将对应 ID 加入变更集合（`changed:comments`/`changed:activities`）。
- 批量发布器：
  - `@Scheduled(fixedDelay=30s)` 扫描变更集合，读取最新计数，构造批量 payload：
    - 评论：`[{commentId, activityId, countLikes, ts}]`
    - 活动：`[{activityId, countLikes, ts}]`
  - 发送到交换机 `luntanexchange`，路由键 `comment` 与 `activity`；成功后从变更集合移除已处理 ID。
- 配置复用：若 Likes 缺 ES 配置，先从 Activity 的 `spring.elasticsearch` 导入。

## Likes 模块实现
- 删旧类：移除基于 `comment_like` 的 Entity/Mapper/Service/XML。
- 新增模型：
  - `comment_count_like`：`CommentCountLike` + Mapper + XML + Service（字段：`commentId` 主键、`activityId`、`countLikes`、`updatedAt`）。
  - `activity_count_like`（若未建）：同样结构的 Entity/Mapper/XML/Service。
- 批量消费者：
  - `@RabbitListener(queues="commentchannel")` 与 `@RabbitListener(queues="activitychannel")`，将计数合并到内存 Map（最新覆盖）。
- 批量落库与索引：
  - `@Scheduled(fixedDelay=30s)` 执行 UPSERT 到两张计数表，并对 ES 进行 Bulk Update：
    - ES 字段统一写入 `comment-all`。
  - 成功清理已处理项；失败保留重试。

## ES 与索引约定
- 评论与活动索引的点赞计数字段命名统一为 `comment-all`；读取接口直接从 ES 取该字段。
- 若现有索引字段不同，新增或映射至 `comment-all` 并在 Bulk 更新统一写入。

## 数据库与 UPSERT
- MySQL 批量语句：`INSERT ... ON DUPLICATE KEY UPDATE`，批次大小建议 ≤1000；两张表分别 UPSERT。

## 可靠性与幂等
- RabbitMQ 队列持久化、手动 ack；
- 幂等策略：以 ID 作为键，用“最后计数为准”的覆盖逻辑；payload 可包含 `batchId` 做去重（可选）。

## 验证与回滚
- 验证：手动点赞/取消点赞 → 检查 Redis → 消息队列 → Likes 批量入库与 ES Bulk → 对外查询返回点赞数一致。
- 回滚开关：`like.sync.mode=push|direct`，可快速恢复 Activity 直写 DB/ES（保留原分支）。

## 变更清单（关键文件）
- Activity：
  - 修改评论/活动点赞控制器（仅更 Redis + 标记变更集合）。
  - 新增批量发布器类；复用 `RabbitTemplate` 与现有 `RabbitMQConfig`。
- Likes：
  - 删除 `CommentLike` 相关 4 个类和 1 个 XML。
  - 新增 `CommentCountLike`/`ActivityCountLike` 的 Entity/Mapper/Service/XML。
  - 新增批量消费者与批量刷库/ES 更新类；复用 `ElasticsearchOperations` 进行 Bulk。
- 配置：
  - 两模块本地 `application.yml` 完整维护 `spring.rabbitmq`、`spring.elasticsearch`、`spring.datasource`；之后统一导入 Nacos。

## 需要确认
- 批量周期与批次大小（默认 30 秒、每批 ≤1000）。
- 活动索引是否也统一使用 `comment-all` 字段（已按统一命名设计）。
- 删除旧 `comment_like` 后，不再保留用户-评论点赞关系表（如需保留关系，另建表或走旁路逻辑）。
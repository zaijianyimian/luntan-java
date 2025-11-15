## 背景与目标
- RustFS 是一个开源、S3 兼容的高性能对象存储（仍在快速迭代，不建议直接用于生产）[GitHub: rustfs/rustfs](https://github.com/rustfs/rustfs)。
- 目标：在现有系统中统一用 S3 接口接入 RustFS，承载活动图片/附件等对象数据，做到可配置、可观测、易回滚。

## 集成方式（S3 兼容）
- 采用标准 S3 客户端（推荐 MinIO Java SDK 或 AWS SDK v2），配置 `endpoint/accessKey/secretKey/region`，禁用虚拟域名风格（用路径风格）。
- 保持可替换：SDK 层抽象成 `StorageService` 接口，底层驱动可切换到 MinIO/Ceph/OSS 等。

## 代码改动点
- Activity 模块
  - 新增 `StorageService` 与实现 `S3StorageService`：上传（putObject）、删除（removeObject）、生成下载地址（presignedUrl）。
  - 在 `application.yml` 中增加：`storage.endpoint/storage.accessKey/storage.secretKey/storage.bucket/storage.region`（仍用环境变量占位）。
  - 控制器增加上传接口（表单/多文件），保存对象 key 到活动记录（如 `authorImage`/附件列表），并统计 `countPhotos`。
  - 排行/点赞逻辑不受影响，仅在展示时读取对象 URL。
- logreg 模块（可选）
  - 为用户头像也走对象存储：注册或修改头像时上传，返回可访问的 URL。

## 运行与部署
- 本地快速启动 RustFS（Docker）
  - `docker run -d -p 9000:9000 -p 9001:9001 -v ./data:/data -v ./logs:/logs rustfs/rustfs:latest`（按官方 README）
  - 创建 bucket（如 `luntan`），记录 `endpoint`（默认 `http://localhost:9000`）与凭证。
- 配置应用
  - 通过环境变量注入：`STORAGE_ENDPOINT/STORAGE_ACCESS_KEY/STORAGE_SECRET_KEY/STORAGE_BUCKET/STORAGE_REGION`。
  - 启动后在测试接口调用上传，检查对象出现在 RustFS 控制台/目录。

## 安全与可靠性
- 不在代码/仓库中存放明文密钥，统一用环境变量/配置中心。
- SDK 超时与重试策略：短超时+有限重试，失败时统一异常与观测日志。
- 在非生产环境使用 RustFS；生产建议用 MinIO/Ceph 等成熟方案，保持接口不变。

## 验证与观测
- 单元测试：对 `StorageService` 做接口级测试（mock S3）。
- 集成测试：本地上传/下载/删除流程跑通。
- 性能测试：使用 `wrk/k6` 压测上传与下载端点，记录 RT 与错误率。

## 迭代与回滚
- 首次集成仅在 Activity 头像/图片生效；保留现有存储逻辑（若有）作为回退方案。
- 驱动可切换：如需切至 MinIO/OSS，仅改配置与实现绑定，不影响上层。

## 下一步
- 我将：
  1) 在 Activity 模块新增 `StorageService` 抽象与 S3 实现，并加配置读取。
  2) 增加上传/删除/获取URL的控制器端点，并在活动新增时写入对象 key 与计数。
  3) 提供示例配置与本地 Docker 启动脚本说明，完成联调与构建验证。
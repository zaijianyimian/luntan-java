## 目标（按你的约束）
- 仅使用 Redis 完成地理邻近计算与存储（GEO），成员为用户ID。
- 查询得到用户ID列表后，使用 MyBatis 一次性批量查询（List/批量ID）获取用户信息。

## Redis 结构
- Key：`user:geo`（GEOADD/GEOSEARCH，成员为用户ID字符串）
- 可选：`user:geo:ts:{uid}`（最近上报时间戳，用于过滤过期位置；不强制，首版可不加）

## 接口
- `POST /api/user/location`
  - Body：`{ latitude, longitude }`
  - 从 JWT 取当前用户ID
  - 执行：`GEOADD user:geo (lon,lat) userId`
- `GET /api/users/nearby?latitude=&longitude=&radiusKm=10&limit=50`
  - 执行：`GEOSEARCH user:geo BYLONLAT lat lon RADIUS radiusKm km WITHDIST ASC COUNT limit`
  - 获得用户ID列表后：`SysUserMapper.selectBatchIds(ids)`（或 `IN (...)` 批量查询）
  - 返回精简信息：`userId, username, nickname, distanceKm`

## 代码改动
- 新增 `NearbyController`（logreg）：实现上述两个接口
- 新增 `NearbyService`：封装 Redis GEO 操作与 DB 批量查询
- 复用现有 `RedisTemplate<String,Object>` 的 `opsForGeo()`；如需要更简洁，可注入 `StringRedisTemplate`（不强制）

## 规则与过滤
- 半径单位：公里（默认10）
- 排除自己：结果中剔除当前用户ID
- 仅返回未删除用户：`deleted=0`
- 按距离升序：使用 `WITHDIST ASC`，无需应用层排序

## 降级与容错
- Redis 不可用：返回空列表并提示“位置服务不可用”
- 坐标缺失：若未传坐标且也未上报，返回提示引导先上报位置

## 验证
- 多账号上报坐标（纽约示例），查询 10km 范围并校验排序与批量查询是否一次性完成

## 下一步
- 如果你同意，我将按本方案在 logreg 模块添加控制器与服务，完成 Redis GEO 与批量查询落地，代码尽量简洁并附必要注释。
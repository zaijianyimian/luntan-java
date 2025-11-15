## 目标

* 登录后签发的 JWT 不再存储用户名，而是存储用户ID（数值型，稳定唯一）。

* 认证过滤器与下游模块统一基于用户ID建构 `SecurityContext`，保证点赞唯一约束稳定。

## 变更范围

* Filter 模块：`JwtUtil` 与 `JwtAuthenticationFilter`

* LogReg 模块：`JwtUtil` 与 `LogRegController`

* 依赖：注入 `SysUserMapper` 以便通过用户名/ID互查

## 具体修改

### 1) 修改 JwtUtil（Filter 模块）

* 生成：新增/替换 `generateToken(Long userId)`，将 `subject` 设置为 `String.valueOf(userId)`（或同时写入 `claim uid`，但以 `subject` 作为唯一即可）。

* 解析：新增 `getUserId(String token)`，返回 `Long.parseLong(claims.getSubject())`；保留 `validateToken` 不变。

* 兼容：保留现有 `generateToken(String username)` 与 `getUsername`（仅用于过渡）；后续统一切换到 `userId`。

### 2) 修改 JwtAuthenticationFilter（Filter 模块）

* 注入 `SysUserMapper`。

* 流程：

  1. 读 `Authorization: Bearer` token；
  2. 用 `jwtUtil.getUserId(token)` 取用户ID；
  3. `SysUserMapper.selectById(id)` 查到用户名（同时校验 `deleted=0`）；
  4. `dbUserDetailsManager.loadUserByUsername(username)` 获得 `UserDetails`；
  5. 构造 `UsernamePasswordAuthenticationToken` 设置到 `SecurityContext`；

* 日志：移除 `logger.error("jwtutil is" + jwtUtil)` 等无用错误日志，改为 `debug`（可选）。

### 3) 修改 JwtUtil（LogReg 模块）

* 同 Filter 模块一致：新增/替换 `generateToken(Long userId)` 与 `getUserId(String token)`；保持 `validateToken`。

* 秘钥一致性：建议两个模块统一使用同一秘钥配置源（`spring.config` / Nacos），避免跨模块解析不一致；如果当前已一致则无需调整。

### 4) 修改登录控制器（LogReg 模块）

* 注入 `SysUserMapper`。

* 登录流程不变（用用户名+密码认证）；认证成功后：

  * 用用户名查 `SysUser`，取 `id`

  * 调用 `jwtUtil.generateToken(userId)` 签发token

* 返回体不变，仅 token 内载荷由用户名变为用户ID。

## 影响与适配

* 下游控制器（Activity、Comment）均从 `SecurityContext` 读用户名再查库拿 `userId` 的逻辑可优化：直接从 `SecurityContext` 放入用户ID（可用 `Authentication.getPrincipal()` 携带自定义对象，或在 Filter 中设置 `details` 为 `userId`），但此次改动先保持稳定路径：Filter 构建 `UserDetails` 后，Activity 继续用 Mapper 查 `userId`。

* 旧 token 兼容：过渡期可同时支持 `getUsername()` 与 `getUserId()`；完全切换后清理旧方法。

## 校验用例

* 登录签发 token，`subject` 为用户ID；

* 携带 token 访问点赞接口，Filter 解析出用户ID并设置认证；

* 点赞接口从 Mapper 获取用户ID后执行原子脚本（唯一限制、计数增减）；

* Likes 批量持久化 `comment_like` 时使用用户ID，唯一索引 `(comment_id, user_id)` 生效。

## 回滚与风险

* 若外部客户端仍依赖 `subject=用户名`，会解析失败；需在发布前通知客户端或在短期内保留双签发（同时包含 `uid` claim 与 `subject=username`），Filter 优先读 `uid`，无则退回 `subject`。

* 两模块的秘钥必须一致；如不一致，解析将失败。建议统一配置源。

## 交付项

* 修改两个 JwtUtil 类：新增基于用户ID的生成与解析方法；

* 修改 Filter 的 JwtAuthenticationFilter：用用户ID流；

* 修改 LogRegController：登录后按用户ID生成 token；

* 简要单元/集成测试用例说明。


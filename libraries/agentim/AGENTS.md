# AgentIM Android 数据边界

继承根 AGENTS.md 和产品450行规则。`api` 定义独立功能的公开接口/模型；`impl` 实现 HTTP、映射和会话，不导入 feature UI。新配对放 `api/pairing` 和 `impl/pairing`，请求端不含批准接口。私有 verifier/短期 token 不进入 data class、持久存储或日志；公开请求快照只保留协议元数据。HTTP 禁止重定向、cookie/认证头和自动重试配对写操作；取消必须停止所属 Call。契约来源是产品 contracts，不从Kotlin反向生成规范。

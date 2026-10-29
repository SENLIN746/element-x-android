# AgentIM Android 数据边界

继承根 AGENTS.md 和产品450行规则。`api` 定义独立功能的公开接口/模型；`impl` 实现 HTTP、映射和会话，不导入 feature UI。新配对放 `api/pairing` 和 `impl/pairing`，请求端不含批准接口。配对 verifier/短期 token 不进入 data class、持久存储或日志；公开请求快照只保留协议元数据。HTTP 禁止重定向、cookie/认证头和自动重试配对写操作；取消必须停止所属 Call。契约来源是产品 contracts，不从Kotlin反向生成规范。

公开 v2 注册放 `api/onboarding`、`impl/onboarding`。其首次身份恢复证明在发请求前存入 Keystore 加密、noBackup 原子文件；不同于仅驻内存的配对证明。原生会话须在 crypto 初始化前写恢复日志，恢复沿用原 SDK 目录；服务端完成确认后才清除注册证明。不进 UI、Intent、日志、剪贴板或公开产物。

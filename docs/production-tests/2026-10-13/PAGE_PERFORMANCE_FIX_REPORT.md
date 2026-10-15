# PAGE-PERF-FIX-20261013：页面加载加速交付

日期：2026-10-13（马来西亚）。**已完成并部署 v3，生产浏览器验收通过。** [生产登录](https://resource-profile.45-130-165-168.nip.io/login) · 脱敏证据（本地测试产物，不入库：`./PAGE_PERFORMANCE_EVIDENCE.json`） · 浏览器检查（本地测试产物，不入库：`./UI_CHECKS.json`）

## 加速结果

| 登录页 JS/CSS 依赖图 | 旧版 | 最终版 |
|---|---:|---:|
| 原始字节 | 1,085,827 | 495,822 |
| gzip level 9 估算字节 | 299,956 | 157,418 |
| Caddy 实际 HTTP 正文字节 | 315,213 | 164,191 |
| 资源数 | 5 JS + 4 CSS | 5 JS + 4 CSS |

实际传输正文减少 **47.9%**；两阶段预加载无缺失依赖。统计只含匿名登录所需9个JS/CSS，不含HTML、HTTP/TLS开销，也不是页面打开耗时的百分比。已有gzip、哈希assets一年immutable、HTTP2/TLS继续保留。本轮未取得稳定的浏览器冷/暖缓存FCP/LCP采样，不承诺任何网络下的固定打开秒数。

[全局图标](../../../frontend/src/icons.js)从293个改为真实使用的22个；[Vite](../../../frontend/vite.config.js)聚合登录组件与Vue共享核心，表格/图表保留在后台页面按需加载。完全自动拆分方案虽略小，但需更多新资源；最终保留9个登录资源。[预算门](../../../frontend/scripts/check-login-bundle.mjs)已接npm size:check与CI：≤200KiB gzip估算、≤12资源、禁止charts/真实ElTable实现进入登录图。

## 请求等待策略

[request](../../../frontend/src/utils/request.js)/[retry](../../../frontend/src/utils/retry.js)默认standard：普通单次timeout15秒、每个Axios请求总budget30秒，只读请求最多2次250/750ms短退避；POST/PUT/PATCH/DELETE不自动重放，登录POST明确不重试。取消、迟到响应、会话准备、Retry-After和旧401保留安全边界。PDF/CSV二进制下载默认60/90秒且不重试；显式长timeout保留，默认不重试。SSE仍使用原流式路径，不套普通Axios30秒预算。登录后的userInfo属于另一个请求，不能把每请求预算当整个登录流程的30秒上限。

云Docker/compose明确standard与预热false；Render Blueprint仅显式render/true采用长读预算和预热，所有环境写入均不自动重试。不因任意timeout/429宣称冷启动。CDN仍需可控制域名及独立边界验证，本轮未创建外部平台或收费资源。

## 验证中发现并修复的回归

v1静态校验通过，但浏览器首帧未通过，已恢复旧镜像。真实user→auth→request循环测试能复现工厂默认参数提前读取Pinia const的TDZ，已改为延迟getter。[模块循环回归](../../../frontend/tests/request-default-cycle.test.mjs)保留真实三模块、Pinia/Axios及旧写法阴性控制。

v2新页面可渲染，但真实登录未完成，已再次恢复旧镜像。Budget把浏览器原生timer作为实例方法调用，成功响应的finish()清理抛Illegal invocation；Node默认timer和箭头fake-clock此前掩盖了接收者限制。现将timer依赖包装为自由调用，并补[严格接收者回归](../../../frontend/tests/request-policy.test.mjs)。v3先在仅loopback预览中通过真实生产API登录、教师列表、注销，再上线并复验。

最终npm test、lint:check、build、size:check全部通过；请求相关12政策+27实际Axios+3真实模块循环=42检查，会话27检查通过；图标/构建产物负例均通过。发布助手18项离线安全检查通过；远端GitHub CI本轮未触发。真实生产管理员登录/80教师与1200学生统计、教师表格、退出弹窗、学生问卷入口及受限菜单、两角色注销均通过。学生固定数据面板标签是既有RT-06，未列为本轮已修复。

## 部署与回滚

最终镜像：`edu-portrait-kamatera-frontend:prod-page-perf-20261013-v3`；发布完成 `2026-10-13T01:39:46.326946+00:00`。Docker ID `sha256:229b2990fcbe365d3f76149c9e71a8f76395893ceb16b7e72e0b96042cf45c3e`，实际运行manifest `sha256:d7fe18caf80c9e87de0308914ab254b8a9d4a67ae5222f8c14b91fb75baa91d1`。102新静态文件的SHA/bytes/content-type、压缩与缓存通过；33个旧JS仍返回真实JS，37旧文件保留供已打开页面懒加载。17/17 Deployment Ready，前端0重启；其他16部署镜像、Caddy ConfigMap和两证书PVC身份未改。只CAS替换frontend镜像，没有执行数据库迁移/清理或Secret修改。

旧回滚镜像：`edu-portrait-kamatera-frontend:prod-ai-fix-20261011-v2`。v1/v2实际回滚均成功，旧runtime与静态文件核对且登录页恢复；最终v3不回退到失败的v1/v2。必要时在服务器执行 `k3s kubectl -n educare set image deployment/frontend frontend=edu-portrait-kamatera-frontend:prod-ai-fix-20261011-v2`，再检查rollout、登录/表格/注销与17服务就绪。保留现有PVC、配置和数据库。

服务器制品及冻结证据：`/opt/resource-profile/.local-run/page-perf-20261013/prod-page-perf-20261013-v3`。同源公开前端源码103文件归档在`/opt/resource-profile/.local-run/kamatera/page-perf-incoming-20261013-v3/frontend-source.tar`，SHA见证据；服务器主源码目录未覆盖，以后先同步当前已审阅源码再构建，不直接用旧服务器源码重建。工作区大量既有修改保留，本轮未提交或推送。

本轮Groq/Cloudflare模型请求和Agent触发均0；没有测试新的PDF/反馈。既有完整AI400及R-5/R-6/B-2仍未完成。没有凭据、JWT、原始模型正文或客户端登录IP进入交付JSON/截图。

最终生产登录页（本地测试产物，不入库：`./LOGIN.png`）

教师列表截图（本地测试产物，不入库：`./TEACHERS.png`） · 居中退出弹窗（本地测试产物，不入库：`./LOGOUT.png`）

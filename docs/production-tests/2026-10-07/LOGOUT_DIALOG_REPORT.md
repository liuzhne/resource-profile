# 退出确认弹窗样式修复

- 日期 / 标识：2026-10-07 / LOGOUT-DIALOG-CSS-20261007
- 结论：**已修复并部署；生产浏览器视觉与取消、关闭、确认操作全部通过。**
- 代码：[main.js](../../../frontend/src/main.js)。仅增加两个服务组件的官方CSS入口。
- 证据：LOGOUT_DIALOG_RESULTS.json（本地测试产物，不入库：`./LOGOUT_DIALOG_RESULTS.json`）。不含密码、令牌、业务行或日志。

修复后的退出确认框（本地测试产物，不入库：`./LOGOUT_DIALOG_FIXED.jpg`）

显式使用 `ElMessageBox` 和 `ElMessage` 不会触发模板按需组件解析器的样式导入，原生产包没有 message-box CSS。补齐官方 service `style/css` 后自动包含遮罩、按钮、输入框和基础依赖，保持原确认及服务端注销流程。

| 实测 | 修复前 | 修复后 |
|---|---|---|
| 位置（1280×720） | 左上角 0/0 | 430/296；水平/垂直中心误差均0 |
| 卡片 | 1280px全宽、透明 | 420×128px、白色 |
| overlay | static / start | fixed / center |
| 取消/关闭 | 本次先复现取消 | 均关闭弹窗并保留登录 |
| 确认 | 本次不重复注销 | 正式退出并返回登录页 |

93项前端测试、生产构建、JS体积门和main.js lint均通过；公开CSS三个所需选择器存在且资源HTTP200。仅frontend部署为 `prod-fix-20261007-dialog-css`，11:36:19 UTC Ready，原源码及镜像已备份。临时测试账号已退出、浏览器标签已关闭，用户原标签保留。现有页面刷新后加载新CSS。

运行与回滚见 [RUNBOOK](../../../RUNBOOK.md)。本轮没有再触发学生Agent；先前完整验收待授权的状态保持。

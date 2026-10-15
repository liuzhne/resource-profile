# 中文字体（F-1：PDF 报告导出）

2026-10-06 / KAMATERA-DEPLOY-20261006：仓库目前保留已跟踪的历史静态 `NotoSansSC-Regular.ttf`；`.gitignore` 的字体规则不代表这个已跟踪文件不存在。云部署的安全源码包不携带字体，使用单独上传的静态 TrueType 文件和许可证。不要将 OTF 改成 `.ttf` 后缀：当前 OpenHTMLToPDF / PDFBox 要求真正可用的 TrueType 字体。

## 云部署：独立文件与只读挂载

本次字体来自 [Google Fonts 官方固定版本](https://github.com/google/fonts/tree/2894aab31764f10f29c421bdfd2340d3b382d384/ofl/notosanssc)，以 FontTools 4.60.1 将变量 TTF 实例化为 400 静态字体，并保留 OFL.txt。输出具有 `glyf/loca`，不含 `fvar` 或 CFF。

本地部署文件为 `.local-run/kamatera/fonts/NotoSansSC-Regular.ttf`，SHA256 为 `eeb06b8a64fd04a2744d95579db1571b51027cda61ed78c62e4b730791525461`。字体和同目录许可单独通过 SSH 上传；私有 `CLOUD_EXPORT_FONT_FILE` 指向主机字体。共享云 Compose 只读挂载到 `/opt/educare-fonts/NotoSansSC-Regular.ttf`，设置 `EDUCARE_FONT_PATH=file:/opt/educare-fonts/NotoSansSC-Regular.ttf`。详细流程和验证见 [Kamatera 部署手册](../../../../../../docs/deployment/KAMATERA_DEPLOYMENT.md)。

## 本地字体配置

本地可将 `EDUCARE_FONT_PATH` 指向已核验的静态 TrueType 文件；不要默认假设系统 TTC、OTF 或变量字体兼容。

```bash
export EDUCARE_FONT_PATH=file:/absolute/path/NotoSansSC-Regular.ttf
```

服务会把字体复制到导出目录 `.fonts/NotoSansSC-Regular.ttf` 并复用非空缓存。更换字体时先暂停相关导出任务、备份并更新字体缓存，再重启和验证，避免仍使用旧文件。

## 不下载的后果

启动日志会出现 `F-1：未找到中文字体 ...，PDF 中文将显示为方块`。导出仍可工作，
但 PDF 中文字符全部是 □ 占位符。

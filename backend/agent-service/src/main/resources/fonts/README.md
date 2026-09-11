# 中文字体（F-1：PDF 报告导出）

本目录下的 `.ttf` / `.otf` 不进 Git。首次启动 agent-service 之前，按以下任一方式下载思源黑体：

## 方式 A：Google Fonts（最稳定）

```bash
cd backend/agent-service/src/main/resources/fonts
curl -L -o NotoSansSC-Regular.ttf \
    https://github.com/notofonts/noto-cjk/raw/main/Sans/SubsetOTF/SC/NotoSansSC-Regular.otf
# 若上述链接 404，可去 https://fonts.google.com/noto/specimen/Noto+Sans+SC 手动下载 Regular 字重
```

## 方式 B：本地复用系统字体

macOS / Linux 已安装 Source Han Sans / Noto Sans CJK 时，直接软链接：

```bash
ln -s /System/Library/Fonts/Supplemental/PingFang.ttc NotoSansSC-Regular.ttf
```

或通过环境变量覆盖路径：

```bash
export EDUCARE_FONT_PATH=file:/System/Library/Fonts/PingFang.ttc
```

## 不下载的后果

启动日志会出现 `F-1：未找到中文字体 ...，PDF 中文将显示为方块`。导出仍可工作，
但 PDF 中文字符全部是 □ 占位符。

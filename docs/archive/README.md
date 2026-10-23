# 腕上管家 WatchSuite — 开发归档

这里存放「腕上管家」从零到 v1.32 的完整历史资料，用于在任何设备、任何时间点**完整重建**这个项目，不依赖某台具体的电脑。

## 目录

| 路径 | 内容 |
|---|---|
| `chat/腕上管家开发对话记录.md` | 全部 987 条开发对话（Markdown，纯文本可读） |
| `chat/腕上管家开发对话记录.html` | 同上，手机友好的气泡排版，浏览器直接打开 |
| `chat/原始会话日志.jsonl` | 未经加工的原始会话数据（22 MB），最完整的上下文来源 |
| `memory/MEMORY.md` | 项目长期备忘（架构、技术决策、全部踩坑记录） |
| `memory/2026-10-*.md` | 逐日工作日志 |
| `apk/` | 部分历史成品：WatchSuite v1.31 / v1.32、WatchArcade、WatchHR |

完整恢复包（`parts/` 合并后）还额外包含：
- `恢复指南.md` — 6 步恢复流程 + 关键技术坑汇总
- `projects/` — 四个 App 的完整源码，**含 `.git` 历史**
- `apk/` — WatchSuite v1.13 / 1.14 / 1.15 / 1.17 / 1.18 / 1.20 / 1.21 / 1.31 各版本，以及 VoiceClips 成品
- `tools/` — `build-apk.sh` 等零依赖构建脚本

## 关于完整恢复包

`腕上管家_完整恢复包.zip`（206.9 MB）因为超过 GitHub 单文件 100MB 限制，**不在本仓库内**。它同时存放在：

- 开发机的 `outputs/` 与手机归档文件夹中
- Release 附件（如有）

它相对本目录多出的内容：四份文档级恢复资料、`projects/`（含 `.git` 历史的完整源码）、`tools/`（构建脚本）、全部历史 APK（含 VoiceClips 71MB 成品）。

本目录下的 `apk/` + `chat/` + `memory/` 已覆盖日常回溯所需；真要重建开发环境，用本仓库的源码 + `tools/build-apk.sh` 就够了。

## 快速恢复开发环境

见恢复包内的 `恢复指南.md`。三步概括：

1. 解压，把 `projects/` 下四个工程目录拷到新机器任意位置
2. 装 JDK 21 + Android SDK，设好 `ANDROID_HOME`（**不需要装 Gradle**，用 `tools/build-apk.sh`）
3. `VCODE=32 VNAME=1.32 bash tools/build-apk.sh WatchSuite com.watchsuite WatchSuite 30`
   > 版本号必须用环境变量传，位置参数不生效——这是踩过的坑。

## 项目简介

「腕上管家」是一加手表二（OPWW234，466×466 圆屏，Android 11 / API 30，armeabi-v7a）上的本地全能工具箱：文件管理、应用管理、音乐播放（含 WMA 有声书软解）、心率、秒表、计算器、设置等，纯 Java、零第三方依赖（ffmpeg-kit 除外，用于 WMA 转码）。

同系列还有三个独立小项目：WatchArcade（四个表盘小游戏）、WatchHR（心率 HTTP/UDP 广播）、VoiceClips（腕上语音包，645 条音频）。

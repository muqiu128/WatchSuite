# WatchArcade 项目备忘

## 项目
- 圆形表盘（一加手表二 / OPWW234，466×466，Android 11 API 30）上的四个小游戏：贪吃蛇、打砖块（环形布局）、俄罗斯方块（8×12）、跳一跳。
- 工程在 `WatchArcade/`：标准 Android Studio Gradle 工程（AGP 8.13.2 / Gradle 8.14.6 / JDK 21），纯 Java、零第三方依赖。
- 用户偏好：暂停入口=连点计分处 3 次；暂停/结束面板要有「返回主菜单」；退出自动存档、重进续玩（各游戏实现 dumpState/loadState，格式为分号分隔文本）。

## 关键路径
- 快速构建脚本：`tools/build-apk.sh`（aapt2→javac→d8→zipalign→apksigner，不依赖 Gradle；已参数化：`bash build-apk.sh [工程目录] [包名] [APK名] [targetSdk]`，默认 WatchArcade）
- APK 输出：`build/<名字>/<名字>.apk`、`outputs/`
- 本机 SDK：`C:\Users\MuQiu\AppData\Local\Android\Sdk`；JDK 21：PyCharm 自带 jbr；adb：`E:\AndroidTools\platform-tools\adb.exe`
- Gradle 8.14.6：`C:\Users\MuQiu\.workbuddy\binaries\gradle\gradle-8.14.6`（注：assembleDebug 因 PyCharm JBR 无 jlink 而失败，日常继续用 build-apk.sh）

## WatchHR 心率广播（第二个自研 App）
- 工程 `WatchHR/`，包名 com.watchhr，纯 Java，targetSdk 30。前台服务读 TYPE_HEART_RATE，HTTP :8088（/hr 文本、/hr.json），UDP 广播 :45654（JSON→255.255.255.255）。
- `pm grant com.watchhr android.permission.BODY_SENSORS` 可直接授权；BODY_SENSORS 是读心率的前提（开源 SensorServer 就是缺它读不了心率）。
- 圆屏 UI：大字 BPM + 状态行 + 点击启停。

## VoiceClips 腕上语音包（第三个自研 App）
- 工程 `VoiceClips/`，包名 com.voiceclips：把 12 个 `*box` 语音包 App 合并，645 条去重音频按 8 个分类呈现（鸡你太美三类已合并为 224 条一类）。
- 音频与索引在 `app/src/main/assets/{clips/, clips.json}`；build-apk.sh 会自动把 assets 打进包，**媒体扩展名一律 ZIP_STORED 不压缩**（MediaPlayer 从 APK 按 offset/length 直读，压缩条目会静音/打不开）。
- 播放路径：clips.json 的 `f` 已含 "clips/" 前缀，openFd 直接传 `clip.file`，不要再拼前缀。
- 素材来源 APK 存 `voicebox/`；抽取清单 `clips_manifest.json`。
- 语音命名流程：`tools/asr_parallel.py`（4 进程 faster-whisper，模型在 `.models/whisper-small`）→ `tools/build_manifest.py`（识别文本→简体→显示名，CAT_MERGE 可归并分类）。
- 本机 ASR 环境：venv `~/.workbuddy/binaries/python/envs/asr`；**必须用 soundfile 解码**（PyAV 与 faster-whisper 1.2.1 不兼容）；模型从 ModelScope 下（hf-mirror 太慢）。
- 每条音频识别固定 ~13s（CPU 极慢），645 条并行约 1.5 小时。

## 手表调试要点
- 离腕自动息屏：先 `input keyevent KEYCODE_WAKEUP`；调试期间 `svc power stayon usb` 可保持常亮
- **充电座上 OPPO 的 `SysUI.Charging` 浮窗压最顶层**：截图全是充电表盘、点击被吃掉；拔充电器即消失
- `input tap` 单次约 1.6s 延迟；连续手势要合并进一次 `adb shell "..."`
- 启动 app 用 `monkey -p <pkg> -c android.intent.category.LAUNCHER 1`
- USB 连接不稳（会话中多次 offline/掉线），掉线后重插数据线
- 详见 2026-10-16.md 日志

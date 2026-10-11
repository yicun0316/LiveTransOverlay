# 实时同传字幕 (LiveTransOverlay)

一款专为 Android 设计的高性能**实时同传翻译与悬浮字幕**应用。支持**英语 (EN)、日语 (JA)、简体中文 (ZH)** 三语种实时双向同传，具备全局悬浮窗、字幕样式自由定制、系统内录与麦克风双模式音频采集。

---

## 🌟 核心特性

1. **三语种互译实时同传**：
   - 英语 ⇄ 中文、日语 ⇄ 中文、英语 ⇄ 日语 双向同传。
   - 毫秒级流式分词与实时字幕更新。
2. **100% 免费离线本地运行 (开箱即用)**：
   - 核心搭载 **Google ML Kit On-Device Translation** 神经网络引擎。
   - **完全免费、无需 API Key、无字符量限制、零网络依赖、超低功耗与毫秒级延迟**。
   - 翻译在手机本地私密沙盒内完成，保障会议与通话隐私。
3. **全局悬浮窗与穿透模式**：
   - 支持自由拖动、贴边吸附与窗口缩放。
   - **一键穿透锁定 (Touch-Through Mode)**：开启后悬浮窗不拦截触摸手势，刷剧、看直播、玩游戏或回消息时完全不挡手。
   - 悬浮窗内置快捷工具栏（实时状态指示、一键暂停/恢复、语种标识、关闭）。
4. **个性化字幕样式工作台 (所见即所得)**：
   - **高对比度字体颜色**：荧光黄、纯白、赛博青、薄荷绿、珊瑚粉、魅惑紫等多款预设调色板。
   - **字号随心调节**：14sp ~ 28sp 平滑缩放。
   - **背景不透明度**：0% 全透明 ~ 100% 磨砂纯黑半透卡片。
   - **双语对照 / 仅译文切换**：支持“原文+译文双行显示”或“仅显示译文”。
   - **字体描边与边缘阴影**：保证在白色/强光画面背景下依然清晰锐利。
5. **音频采集双模式**：
   - **📱 手机系统内录 (AudioPlaybackCapture API, Android 10+)**：刷 YouTube/B站/TikTok、外语直播或看生肉剧无需外放，无环境杂音，声音直接从声卡内部捕获。
   - **🎙️ 麦克风外部拾音 (AudioRecord)**：适合面对面交流、跨国线下会议或扬声器外放同传。
6. **开放式双引擎架构 (可随时插拔云端 API)**：
   - 默认采用免费离线端侧引擎。
   - 预留 **DeepL Free API** (每月50万字免费) 与 **OpenAI / DeepSeek** 大语言模型同传接口，用户可随时在设置面板填入自定义 Key 进行语境级润色。

---

## 🛠️ 技术架构选型与探讨

### 1. 为什么推荐 Google ML Kit 端侧离线翻译？
| 评估维度 | Google ML Kit (端侧离线) | 商业云端 API (如 DeepL / 百度 / 腾讯) | 大模型 (如 DeepSeek / GPT) |
| :--- | :--- | :--- | :--- |
| **费用** | **100% 永久免费** | 免费额度有限，超量计费 | 按 Token 计费 |
| **网络要求** | **完全离线 (0 网络依赖)** | 强依赖高速稳定网络 | 强依赖高速稳定网络 |
| **响应延迟** | **20ms ~ 60ms (极快)** | 300ms ~ 800ms (受限于RTT) | 1000ms ~ 2500ms (等待首字生成) |
| **模型体积** | 单语言约 30MB (按需动态缓存) | 0MB (云端推理) | 0MB (云端推理) |
| **隐私安全** | 音频与文字绝不离开本地设备 | 需上传至第三方服务器 | 需上传至第三方服务器 |

> **结论**：在实时同传场景下，**端侧离线翻译是体验最顺畅、成本为零、无网络门槛的最佳选择**。本应用将 ML Kit 作为默认引擎，同时支持用户按需扩展大模型。

---

## 📱 项目结构

```
LiveTransOverlay/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/livetrans/overlay/
│       │   ├── LiveTransApplication.kt   # 应用入口与全局单例
│       │   ├── MainActivity.kt           # 权限申请与控制主界面
│       │   ├── data/
│       │   │   ├── Language.kt           # EN, JA, ZH 语种定义与映射
│       │   │   ├── SubtitleStyle.kt      # 字幕样式配置模型与预设
│       │   │   └── AppPreferences.kt     # 用户偏好本地持久化 (Flow)
│       │   ├── engine/
│       │   │   ├── ITranslationEngine.kt           # 翻译引擎抽象接口
│       │   │   ├── LocalMlKitTranslationEngine.kt  # Google ML Kit 离线引擎
│       │   │   ├── CloudTranslationEngine.kt       # DeepL & DeepSeek 云端引擎
│       │   │   ├── TranslationEngineManager.kt     # 双引擎调度与降级熔断
│       │   │   ├── AudioCaptureManager.kt          # 系统内录与麦克风捕获
│       │   │   ├── ISpeechRecognitionEngine.kt     # 语音识别接口
│       │   │   └── AndroidSystemSpeechEngine.kt    # 流式连续语音识别
│       │   ├── service/
│       │   │   └── LiveSubtitleService.kt          # WindowManager 悬浮窗前台服务
│       │   └── ui/
│       │       ├── screens/HomeScreen.kt           # Jetpack Compose 控制台
│       │       └── theme/                          # 现代化深色主题体系
│       └── res/
│           ├── layout/view_floating_subtitle.xml   # 悬浮窗 XML 布局与触摸控制
│           └── values/                             # 颜色、样式与文字资源
```

---

## 🚀 编译与运行指南

### 前置环境要求
- **Android Studio**：Android Studio Hedgehog (2023.1.1) 或更高版本 / Ladybug。
- **JDK**：JDK 17 (已在 Gradle 配置)。
- **Android SDK**：Min SDK 29 (Android 10)，Target SDK 34 (Android 14)。
- **测试设备**：运行 Android 10 及以上系统的真机或模拟器。

### 部署步骤
1. 用 Android Studio 打开目录 `LiveTransOverlay`。
2. 等待 Gradle Sync 完成下载依赖包（包括 ML Kit 与 Jetpack Compose）。
3. 连接手机或开启模拟器，点击 **Run 'app'** 安装到手机。
4. 打开 App 后：
   - 首次启动会引导授予 **“悬浮窗 (在其他应用上层显示)”** 权限。
   - 授予 **“麦克风 / 音频录制”** 权限。
   - 如果选择“系统内录”，系统会弹窗提示确认投屏内录权限。
5. 点击 **“开启悬浮字幕同传”**，悬浮窗即可浮现并自动跟随音频进行翻译！

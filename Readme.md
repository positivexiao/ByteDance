# DoubaoApp

仿豆包风格的 Android AI 聊天助手。支持火山方舟远端大模型与本地 llama.cpp 端侧推理，可在同一 App 内切换；聊天记录通过 Room 本地持久化，API 凭证仅保存在设备端。

项目已完成 **Kotlin Multiplatform（KMP）** 架构迁移：业务逻辑与 Compose UI 集中在 `:shared` 模块，`:app` 仅保留 Android 壳层（Manifest、图标、`MainActivity`）。当前 KMP 仅配置 **Android target**，尚未添加 iOS/Desktop 等目标平台。

## 功能概览

### 聊天与会话

- 多会话列表，单会话聊天界面（Compose Multiplatform + Material 3）
- 文本消息流式输出，上下滑动查看历史
- Room 本地数据库持久化（`doubao_chat_db`，杀进程后仍可恢复）
- 消息互动：复制、点赞、点踩、重新生成
- 助手回复支持追问建议，点击可继续对话

### 远端能力（火山方舟）

需在 **设置** 页配置 API Key 与推理接入点后使用：

| 能力 | 说明 |
|------|------|
| 文本对话 | Chat Completions / Responses API 流式回复 |
| 联网搜索 | Responses API 内置 web_search |
| 图片理解 | 多模态输入（图片 + 文本） |
| 图片生成 | Seedream 接入点生图，结果可保存到相册 |
| 语音输入 | 火山 Talk ASR 流式识别 |
| 语音播报 | 火山 TTS 双向流式合成 |

### 端侧大模型（llama.cpp）

- 设置页选择任意 GGUF 文件，复制到 App 私有目录后加载，可切换为「本地 llama.cpp 模型」
- 本地模式仅支持**文本对话**；图片、联网、生图、语音输入/播报会自动禁用
- 内置 **本地模型性能评测**：一键生成 Markdown 报告（加载耗时、首 token、tokens/s、内存、稳定性、效果题等）

推荐本地模型：**Qwen2.5-0.5B-Instruct Q4_K_M**（约 491 MB，中文表现较好）

---

## 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin |
| 架构 | KMP `:shared` + Android `:app` 壳 |
| UI | Compose Multiplatform + Material 3 |
| 状态管理 | ViewModel + StateFlow |
| 数据层 | Repository 接口（commonMain）+ Room 实现（androidMain） |
| 本地存储 | Room（仅 `androidMain`） |
| 网络 | OkHttp + SSE |
| 远端 LLM | 火山方舟 OpenAI 兼容 API |
| 端侧推理 | [llama.cpp](https://github.com/ggml-org/llama.cpp)（`:llama-android-lib` 子模块） |
| 凭证存储 | EncryptedSharedPreferences |

**环境要求**

- Android **13+**（`minSdk 33`）
- `compileSdk 36`，JDK **17**
- Android SDK + NDK（llama native 库通过 CMake 构建）
- 模拟器支持 **x86_64**，真机建议 **arm64-v8a**，RAM ≥ 8 GB（加载约 500 MB 级 GGUF）

---

## 项目结构

```
Doubao/
├── app/                              # :app Android 应用壳
│   └── src/main/
│       ├── AndroidManifest.xml       # 权限、Application、Activity 声明
│       ├── java/.../MainActivity.kt  # 唯一入口，setContent 加载 CMP UI
│       └── res/                      # 应用图标、strings、themes、备份规则
│
├── shared/                           # :shared KMP 模块（核心业务）
│   └── src/
│       ├── commonMain/kotlin/.../
│       │   ├── data/model/           # ChatMessage、Session 等数据模型
│       │   ├── data/repository/      # ChatRepository 接口
│       │   ├── data/provider/        # LlmProvider 接口、RemoteLlmProvider、ModelSwitchManager
│       │   ├── data/api/             # ApiConfig、ArkImageClient
│       │   ├── data/context/         # SlidingWindowPolicy
│       │   ├── platform/             # AppServices 接口
│       │   └── ui/                   # InputBar、MessageItem、Theme 等共享组件
│       └── androidMain/kotlin/.../
│           ├── DoubaoApp.kt          # Application，装配 Room / Provider / Prefs
│           ├── App.kt              # DoubaoAppContent 根 Composable
│           ├── ChatViewModel.kt    # 聊天状态编排
│           ├── data/db/            # Room（AppDatabase、DAO、Entity）
│           ├── data/repository/    # ChatRepositoryImpl
│           ├── data/provider/      # LocalLlmProvider、VolcSpeech/Tts 等
│           ├── data/prefs/         # SettingsPrefs、ApiCredentialsResolver
│           └── ui/                 # MainScreen、ChatScreen、Settings、SessionList
│
├── llama/llama.cpp/                  # llama.cpp 子模块（git submodule）
│   └── examples/llama.android/lib/   # :llama-android-lib
│
├── patches/
│   └── llama-android-lib.patch       # llama.android 本地构建补丁
│
├── docs/
│   ├── TECHNICAL.md
│   ├── FEATURE_IMPLEMENTATION.md
│   ├── local_llm_report.md
│   └── GEN3_llm_report.md
│
├── models/download.py                # GGUF 下载辅助脚本
├── env.example                       # API 字段说明（不含真实 Key）
├── .gitmodules
└── settings.gradle.kts               # :app + :shared + :llama-android-lib
```

**模块职责**

| 模块 | 职责 |
|------|------|
| `:app` | Android 壳：启动 Activity、应用资源、备份规则 |
| `:shared` | KMP 共享层：`commonMain` 放跨平台契约与 UI 组件，`androidMain` 放 Room、JNI、平台 Provider |
| `:llama-android-lib` | llama.cpp JNI 封装，提供 `InferenceEngine` |

> `local.properties`（SDK 路径）、`*.gguf` 模型权重、`build/` 产物均在 `.gitignore` 中，不入库。

**模型切换架构**

```
DoubaoApp（shared/androidMain）
  └── ModelSwitchManager
        ├── remote → RemoteLlmProvider（火山 Ark API，commonMain）
        └── local  → LocalLlmProvider（llama.cpp JNI，androidMain）
```

设置保存或 App 启动时通过 `reconfigureRemoteServices()` 热更新远端 Provider，无需重启。

---

## 快速开始

### 1. 克隆与编译

```bash
git clone <your-repo-url>
cd Doubao
git submodule update --init --recursive
```

克隆后需对 `llama.android` 应用本地补丁（修改了 context 大小、CMake 参数等，详见 `patches/llama-android-lib.patch`）：

```bash
cd llama/llama.cpp
git apply ../../patches/llama-android-lib.patch
cd ../..
```

Android Studio 打开项目，或命令行：

```bash
./gradlew assembleDebug
```

安装包路径：`app/build/outputs/apk/debug/app-debug.apk`

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 首次编译会构建 llama.cpp native 库，耗时较长属正常现象。

### 2. 配置远端能力

1. 打开 App → **设置**
2. 填写 **Ark API Key**（聊天 / 联网 / 识图 / 生图）
3. 填写 **Talk API Key**（语音识别 / 播报）
4. 填写 **文本对话**、**图片生成** 接入点 ID（`ep-xxx`，在[火山方舟控制台](https://console.volcengine.com/ark)创建）
5. 点击 **保存**

Key 仅存于本机加密存储，**不会**写入源码或 APK。字段说明见 [`env.example`](env.example)。

### 3. 配置本地 GGUF 模型

App **不内置**模型权重，需自行下载 `.gguf` 后导入。

#### 推荐模型

| 模型 | Hugging Face | 推荐文件 | 说明 |
|------|--------------|----------|------|
| Qwen2.5-0.5B-Instruct | [Qwen/Qwen2.5-0.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF) | `qwen2.5-0.5b-instruct-q4_k_m.gguf` | 默认推荐，约 491 MB |
| Qwen2.5-0.5B-Instruct | 同上 | `qwen2.5-0.5b-instruct-q4_0.gguf` | 更小，质量略低 |
| Qwen2.5-0.5B-Instruct | 同上 | `qwen2.5-0.5b-instruct-q5_k_m.gguf` | 质量更好，更占内存 |
| Gemma 3 270M IT | [unsloth/gemma-3-270m-it-GGUF](https://huggingface.co/unsloth/gemma-3-270m-it-GGUF) | `gemma-3-270m-it-Q4_K_M.gguf` | 更小更快，中文通常弱于 Qwen |

#### 下载与导入

**脚本（项目自带）：**

```bash
python models/download.py
```

**浏览器：**

1. 打开 Hugging Face 仓库 → **Files and versions** → 下载 `.gguf`
2. 传到手机：

```powershell
adb push ".\qwen2.5-0.5b-instruct-q4_k_m.gguf" "/sdcard/Download/"
```

3. App **设置** → **选择 GGUF 模型文件** → **加载本地模型** → 切换到 **本地 llama.cpp 模型**

> 安装后可在设置页随时更换其他 GGUF 文件，无需重装 APK。须为 llama.cpp 支持的架构，且设备内存足够。

---

## 本地模型性能评测

路径：**设置 → 本地模型性能评测**

1. 确保已在设置页选择 GGUF 并复制到 App 私有目录
2. 点击 **开始性能评测**（约 2–8 分钟，请勿切换应用）
3. 完成后 **复制 Markdown 报告**

报告包含：设备信息、模型加载/复制耗时、首 token 延迟、llama.cpp 原生 bench（pp/tg tokens/s）、PSS 内存、5 轮稳定性、可选 8 道效果题。

详细说明见 [`docs/local_llm_report.md`](docs/local_llm_report.md)。

**当前推理参数**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| context | 2048 | `ai_chat.cpp`（补丁后；上游默认 8192） |
| max predict | 512 | Kotlin `LocalConfig.predictLength` |
| temperature | 0.7 | C++ sampler（补丁后；上游默认 0.3） |
| topP | 0.9 | 设置页展示，native 暂未暴露修改接口 |
| threads | 2–4 | 按 CPU 核数自动选取 |

更换 GGUF 文件可在设置页操作；修改 context / 温度等需改 `patches/llama-android-lib.patch` 对应源码后重新编译。

---

## 安全说明

- Ark / Talk API Key 使用 **EncryptedSharedPreferences**（`doubao_secure_settings`）加密存储
- 编译时**不会**将 Key 注入 `BuildConfig` 或 APK
- 系统备份规则排除：加密 prefs、Room 聊天数据库（`backup_rules.xml`、`data_extraction_rules.xml`）

**提交 GitHub 前请确认：**

- [ ] 未提交 `*.apk` / `*.aab` / `build/` 目录
- [ ] 未提交 `local.properties`、`local.env`、`.env`、真实 API Key
- [ ] 已提交 `shared/` 模块、`.gitmodules`、`patches/`
- [ ] 克隆后执行 `git submodule update --init` 并应用 `patches/llama-android-lib.patch`
- [ ] 若曾泄露 Key 或旧版 APK，请在火山控制台**轮换 Key**

---

## 常见问题

**远端聊天提示配置 Key？**  
在设置页填写 Ark Key 与文本对话接入点 `ep-xxx` 并保存。

**本地模型加载成功但对话报错？**  
llama.android 要求 `setSystemPrompt()` 仅在 `loadModel()` 之后调用一次。项目已在 `LocalLlmProvider` 中按此约束实现；若仍失败，请到设置页重新加载模型，或查看 Logcat 中 `InferenceEngineImpl` 日志。

**本地模型加载失败？**  
确认 GGUF 未损坏、架构受 llama.cpp 支持、设备内存充足；真机优先使用 arm64。

**本地模式为何不能发图/联网？**  
本地链路仅实现文本对话，相关能力在 `modelSource == "local"` 时由 `ChatViewModel` 禁用。

**克隆后编译 llama 模块失败？**  
确认已初始化子模块并应用 `patches/llama-android-lib.patch`。

**评测里部分题目回答质量差？**  
0.5B 小模型在计算/续写/总结类问题上能力有限，与远端大模型存在差距属正常现象。

---

## 许可证与致谢

- 端侧推理基于 [llama.cpp](https://github.com/ggml-org/llama.cpp)（MIT），通过 `:llama-android-lib` 集成
- 远端能力依赖[火山引擎方舟](https://www.volcengine.com/product/ark)及语音服务，使用前需自行开通并遵守其服务条款

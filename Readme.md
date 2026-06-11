# DoubaoApp

仿豆包风格的 Android AI 聊天助手。支持火山方舟远端大模型与本地 llama.cpp 端侧推理，可在同一 App 内切换；聊天记录本地持久化，API 凭证仅保存在设备端。

## 功能概览

### 聊天与会话

- 多会话列表，单会话聊天界面（Jetpack Compose）
- 文本消息流式输出，上下滑动查看历史
- Room 本地数据库持久化（杀进程后仍可恢复）
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

- 设置页选择任意 GGUF 文件，加载后可切换为「本地 llama.cpp 模型」
- 本地模式仅支持**文本对话**；图片、联网、生图、语音输入/播报会自动禁用
- 内置 **本地模型性能评测**：一键生成 Markdown 报告（加载耗时、首 token、tokens/s、内存、稳定性、效果题等）

推荐本地模型：**Qwen2.5-0.5B-Instruct Q4_K_M**（约 491 MB，中文表现较好）

---

## 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | ViewModel + Repository + Provider 抽象 |
| 本地存储 | Room |
| 网络 | OkHttp + SSE |
| 远端 LLM | 火山方舟 OpenAI 兼容 API |
| 端侧推理 | [llama.cpp](https://github.com/ggerganov/llama.cpp)（`:llama-android-lib` 模块） |
| 凭证存储 | EncryptedSharedPreferences |

**环境要求**

- Android **13+**（`minSdk 33`）
- `compileSdk 36`，JDK **17**
- 编译需 Android SDK + NDK（llama native 库 CMake 构建）
- 真机建议 **arm64-v8a**，RAM ≥ 8 GB（加载约 500 MB 级 GGUF）

---

## 项目结构

```
Doubao/
├── app/                              # 主应用
│   └── src/main/java/.../
│       ├── ui/                       # 聊天、会话、设置、性能评测
│       ├── data/
│       │   ├── provider/             # RemoteLlm / LocalLlm / 语音 Provider
│       │   ├── benchmark/            # 端侧性能评测
│       │   ├── prefs/                # 设置与加密 Key
│       │   ├── db/                   # Room
│       │   └── api/                  # Ark / 生图客户端
│       ├── ChatViewModel.kt
│       └── DoubaoApp.kt
├── llama/llama.cpp/                  # llama.cpp 源码
│   └── examples/llama.android/lib/   # → :llama-android-lib
├── docs/
│   ├── local_llm_report.md           # 评测报告模板与说明
│   └── GEN3_llm_report.md            # 真机评测示例（可选）
├── models/download.py                # 模型下载辅助脚本（可选）
└── env.example                       # 配置字段说明（无真实 Key）
```

**模型切换架构**

```
DoubaoApp
  └── ModelSwitchManager
        ├── remote → RemoteLlmProvider（火山 Ark API）
        └── local  → LocalLlmProvider（llama.cpp JNI）
```

设置保存或 App 启动时通过 `reconfigureRemoteServices()` 热更新远端 Provider，无需重启。

---

## 快速开始

### 1. 克隆与编译

```bash
git clone <your-repo-url>
cd Doubao
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

**浏览器：**

1. 打开 Hugging Face 仓库 → **Files and versions** → 下载 `.gguf`
2. 传到手机：

```powershell
adb push ".\qwen2.5-0.5b-instruct-q4_k_m.gguf" "/sdcard/Download/"
```

3. App **设置** → **选择 GGUF 模型文件** → **加载本地模型** → 切换到 **本地 llama.cpp 模型**

**Hugging Face CLI：**

```powershell
pip install -U huggingface_hub
huggingface-cli download Qwen/Qwen2.5-0.5B-Instruct-GGUF qwen2.5-0.5b-instruct-q4_k_m.gguf --local-dir .
adb push ".\qwen2.5-0.5b-instruct-q4_k_m.gguf" "/sdcard/Download/"
```

> 安装后可在设置页**随时更换**其他 GGUF 文件，无需重装 APK。须为 llama.cpp 支持的架构，且设备内存足够。

---

## 本地模型性能评测

路径：**设置 → 本地模型性能评测**

1. 确保已在设置页选择 GGUF 并复制到 App 私有目录
2. 点击 **开始性能评测**（约 2–8 分钟，请勿切换应用）
3. 完成后 **复制 Markdown 报告**

报告包含：设备信息、模型加载/复制耗时、首 token 延迟、llama.cpp 原生 bench（pp/tg tokens/s）、PSS 内存、5 轮稳定性、可选 8 道效果题。

详细说明见 [`docs/local_llm_report.md`](docs/local_llm_report.md)。

**当前推理参数（native 层固定，设置页仅作说明）**

| 参数 | 默认值 | 是否可在 App 内修改 |
|------|--------|---------------------|
| context | 2048 | 否（`ai_chat.cpp`） |
| max predict | 512 | 否（Kotlin `LocalConfig`） |
| temperature | 0.7 | 否（C++ sampler） |
| threads | 2–4（按 CPU） | 否（C++） |

更换 GGUF 文件路径可在设置页操作；修改 context / 温度等需改 native 代码并重新编译。

---

## 安全说明

- API Key 使用 **EncryptedSharedPreferences** 加密存储
- 编译时**不会**将 Key 注入 `BuildConfig` 或 APK
- 系统备份规则中排除加密 prefs 文件

---

## 常见问题

**远端聊天提示配置 Key？**  
在设置页填写 Ark Key 与文本对话接入点 `ep-xxx` 并保存。

**本地模型加载失败？**  
确认 GGUF 未损坏、架构受 llama.cpp 支持、设备内存充足，并使用 arm64 真机。

**本地模式为何不能发图/联网？**  
本地链路仅实现文本对话，相关能力在本地模式下 intentionally 禁用。

**评测里部分题目回答质量差？**  
0.5B 小模型在计算/续写/总结类问题上能力有限；评测已做每题重置上下文与 prompt 优化，仍可能与远端大模型有差距。

---

## 许可证与致谢

- 端侧推理基于 [llama.cpp](https://github.com/ggerganov/llama.cpp)（MIT），通过 `llama-android-lib` 集成
- 远端能力依赖[火山引擎方舟](https://www.volcengine.com/product/ark)及语音服务，使用前需自行开通并遵守其服务条款

<div align="center">

<img src="https://raw.githubusercontent.com/heit-233/goal/main/docs/screenshots/windows-home.png" width="880" alt="goal 电脑端主页">

# goal

**把目标，做小一点。**

免费、离线、可扩展的任务计划软件  
Windows 10 / 11 · Android 8.0+

[![version](https://img.shields.io/badge/version-1.0.0-D9653B?style=flat-square)](https://github.com/heit-233/goal/releases/tag/v1.0.0)
[![Windows](https://img.shields.io/badge/Windows-10%20%2F%2011-0078D4?style=flat-square)](#下载)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square)](#下载)
[![offline](https://img.shields.io/badge/offline--first-4C8C6A?style=flat-square)](#数据与隐私)

[下载](#下载) · [功能](#功能) · [截图](#截图) · [插件](#插件) · [隐私](#数据与隐私) · [完整文档](docs/README.md)

</div>

---

## 下载

| 平台 | 文件 | 说明 |
| --- | --- | --- |
| **Windows 10 / 11 x64** | [goal-1.0.0-setup.exe](https://github.com/heit-233/goal/releases/download/v1.0.0/goal-1.0.0-setup.exe) | 安装版，约 42 MB；创建开始菜单快捷方式，可在“应用和功能”中卸载 |
| **Android 8.0+** | [goal-1.0.0.apk](https://github.com/heit-233/goal/releases/download/v1.0.0/goal-1.0.0.apk) | 约 87 KB；使用同一签名密钥，可直接覆盖安装旧版 |

> 校验值见 [SHA256SUMS.txt](https://github.com/heit-233/goal/releases/download/v1.0.0/SHA256SUMS.txt)。
>
> 安装包未做商业代码签名，Windows SmartScreen 可能提示“未知发布者”。选择“更多信息 → 仍要运行”即可。安装为当前用户级别，不需要管理员权限。

### Windows 安装

1. 下载 `goal-1.0.0-setup.exe` 并运行。
2. 安装程序默认装到 `%LOCALAPPDATA%\Programs\goal`，不需要管理员权限。
3. 安装完成后从开始菜单启动 goal。卸载时不会删除你的计划数据。

### Android 安装

1. 下载 `goal-1.0.0.apk`。
2. 在系统提示中允许当前文件管理器或浏览器“安装未知应用”。
3. 安装后打开 goal；Android 13 及以上首次启动会请求通知权限。

---

## 功能

### 三种完成方式，加一个“一键完成”

| 方式 | 怎么算完成 |
| --- | --- |
| **提交** | 先添加文件、视频、音频或图片等材料，再提交完成 |
| **时长** | 开始计时，达到设定时长后自动完成；离开页面也会继续累计 |
| **变化** | 用 − / + 调整当前数值，达到目标后自动完成 |
| **一键完成** | 不经过上述判定，直接标记完成 |

提交的材料会复制到应用私有目录，**只保存在本机，不会上传**。备份 JSON 只记录材料名称、类型、大小等元数据，不内嵌文件本身。

### 电脑端

- 左侧栏：主页 / 插件 / 设置；添加按钮在内容区右下角。
- 右键任务卡：编辑计划、一键完成 / 恢复为进行中、删除计划。
- 快捷键：`Ctrl + N` 新建计划，`Ctrl + 1 / 2 / 3` 切换主页 / 插件 / 设置。
- 单实例运行：重复启动会激活已打开的窗口，避免两个进程同时写同一份数据。
- 记住窗口大小、位置和最大化状态。
- 自动保存：只有计时进行中才按 12 秒节流写入，减少不必要的磁盘写入。

### 手机端

- 底部栏：主页 / 插件 / 设置；长按任务卡打开编辑 / 完成 / 删除菜单。
- 提交材料可以在系统相册、视频或文件应用中直接打开。
- 每天 9:00 的到期提醒，可在设置中关闭；重启手机后自动恢复。
- 深色模式与强调色会同步到状态栏、导航栏和弹层。

### 主题与外观

内置暖砂、苔原、远海、暮紫、山茶、素墨六套主题，支持深色模式和自定义 HEX 强调色。动画短而轻，不做夸张的弹跳和闪烁。

---

## 截图

<div align="center">

<img src="https://raw.githubusercontent.com/heit-233/goal/main/docs/screenshots/windows-home.png" width="48%" alt="主页">
<img src="https://raw.githubusercontent.com/heit-233/goal/main/docs/screenshots/windows-editor.png" width="48%" alt="编辑计划">

<img src="https://raw.githubusercontent.com/heit-233/goal/main/docs/screenshots/windows-plugins.png" width="48%" alt="插件">
<img src="https://raw.githubusercontent.com/heit-233/goal/main/docs/screenshots/windows-settings.png" width="48%" alt="设置">

</div>

---

## 数据与隐私

- 没有账号、广告、云同步、遥测，也没有付费功能。
- 电脑端数据：`%APPDATA%\goal\data.json`
- 手机端数据：应用私有目录 `files/goal-data.json`
- 电脑端材料：`%APPDATA%\goal\proofs\<任务 id>`
- 手机端材料：`files/proofs/<任务 id>`
- 导出：一份可读 JSON，包含用户信息、配置和任务。
- 导入：先校验格式，再选择“覆盖”或“合并”；合并只追加本地不存在的任务 id。
- 联网只发生在两处：检查 / 下载更新，以及你主动点击的插件链接。插件链接的域名必须写在插件清单的 `network` 中。

> 备份 JSON 不包含材料二进制文件。换设备导入后，材料需要重新添加。

---

## 插件

插件是声明式的 `goal-panel` JSON 面板，**不执行任何插件代码**。支持从本地 `.zip` 安装；安装前展示说明、网络权限和责任声明，安装后默认关闭。

插件面板支持：标题、文本、引用、分隔线、清单、链接、添加计划。链接只能打开 `http(s)`，且域名必须在清单中声明。

> 插件由第三方作者独立发布，与《goal》原作者无关；用户添加插件后，应自行判断插件内容及浏览信息是否合规，原作者不承担由此产生的任何责任。

示例插件在 [examples/plugins](examples/plugins)，格式说明见 [docs/README.md](docs/README.md)。

---

## 从源码构建

### Windows

需要 .NET 10 SDK。

```powershell
dotnet publish windows/Goal/Goal.csproj -c Release -r win-x64 --self-contained true `
  -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true `
  -p:DebugType=embedded -o publish/win-x64
```

生成安装包需要 [Inno Setup 6](https://jrsoftware.org/isinfo.php)：

```powershell
ISCC.exe installer\goal.iss
```

### Android

需要 JDK 17、Android SDK 34 和 Gradle 8.9。

```bash
cd android
gradle assembleRelease
```

签名配置从 `android/keystore.properties` 读取（该文件不提交仓库）；首次构建请按 [docs/README.md](docs/README.md) 填写，发布前请替换为你自己的 keystore。

---

## 已知限制

- Windows 安装包未做代码签名，SmartScreen 可能提示未知发布者。
- Android 的每日提醒使用系统闹钟；开启省电限制或“强行停止”后，提醒可能延迟，直到下次打开应用或重启。
- 材料只在本机，不随 JSON 备份迁移；换设备后需要重新添加。
- 更新清单地址默认指向 `heit-233/goal`。Fork 后请同时修改两端代码中的仓库地址和 `update.json` 下载地址。
- 没有账号与云同步；多设备之间请手动导出 / 导入 JSON。

---

## 版本 1.0.0

- 第一个公开版本：提交 / 时长 / 变化三种完成方式与一键完成。
- 提交材料、每日到期提醒、六套主题、深色模式、自定义强调色。
- 声明式插件面板，本地安装、默认关闭、域名白名单。
- 单份可读 JSON 导出 / 导入，支持覆盖与合并。
- 本次发布补充：Windows 安装包、单实例保护、窗口位置记忆、减小自动保存写入、保存失败提示、Android 材料直接打开、标签页状态恢复。

---

<div align="center">

**goal** · 把目标，做小一点。

如果这个项目对你有帮助，欢迎在 GitHub 点一个 Star。

</div>
# Goal

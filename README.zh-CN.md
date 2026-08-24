# Per-App Language

[English](README.md) | 简体中文 | [日本語](README.ja.md)

一款 Android 工具，可为单个应用强制指定**语言区域（locale）**——即使应用本身没有语言设置，
也从未出现在“设置 → 应用 → 应用语言”中，也可以使用。

你的手机可以保持日语，同时让微信和淘宝使用简体中文、ChatGPT 使用英语、Google 地图保持日语。

> **这不是翻译工具。**
> 它改变的是应用所“看到”的语言区域。如果应用没有提供该语言的资源，界面不会发生任何可见变化。
> 详见[它做不到什么](#它做不到什么)。

---

## 截图

<table>
  <tr>
    <td align="center" width="25%">
      <img src="docs/screenshots/list.png" alt="显示各应用语言区域的应用列表" width="100%"><br>
      <sub><b>所有已安装应用</b><br>已覆盖语言的应用会有标记，并可置顶显示</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/picker.png" alt="语言选择器" width="100%"><br>
      <sub><b>选择语言</b><br>可选预设、自定义语言，或输入任意 BCP 47 标签</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/setup.png" alt="Shizuku 设置指南" width="100%"><br>
      <sub><b>设置 Shizuku</b><br>四个步骤，无需 root，也无需电脑</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/help.png" alt="帮助页面" width="100%"><br>
      <sub><b>帮助</b><br>说明它能做什么，以及能力边界</sub>
    </td>
  </tr>
</table>

---

## 它实际上做了什么

Android 13 引入了**单应用语言区域**。系统会为每个软件包保存一份 `LocaleList` 覆盖设置，
并在应用启动时将其应用到该应用的 `Configuration`。应用无需主动适配这项功能——
无论应用是否适配，框架都会覆盖其配置。

应用需要主动适配的只是被系统设置**列出**：“设置 → 应用 → 应用语言”只会显示附带
`locales_config.xml`（`android:localeConfig`）的应用。没有该文件的应用在设置中不可见，
但底层语言区域覆盖机制依然可以正常工作。

本应用会为任意已安装软件包直接写入同一份覆盖设置。

---

## 使用要求

| | |
|---|---|
| Android | 13（API 33）或更高版本 |
| Root | 不需要 |
| [Shizuku](https://shizuku.rikka.app/) | 需要（已 root 的设备也可使用 Sui） |

### 为什么需要 Android 13+

单应用语言区域是 Android 13 新增的平台功能。API 33 之前不存在 `LocaleManager`、
`"locale"` 系统服务和按软件包保存的 `LocaleList`，在 Android 12 及更早版本上也没有
可模拟这一功能的同类机制。

### 为什么需要 Shizuku

公开 API `LocaleManager.setApplicationLocales(LocaleList)` 只能写入**调用方自身**软件包的
语言区域。其背后的系统服务提供了可指定软件包的版本：

```aidl
// frameworks/base/core/java/android/app/ILocaleManager.aidl
void setApplicationLocales(String packageName, int userId, in LocaleList locales);                          // API 33
void setApplicationLocales(String packageName, int userId, in LocaleList locales, boolean fromDelegate);    // API 34+
LocaleList getApplicationLocales(String packageName, int userId);
```

当调用方拥有目标软件包，或持有以下权限时，`LocaleManagerService` 才允许调用这些接口：

* `android.permission.CHANGE_CONFIGURATION`——写入语言区域
* `android.permission.READ_APP_SPECIFIC_LOCALES`——读取语言区域
* `android.permission.FORCE_STOP_PACKAGES`——用于“应用并重启”

这三个权限均为 `signature|privileged`，普通应用无法获得。但 `com.android.shell`
（uid 2000）在其清单中声明了全部三个权限——这也正是
`adb shell cmd locale set-app-locales …` 能够工作的原因。

Shizuku 以同一个 shell uid 运行一个小型服务，并允许应用通过它转发 Binder 事务。
因此，本应用自身不会获得任何权限，而是请 shell uid 代为调用。这也是本项目无需 root 的原因。

---

## 下载

请从[最新版本](https://github.com/TakeruF/android-perapp-language-selector/releases/latest)
下载已签名 APK。每个版本都使用同一密钥签名，证书指纹会发布在发行说明中。

---

## 设置

1. **安装 Shizuku**——可从 [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)、
   F-Droid 或 [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases) 安装。

2. **启动 Shizuku 服务。**

   *直接在设备上启动（Android 11+，无需电脑）：*
   打开开发者选项 → 开启**无线调试** → 打开 Shizuku → 选择**通过无线调试启动**。

   *通过电脑启动：*
   ```
   adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
   ```

   Shizuku 会在设备重启后停止，因此每次重启后都需要重复此步骤。
   已 root 的用户可以改装 **Sui**，它会自动启动。

3. **打开 Per-App Language**，并在弹出的 Shizuku 权限对话框中授权。

4. 点选一个应用，选择语言，然后点击**应用并重启**。

> **设置完成后：**即使 Shizuku 停止、设备重启或开发者选项被关闭，Android 仍会保留已经应用的
> 语言设置。开发者选项和调试只用于启动并保持 Shizuku 可用；当你需要更改或重置语言时，
> 请重新开启它们并启动 Shizuku。在未 root 的设备上，每次重启后都需要重新启动 Shizuku。
> 如果希望 Shizuku 持续可用，其[故障排除指南](https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging-start-by-connecting-to-a-computer-shizuku-randomly-stops)
> 建议保持开发者选项和 USB 调试开启。

---

## 语言区域更改是如何应用的

```
Per-App Language (uid 10xxx)
        │  ServiceManager.getService("locale")      ← 原始 Binder，此时尚无权限
        ▼
  ShizukuBinderWrapper                              ← 重新转发 Parcel
        │
        ▼
Shizuku 服务进程 (uid 2000 = com.android.shell)
        │  持有 CHANGE_CONFIGURATION / READ_APP_SPECIFIC_LOCALES / FORCE_STOP_PACKAGES
        ▼
LocaleManagerService  →  按软件包保存的 LocaleList 覆盖  →  下次启动应用时生效
```

本应用实现了两种与该服务通信的方式，并会自动回退：

1. **反射调用 `ILocaleManager$Stub.asInterface`**（首选）。它会检查设备上实际存在的方法，
   因此可自行适配 API 33 → 34 的签名变化（`fromDelegate`）以及 OEM 调整。
2. **手写 `Parcel` 事务**（回退方案），在隐藏 API 反射被阻止时使用。它完全不需要隐藏类，
   代价是需要硬编码事务 ID——自 API 33 起，这些接口在 AIDL 中的声明顺序一直没有变化。

**应用**只写入语言区域。**应用并重启**还会调用 `IActivityManager.forceStopPackage()`
并重新启动应用，因为运行中的应用只会把语言区域变化当作配置变化，而许多应用会在启动时缓存字符串，
从而忽略这一变化。

选择**系统默认**会发送一个空的 `LocaleList`，这是框架中“移除覆盖设置”的表示方式。

---

## 它做不到什么

* **它不能翻译。**为一个只提供英语资源的应用设置 `zh-CN` 不会产生可见变化。
  Android 会回退到应用的默认资源。
* **自行在内部选择语言的应用**（将语言保存在账号/服务器端，或提供自己的应用内设置）
  会忽略系统语言区域。部分中国超级应用属于这种情况。
* **只在进程启动时重新读取语言区域的应用**需要使用“应用并重启”——这正是该按钮的用途。
* **应用内的网页内容**（WebView、服务器渲染的页面）通常跟随账号设置或 `Accept-Language`，
  而不是单应用语言区域。
* **某些 OEM 系统可能拒绝强制停止。**语言区域仍会成功写入；你只需自行关闭应用。
  发生这种情况时，界面会明确提示。
* **工作资料 / 次要用户：**本应用只操作其安装所在的用户。
* 一些深度修改的 OEM ROM 可能会进一步限制 shell 权限。此时，本应用会报告
  `SecurityException`，而不是静默失败。

---

## 兼容性

所有操作均通过标准 AOSP 接口完成——`"locale"` 和 `"activity"` 系统服务、
`PackageManager` 以及 Shizuku。项目没有针对特定 OEM 的分支，也没有设备型号白名单，
因此应能在 AOSP、Pixel、One UI、ColorOS、OriginOS 和 HyperOS 等系统上工作。
当厂商限制某项操作（最常见的是强制停止）时，本应用会显示失败信息，而不会通过特定设备专用的
变通方式绕过。

---

## 验证

特权层并非凭假设实现。`app/src/debug/.../LocaleGatewayProbe.kt` 会通过 `app_process`
以 uid 2000 运行真实的 `LocaleGateway` 和 `ProcessGateway` 类，直接连接设备上的
`LocaleManagerService`——不使用 Shizuku，也不使用 mock：

```
./gradlew assembleDebug
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/probe.apk
adb shell CLASSPATH=/data/local/tmp/probe.apk app_process /system/bin \
    --nice-name=locale-probe dev.takeru.perapplocale.probe.LocaleGatewayProbe com.android.settings
```

它会同时测试反射路径和原始事务路径，并将每一项结果与
`cmd locale get-app-locales` 交叉核对。在 API 37 模拟器上，全部九项检查均通过，
确认硬编码事务 ID、Parcel 布局和 `fromDelegate` 参数均正确。

还在该模拟器上通过实际运行的 Shizuku 13.6.0 完成了端到端验证：状态转换
（未安装 → 需要授权 → 就绪）、权限对话框、为第三方应用应用 `zh-CN`、强制停止并重新启动，
以及重置为系统默认。每一步都通过 `cmd locale get-app-locales` 确认结果，而非仅相信界面显示。

需要明确说明一个限制：此处唯一可用的 Android 版本是 API 37，因此 API 33 的三参数
`setApplicationLocales` 分支是根据 AOSP 源码实现的，并未实际执行。实际运行的是 API 34+
的四参数分支。

---

## 架构

```
app/src/main/java/dev/takeru/perapplocale/
├── PerAppLocaleApp.kt          Application；解除隐藏 API 限制，持有 ShizukuRepository
├── MainActivity.kt             单 Activity、Compose 宿主与事件衔接
├── core/
│   ├── SystemBinder.kt         服务查找 + ShizukuBinderWrapper
│   ├── LocaleGateway.kt        get/setApplicationLocales——反射 + 原始事务路径
│   └── ProcessGateway.kt       通过 IActivityManager 调用 forceStopPackage
├── shizuku/
│   ├── ShizukuState.kt         READY / PERMISSION_REQUIRED / NOT_RUNNING / NOT_INSTALLED
│   └── ShizukuRepository.kt    Binder + 权限监听器，以 StateFlow 暴露状态
├── data/
│   ├── AppInfo.kt              单行数据
│   ├── AppRepository.kt        PackageManager 查询
│   ├── LocaleOption.kt         预设 + BCP 47 验证
│   └── SettingsStore.kt        DataStore：偏好设置 + 本地语言分配镜像
├── ui/                         Compose（Material 3）：MainScreen、LocaleSheet、
│   │                           ShizukuStatusCard、MainViewModel，以及由 DocScreen 构建的
│   │                           两个说明页面——SetupScreen 和 HelpScreen
│   └── theme/Theme.kt          可用时使用动态颜色；支持浅色 + 深色
└── util/AppIcon.kt             延迟加载、使用 LRU 缓存的启动器图标
```

数据流是单向的：`MainViewModel` 将 Shizuku 状态、软件包列表和 DataStore 偏好设置合并为一个
`MainUiState`；UI 是其纯函数，而 Snackbar、启动应用等一次性事件通过独立事件通道传递。

语言区域以系统记录为事实来源——DataStore 只保留一份镜像，使列表可以在按软件包进行的
Binder 扫描完成前立即显示已配置的应用。

---

## 构建

```
git clone https://github.com/TakeruF/android-perapp-language-selector.git
cd android-perapp-language-selector
./gradlew assembleDebug
```

需要 JDK 17 和 Android SDK 36。用 Android Studio 打开后应可直接导入。

### 发布版本构建

如果项目根目录中存在 `keystore.properties`，`assembleRelease` 会为 APK 签名；
如果不存在，构建仍会成功，但生成的是未签名 APK。

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

该文件和密钥库均已加入 `.gitignore`。

---

## 致谢

* **[VegaBobo/Language-Selector](https://github.com/VegaBobo/Language-Selector)**——
  该先行项目证明了通过 Shizuku 控制单应用语言区域是可行的。本项目将其总体思路作为参考，
  但依据 AOSP 源码和 Shizuku API 独立实现，不包含其任何代码。（若将来引用其代码，
  会在此正式添加 Apache-2.0 署名。）
* **[RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)** 及 Shizuku API。
* **[LSPosed/AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)**。

## 许可证

Apache License 2.0——详见 [LICENSE](LICENSE)。

# RoadCast

Android 车机 CarPlay 应用及 Android 手机副屏，包含源码、JNI 音频组件、测试、文档与示例。

基于 [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay) 继续开发，上游基线为 `615b32c`。保留原有版权和第三方组件说明，沿用 [GPL-3.0](LICENSE)；原始项目介绍见 [上游 README](docs/UPSTREAM_README.md)。

## 模块

| 模块 | 用途 |
| --- | --- |
| `mobile` | 车机主程序，含手机副屏发送入口 |
| `common` | 界面、设置、会话管理、车辆适配和副屏发送服务 |
| `shared` | USB / Wi-Fi、iAP2 / AirPlay、媒体、导航协议及 JNI 音频处理 |
| `companion` | Android 手机副屏，显示导航、音乐卡片和上游提供的歌词文本 |
| `companion-ui` | 两端共享界面文案 |
| `samples/home`（`:home`） | 可选桌面启动器 |
| `samples/maphost`（`:maphost`） | 同设备地图嵌入示例，不是跨手机副屏 |
| `automotive` | 独立 Android Automotive 应用 |
| `site` | 保留的上游静态网站与素材，本仓库不自动部署网站 |

## 拉取并构建

安装 JDK 25（也可使用 Android Studio 自带的 JBR 25），通过 SDK Manager 安装：

- Platform-Tools
- Android SDK Platform 37：`platforms;android-37.0`
- Build-Tools：`36.0.0`
- NDK：`28.2.13676358`

项目固定 Gradle Wrapper 9.5.0、AGP 9.3.0，JNI 使用 ndk-build，无需另装 Gradle 或 CMake。首次构建需要联网下载依赖。

```sh
git clone https://github.com/YiKiu6078/RoadCast.git
cd RoadCast
```

Android Studio 打开仓库根目录，选择 Gradle JDK 25，等待同步。主程序选择 `mobile`，手机副屏选择 `companion`。

命令行需要设置 `JAVA_HOME` 和 `ANDROID_HOME`（SDK 根目录），或在不提交的 `local.properties` 中填写自己的 `sdk.dir`。

Windows PowerShell：

```powershell
.\gradlew.bat :mobile:assembleDebug :companion:assembleDebug
```

Linux / macOS：

```sh
./gradlew :mobile:assembleDebug :companion:assembleDebug
```

Windows 可运行 `build-companion.cmd`，将两个 APK 导出到本机 `dist/companion/`。

产物：

- 主程序：`mobile/build/outputs/apk/debug/mobile-debug.apk`
- 手机副屏：`companion/build/outputs/apk/debug/companion-debug.apk`
- 其他应用：`gradlew :home:assembleDebug :maphost:assembleDebug :automotive:assembleDebug`

## 源码构建与真实 iPhone 连接

公开源码不需要私钥或签名材料即可构建普通调试 APK。**源码 APK 默认没有运行时配件认证身份，不能据此承诺独立连接 iPhone。** 真实独立连接需要使用者在仓库之外提供适用的认证材料，按 [构建指南](docs/BUILD.md) 设置 `ROADCAST_AUTH_ASSETS_DIR` 或本机 `local.properties` 中的 `roadcast.authAssetsDir`，然后执行 `:mobile:assembleStandaloneDebug`。

Release 构建另需使用者自己的 Android 签名密钥，配置见构建指南。`companion` 不需要 iPhone 认证文件，可使用 Android Studio 签名向导单独打包。

仓库不包含本机 SDK、缓存、APK、认证文件、签名文件、密码或个人开发路径。请不要把认证文件改名为 `.jar` 后提交。

## 手机副屏

两台 Android 设备连接同一可信局域网。首次在车机打开“RoadCast 副屏发送”，手机输入地址和配对码；之后记住配对，自动连接，并通过 mDNS 查找同一车机的新 IP。车机再次打开 RoadCast 时恢复此前开启的共享。

支持导航箭头、距离、道路、音乐封面、歌曲信息和音乐 App 提供的动态歌词文本。声音保留在车机，目前不包含实时地图视频或远程触摸。局域网传输为明文；网络过滤 mDNS 时可使用保存地址或手动更新 IP。

详见 [副屏使用与开发说明](companion/README.zh-CN.md)。导航和媒体实机数据依赖有效的上游 CarPlay 会话。

## 验证

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest :mobile:lintDebug :home:lintDebug :maphost:lintDebug :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug :companion:testDebugUnitTest :companion:lintDebug :companion:assembleDebug
```

Windows 使用 `gradlew.bat`。CI 见 [.github/workflows/android.yml](.github/workflows/android.yml)，本机验证结果见 [SOURCE_VALIDATION.md](docs/SOURCE_VALIDATION.md)。自动测试不能替代车机、iPhone 和手机实机联调。

`docs/` 和多语言 README 保留上游功能及历史说明，其中上游发布链接不是本仓库的构建产物。

# 源码快照验证

验证日期：2026-11-12。环境为 Windows、JDK 25、SDK Platform 37.0、Build-Tools 36.0.0、NDK 28.2.13676358、项目自带 Gradle Wrapper 9.5.0。

本次在独立 Git 工作目录中复制可发布源码进行构建，没有复制原工作目录的 `build/`、`.gradle/`、IDE 配置、`local.properties` 或运行时认证材料；构建进程只通过环境变量获取已安装 JDK / SDK，并使用正常 Gradle 依赖缓存。

## 已验证

- `mobile`、`companion`、`home`、`maphost`、`automotive` 的 `assembleDebug` 成功。
- `mobile`、`companion`、`home`、`maphost` 的 `lintDebug` 成功。
- 检查主程序与接收端 APK：包含对应副屏实现，不包含 `offline-mfi` 认证文件。
- 暂存源码通过 `scripts/check_public_tree.py` 检查，没有认证容器、安装包、私钥块，包含归档内部条目检查。
- 未提交 SDK、本机路径配置、生成输出或个人认证材料；唯一提交的 JAR 为 Gradle Wrapper。

## 完整回归与已知限制

完整 CI 命令见 `.github/workflows/android.yml`。Windows 下原有 `AmbientWorkerOwnerLockSymlinkTest` 的 3 项测试需要创建符号链接权限，本机不具备该权限；完整测试不能因此标记为全部通过。

上游 `WifiP2pLegacyGroupManagerTest.missingExactGroupInterfaceStopsAndRemovesOnlyOurGroup` 在旧工作目录曾有断言失败记录；本次独立源码目录中完整重建后，该用例通过，相关业务代码和测试没有修改。

最终完整回归耗时 13 分 19 秒，338 个任务全部实际执行：

| 模块 | 测试数 | 失败 | 跳过 |
| --- | ---: | ---: | ---: |
| shared | 1798 | 3（上述 Windows 权限限制） | 2 |
| common | 1733 | 0 | 0 |
| companion | 6 | 0 | 0 |
| home | 11 | 0 | 0 |

因此本机完整 CI 没有全通过，但所有 APK 构建和 Lint 均成功。没有跳过或删除权限测试来掩盖这一限制。

GitHub Actions 使用 Linux 环境，具体结果以该次 Actions 运行报告为准，不把本机编译成功等同于云端 CI 全部通过。

本次没有连接车机、iPhone 和副屏手机，未完成实机连接或 mDNS 网络并发验证。独立 iPhone 连接需要本地外部认证输入，普通源码构建不包含这些材料。

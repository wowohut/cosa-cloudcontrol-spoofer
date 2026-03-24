# CosaSpoof - ColorOS 游戏云控伪装

## 项目简介

CosaSpoof 是一个基于 `libxposed API 101` 开发的 Xposed 模块，专为 ColorOS/realmeUI 系统设计。其核心功能是拦截并修改系统「应用增强服务」(`com.oplus.cosa`) 读取的 `ro.boot.prjname`（主板机型代号）属性，将其伪装为用户指定的机型代号。

**工作原理：**
ColorOS / realmeUI 的应用增强服务会根据设备的 `ro.boot.prjname` 从云端拉取特定的游戏调度配置（例如风驰、FAS 等）。在相同 SoC 平台下，不同机型可能会被分配到差异化的性能调度方案。
通过本模块，可在不改变系统其他属性的前提下，将当前设备的标识伪装为特定机型，从而获取目标机型的性能调度配置。

## 模块特性

- **作用域限制**：仅在 `com.oplus.cosa` 进程中拦截 `SystemProperties.get` 相关方法，未对系统其他组件进行修改，不影响日常 OTA 更新。
- **开发架构**：使用 Kotlin 开发，设置界面基于 Jetpack Compose 构建，并采用 `libxposed API 101` 。
- **配置生效逻辑**：设置独立页面，修改保存代号后，模块将调用 Root 权限清理云控相关应用数据并触发系统重启，从而使配置生效。

## 项目结构

- `hook/CosaModule.kt` —— 核心 Hook 逻辑
- `ui/activity/MainActivity.kt` —— 模块设置与交互
- `data/...` —— 持久化配置，以及执行清理数据和重启手机的 Root 脚本

## 许可证

本项目基于 [MIT License](LICENSE) 授权开源。

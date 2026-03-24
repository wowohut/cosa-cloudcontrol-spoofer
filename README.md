# CosaSpoof

## 项目简介

一个基于 `libxposed API 101` 的现代 Xposed 模块，用于 Hook ColorOS 的 `com.oplus.cosa`（应用增强服务），伪装 `ro.boot.prjname`，让应用增强服务按“目标机型”拉取对应的云控配置。

> 这里的“云控”可理解为游戏调度/策略配置（如性能策略、帧率策略等）。通常在同一 SoC 平台内，选择更合适的云控配置能让游戏体验更接近该平台的最佳状态。

## 需求背景

- ColorOS/realmeUI 的应用增强服务（`com.oplus.cosa`）会根据 `ro.boot.prjname` 拉取对应机型的云控配置
- 不同机型的云控（游戏调度）配置可能有差异（如风驰，FAS等）
- 在同一 SoC 平台下，希望切换到另一机型的云控配置，以获得更合适的调度策略与游戏体验

## 功能需求

### 核心功能

- Hook `SystemProperties.get(String)` 与 `get(String, String)`，当读取 `ro.boot.prjname` 时返回伪装值
- 仅对 `com.oplus.cosa` 生效，不影响系统其他进程
- 设置页通过 `RemotePreferences` 读写伪装值

### 技术实现

- **框架**: libxposed API 101
- **平台**: 支持 modern Xposed API 的 LSPosed / libxposed 实现
- **语言**: Kotlin
- **最低 API**: 27 (Android 8.1)
- **目标 API**: 36

## Hook 点

```kotlin
if (key == "ro.boot.prjname") {
    return spoofValue
}
return originalValue
```

## 配置项

| 配置                | 值        | 说明                           |
| ------------------- | --------- | ------------------------------ |
| `ro.boot.prjname` | `24831` | 默认伪装值，可在模块设置页修改 |

说明：修改后需清除 `com.oplus.cosa` 数据重启生效。

## 使用方法

1. 编译安装 APK
2. 在框架中激活模块
3. 确认作用域仅为 `com.oplus.cosa`
4. 打开模块设置页，写入需要的 `ro.boot.prjname`，然后自动清除数据重启手机

## 开发构建说明

- 模块入口走 `META-INF/xposed/java_init.list`
- 模块元数据走 `META-INF/xposed/module.prop`
- 作用域走 `META-INF/xposed/scope.list`

## 项目结构

- `hook/CosaModule.kt`: modern Xposed 入口
- `data/SpoofSettings.kt`: 远程配置读写封装
- `data/XposedServiceBridge.kt`: 模块 App 与 Xposed Service 的连接桥
- `ui/activity/MainActivity.kt`: 极简设置页

## 注意事项

- 仅影响应用增强服务，不影响 OTA 更新
- 不再兼容旧版共享偏好配置；升级后如有自定义值，需要在设置页重新保存一次
- 需要框架支持 `libxposed API 101` 和 remote capabilities

## 许可证

MIT License

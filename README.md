# Xiaomi Icon Converter

Xiaomi Icon Converter 是一款面向 HyperOS 的图标转换与主题辅助工具，帮助用户导入 Icon Pack、检查图标映射、预览 Material You 效果，并在具备相应权限时应用到系统主题。

当前稳定发布版本为 **v0.5.0**（`versionCode 20`）。

[查看 v0.5.0 Release](https://github.com/chris477966/xiaomi-icon-converter/releases/tag/v0.5.0)

## 功能概览

### Icon Pack

- 从 APK 导入 Icon Pack 并浏览其中的图标资源
- 按应用名称或 package name 搜索图标
- 查看应用的匹配状态和资源解析诊断
- 对单个应用设置手动图标覆盖
- 恢复单个应用的自动匹配结果
- 管理已导入的 Icon Pack，并设置 Active Icon Pack

### Material You

- 查看当前 Material You / Monet 状态
- 生成并预览 Material 图标方案
- 选择图标样式、形状和资源来源
- 导出 Material 资源诊断信息
- 延迟预览 Bitmap 解码，降低首次进入页面的开销
- 使用 36 项 LRU 预览缓存，减少重复解码和重组

### HyperOS 工具

- 检查 HyperOS 版本与主题环境
- 检查 Root 可用性
- 应用或恢复图标主题
- 在执行主题变更前提供基础主题确认
- 保留主题备份、恢复和完整诊断导出能力

## v0.5.0 视觉更新

- 采用简约、Apple 风格的视觉层级和留白
- 重排首页、Icon Pack、Material You 与 Settings 页面
- 底部采用半透明液态玻璃风格操作栏
- 以分组卡片呈现图标包、状态和诊断信息
- 将主要操作统一收纳到底部操作栏，减少页面视觉噪音

本次版本的 UI 和预览性能优化不改变 Icon Pack 匹配/parser 行为，也不改变 HyperOS apply、Root 或 Material 处理逻辑。

## 推荐使用流程

1. 导入一个 Icon Pack APK。
2. 在 Icon Pack 页面确认资源索引、匹配数量和诊断状态。
3. 使用应用名称或 package name 搜索并检查图标。
4. 对需要特殊处理的应用设置手动覆盖。
5. 确认 Active Icon Pack 和图标样式。
6. 检查 Root 与 HyperOS 状态后，再执行应用操作。
7. 如果出现异常，先导出诊断报告，再尝试恢复主题。

## 界面预览

![主界面](主图1.png)

![Icon Pack 与 Material You](主图2.png)

![详情与诊断](详情页.png)

## 当前版本信息

| 项目 | 值 |
| --- | --- |
| `versionName` | `0.5.0` |
| `versionCode` | `20` |
| `applicationId` | `com.wikiglobal.iconconverter` |
| 发布分支 | `feature/v0.3.0-icon-pack` |
| 发布 tag | `v0.5.0` |

## 构建与验证

使用仓库内置的 Gradle Wrapper，并按照 [RELEASE.md](RELEASE.md) 的顺序执行：

```bash
./gradlew clean test assembleDebug
scripts/verify-apk.sh app/build/outputs/apk/debug/app-debug.apk
apksigner verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
```

可安装的 Debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

发布前必须确认 APK 的 application ID、版本号、签名和 SHA-256，并对从 GitHub 下载的 Release asset 重新执行验证。

## 开发说明

- 本项目使用 Kotlin、Jetpack Compose 和 Material 3。
- UI 层负责展示状态与触发既有操作；Icon Pack 解析、匹配、主题应用和 Root 逻辑位于对应的 domain / hyperos 模块。
- 涉及系统主题变更的操作应在真实设备上由用户确认。
- Debug APK 仅用于测试和验收，不代表 Play Store 签名版本。

## 第三方组件与许可证

第三方组件、许可证和声明请参阅 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 以及 `third_party/` 目录。

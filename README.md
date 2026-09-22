# Xiaomi Icon Converter

面向 HyperOS 的图标转换与主题辅助工具，当前版本为 **0.5.0**（`versionCode 20`）。

## 当前版本

- `versionName`: `0.5.0`
- `versionCode`: `20`
- `applicationId`: `com.wikiglobal.iconconverter`
- Git branch: `feature/v0.3.0-icon-pack`

## 功能

- 导入并浏览 Icon Pack 资源
- 按应用名称或 package name 搜索图标
- 手动设置图标覆盖
- 管理 Active Icon Pack
- HyperOS 图标转换与应用流程
- Material You 状态查看、预览和应用
- 导出完整诊断信息，便于问题定位
- 保留 Root、HyperOS 兼容性与主题备份相关能力

## 0.5.0 UI 更新

- 采用简约、Apple 风格的视觉层级
- 重排首页、Icon Pack、Material You 和 Settings 页面
- 底部采用半透明液态玻璃风格操作栏
- 使用更清晰的分组卡片、留白和状态层级
- 延迟 Material 预览 Bitmap 解码
- 使用 36 项 LRU 预览缓存，减少重复解码与重组

本次 UI 更新不改变 Icon Pack 匹配/parser 行为，也不改变 HyperOS apply、Root 或 Material 处理逻辑。

## 构建与验证

使用仓库内置的 Android SDK/JDK 环境执行：

```bash
./gradlew clean test assembleDebug
scripts/verify-apk.sh app/build/outputs/apk/debug/app-debug.apk
apksigner verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
```

可安装测试 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

正式发布流程请参阅 [RELEASE.md](RELEASE.md)。

## 第三方组件

第三方组件、许可证和声明请参阅 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 以及 `third_party/` 目录。

# V0.5 构建与验证报告

日期：2026-11-03。

- 从用户提供的 V0.4 源码升级，应用 ID 保持 com.example.cardpool；versionCode=5，versionName=0.5-realtime。
- JDK 21、Gradle 8.13、Android Gradle Plugin 8.13.2、SDK/Build Tools 35、NDK r27c。
- `:app:assembleDebug :app:lintDebug`：BUILD SUCCESSFUL。Lint 0 errors、7 warnings（目标SDK版本提示、备份规则、拖动控件无障碍及本地化提示；完整报告随源码附带）。
- C++：605 组与递归参考公式对照的概率用例通过，另含耗尽、非法数量、NaN、独立格/无放回模型边界。
- Java/JNI：33 项检查通过，实际加载宿主 JNI 动态库，覆盖旧版迁移与来源标注、八人/多星折算、深拷贝、持久化、存储失败回滚、双订阅者通知、模拟隔离、无效参数与旧概率清除。
- 原 V0.4 CalculatorTest / TrackerTest / QuickEditTest 全部通过。
- APK Signature Scheme v2 验证通过，`zipalign -c -P 16 4` 通过；四种 ABI：arm64-v8a、armeabi-v7a、x86_64、x86，JNI ELF LOAD 段按 16KB 对齐构建。
- AAPT 检查：Android 8.0（API 26）以上，target/compile SDK 35，启动 Activity 存在，包含 classes.dex、资源与四种 ABI 原生库；未声明网络、ROOT 或游戏进程访问权限。
- 签名证书 SHA-256：f80b90d981b8e4fc67f9bbbdf16e3e627b0747a6856bf7475aa2759d5993f8c1，与当前环境旧 V0.3 APK 一致；没有 V0.4 APK 可供独立证书比对。当前产物是可侧载的已签名调试版。
- APK 大小：1188428 字节。
- APK SHA-256：`922c99aea504935c19cde042f7e1e267c2e3be64fef83756cecc019be287a2c0`。

## 验证限制

本次没有 Android 真机或模拟器连接，没有执行设备上的安装、触控、悬浮窗权限/厂商保活测试。已完成编译、单元/JNI 测试、Android Lint、签名及 APK 结构与对齐检查。这里不宣称已完成真机验收。

## 建议设备验收

覆盖安装同签名旧版，检查八人旧数据与备注；补齐赛季名单；主界面保存后观察悬浮窗修订号及概率；从悬浮窗编辑并返回主界面；交叉打开编辑器后修改另一处，检查旧编辑器保存被拒绝；切换模拟再切回，核对手动记录完整保留；检查应用重启后数据恢复。

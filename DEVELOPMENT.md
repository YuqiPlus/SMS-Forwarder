# 开发与集成文档

如果只想安装和使用应用，请看[用户指南](README.md)。

## 构建与验证

用 Android Studio 打开项目目录，安装 Android SDK 37 并同步 Gradle。工程使用 Android Gradle Plugin 9.3.0、Gradle 9.9.2 和 JDK 17 或更高版本。

| 用途 | macOS / Linux | Windows |
| --- | --- | --- |
| 本地联调 | `./gradlew assembleDebug lintDebug` | `gradlew.bat assembleDebug lintDebug` |
| 发布构建 | `./gradlew assembleRelease lintRelease` | `gradlew.bat assembleRelease lintRelease` |

调试包在 `app/build/outputs/apk/debug/app-debug.apk`。用普通运营商短信验证接收、SIM 卡规则和接收端回执，真机锁屏行为可参考[用户指南](README.md#各品牌后台设置)。

正式 APK 使用独立的发布密钥签名。更换签名不能直接覆盖同包名的旧版本。若要从调试版切换到正式版，先保存还要用的接收端信息，再卸载调试版。卸载会删除本机配置和历史。后续正式版沿用相同发布签名。 [Android 应用签名](https://developer.android.com/studio/publish/app-signing#considerations)

## 通用 Webhook 请求

应用向自建接收端发送 HTTPS POST，`Content-Type` 为 `application/json; charset=utf-8`。填了 Token 时，请求会带上 `Authorization: Bearer <token>`。

```json
{
  "id": "dc1f1892-0a89-42b7-92b8-b7e465bbf20b",
  "event": "sms.received",
  "sender": "+8613800000000",
  "body": "这是一条普通短信",
  "receivedAt": 1795435200000
}
```

`receivedAt` 是 Unix 毫秒时间戳，真实短信取自短信本身，测试消息取手机当前时间。测试事件名是 `sms.test`。接收端可以用 `id` 或同值的 `Idempotency-Key` 请求头去重。同一条消息重试时会沿用这个 ID。

网络异常以及 HTTP 408、425、429、5xx 会触发自动重试。每条消息最多尝试 6 次，任务再次执行时会丢弃排队超过 24 小时的消息。待处理上限为 40 条。关闭转发或更改正在使用的接收端和 SIM 卡规则会清空待发队列，已发出的请求可能仍会被接收。

## 接口参考

- [Android 短信广播](https://developer.android.com/reference/android/provider/Telephony.Sms.Intents#SMS_RECEIVED_ACTION)
- [Android 短信权限](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS)
- [Android 任务调度](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setPersisted(boolean))
- [飞书自定义机器人指南](https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot?lang=zh-CN)
- [Google Play 短信权限政策](https://support.google.com/googleplay/android-developer/answer/10208820)

## 第三方图标

接收端图标用于标识各平台，商标归各自权利人所有。Slack 和 Discord 图标来自 [SVG Logos](https://github.com/gilbarbara/logos)，钉钉图标来自 [Ant Design Icons](https://github.com/ant-design/ant-design-icons)，飞书 / Lark 和企业微信图标来自各平台官网。钉钉图标的 [MIT 许可](app/src/main/assets/ant-design-icons-LICENSE.txt) 随 APK 一同分发。

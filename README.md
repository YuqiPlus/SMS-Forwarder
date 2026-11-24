# 短信转发

这是一个 Android 应用。开启转发并授予短信权限后，应用会把系统交给它的新短信通过 HTTPS POST 发送到你配置的 Webhook。支持 Android 8.0（API 26）及以上。

## 手机上怎么用

1. 在手机上安装随附的 `sms-forwarder-debug.apk`，打开「短信转发」。
2. 填写能接收 POST JSON 的 HTTPS 地址。如果接收端使用 Bearer 鉴权，也填入 Token，然后点「保存设置」。
3. 点「发送测试消息」。这会发送预设文字，转发关闭时也能测试。接收端返回 HTTP 2xx 后，应用会显示发送成功。
4. 点「开启短信转发」并授权，从另一部手机发一条普通短信到测试手机。在接收端检查 `event` 为 `sms.received` 的请求。

Android 会根据安装来源和设备策略决定能否授予 `RECEIVE_SMS`。部分含一次性验证码或 SMS Retriever 哈希的短信可能不会交给应用。测试真实短信时先用普通文本。上架 Google Play 需要核对短信权限政策并按要求申报。

## Webhook 请求

`Content-Type: application/json; charset=utf-8`。填了 Token 时，请求会带上 `Authorization: Bearer <token>`。

```json
{
  "id": "dc1f1892-0a89-42b7-92b8-b7e465bbf20b",
  "event": "sms.received",
  "sender": "+8613800000000",
  "body": "这是一条普通短信",
  "receivedAt": 1795435200000
}
```

`receivedAt` 是 Unix 毫秒时间戳，真实短信取自短信本身，测试消息取手机当前时间。测试事件名是 `sms.test`。如果接收端记录消息或触发其他操作，可通过 `id` 或同值的 `Idempotency-Key` 请求头去重。同一条消息重试时会沿用这个 ID。

网络异常以及 HTTP 408、425、429、5xx 会触发自动重试。每条消息最多尝试 6 次，任务再次执行时会丢弃排队超过 24 小时的消息。待处理上限为 40 条。关闭转发或更改已保存的接收端配置会清空待发队列，已发出的请求可能仍会被接收。

## 打开源码

用 Android Studio 打开 `SMS-Forwarder` 目录，安装 Android SDK 37，然后同步 Gradle。工程使用 Android Gradle Plugin 9.4.0、Gradle 9.6.0 和 JDK 17 或更高版本。命令行可运行 Windows 的 `gradlew.bat assembleDebug`，或者 macOS / Linux 的 `sh ./gradlew assembleDebug`。

随附的是签名测试 APK。可用上面的步骤在你的设备和接收端做端到端验证。

- [Android 短信广播文档](https://developer.android.com/reference/android/provider/Telephony.Sms.Intents#SMS_RECEIVED_ACTION)
- [Android 短信权限文档](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS)
- [Google Play 短信权限政策](https://support.google.com/googleplay/android-developer/answer/10208820)


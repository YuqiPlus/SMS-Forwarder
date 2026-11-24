# 短信转发

这是一个 Android 应用。开启转发并授予短信权限后，应用会把系统交给它的新短信通过 HTTPS POST 发送到你配置的 Webhook。支持 Android 8.0（API 26）及以上。

## 手机上怎么用

1. 在手机上安装 [GitHub Releases](https://github.com/YuqiPlus/SMS-Forwarder/releases) 提供的 APK，打开「短信转发」。从浏览器或文件管理器安装时，如果系统提示，给打开 APK 的应用临时开启「允许安装未知应用」。安装后可撤销。
2. 点首页右上角的设置图标，再点「＋」。选择接收平台，填写昵称和完整的 HTTPS Webhook 地址。需要鉴权时，填入界面提示的密钥，再点「保存接收端」。
3. 返回首页，点「发送测试消息」。这会向当前接收端发送预设文字，转发关闭时也能测试。在「最近转发记录」查看结果。
4. 点「开启转发」并授权，从另一部手机发一条普通短信到测试手机。在目标群或频道查看消息，或在通用接收端检查 `event` 为 `sms.received` 的请求。

首次安装只需在手机上授予短信权限。锁屏后转发不稳定时，先看[各品牌后台设置](#各品牌后台设置)。

## 短信权限与后台运行

### 应用需要哪些权限

| 权限 | 是否需要手动开启 | 用途 |
| --- | --- | --- |
| 接收短信 `RECEIVE_SMS` | **需要**。点首页「开启转发」后选择「允许」 | 接收系统交给应用的新短信。开启后，应用将发件人和正文发往你配置的接收端 |
| 网络访问 `INTERNET` | 系统自动授予 | 通过 HTTPS 发送测试消息和短信 |
| 查看网络状态 `ACCESS_NETWORK_STATE` | 系统自动授予 | 让 Android 在有网络时调度待发任务 |
| 开机广播 `RECEIVE_BOOT_COMPLETED` | 系统自动授予 | 允许 Android 保留已安排的发送任务，供设备重启后继续调度 |

应用无需读取历史短信、发送运营商短信、读取手机号或通讯录，也无需通知权限、无障碍、悬浮窗或设为默认短信应用。SIM 卡规则使用 Android 随新短信提供的卡槽或订阅信息。测试按钮只发送预设文字，验证 Webhook 时无需开启短信转发或授予短信权限。

如果点「开启转发」时拒绝了授权，可以长按应用图标 → **应用信息 → 权限 → 短信 → 允许**。入口也可从 **设置 → 应用 → 短信转发 → 权限** 找到。Android 还会根据安装器和设备管理策略限制这项权限。如果系统没有提供开关，或开关无法修改，应用不能代为授权。 [Android 短信权限说明](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS) · [任务调度说明](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setPersisted(boolean))

### 各品牌后台设置

先完成短信授权、打开一次应用，再按 Home 键返回桌面或直接锁屏。用另一部手机发一条普通运营商短信测试。只有在前台能转发、后台或锁屏不稳定时，再按下表调整**「短信转发」这一个应用**。菜单会随系统版本和地区变化，找不到时可在手机设置里搜索表中的粗体名称。

| 品牌 / 系统 | 常见入口与操作 |
| --- | --- |
| **小米、Redmi、POCO（澎湃 OS / MIUI）** | **设置 → 应用设置 → 授权管理 → 后台自启动**，允许「短信转发」。在 **应用管理 → 短信转发 → 省电策略** 选「无限制」。若该机型在 **权限管理 → 其他权限** 单列「通知类短信」，且需要转发通知类短信，可检查这一项。常用一键清理时，可以在最近任务给应用加锁。 [自启动](https://www.mi.com/uk/support/faq/details/KA-507608/) · [后台保护](https://www.mi.com/global/support/faq/details/KA-230012/) · [通知类短信入口参考](https://consumer.huawei.com/cn/support/content/zh-cn16031287/) |
| **华为（支持 Android APK 的 EMUI / HarmonyOS）** | 设置搜索 **应用启动管理**，找到「短信转发」，关掉该应用的自动管理。弹出的「手动管理」里勾选「允许自启动」「允许关联启动」「允许后台活动」。如果锁屏仍停转，再搜索 **电池优化**，切换到全部应用，选择「短信转发」并设为「不允许优化」。 [华为后台保护说明](https://consumer.huawei.com/cn/support/content/zh-cn15850065/) |
| **荣耀（MagicOS）** | **设置 → 应用 → 应用启动管理**。关闭「短信转发」的自动管理，在手动管理里打开「允许自启动」「允许关联启动」「允许后台活动」。部分机型也可从系统管家的启动管理进入。 [荣耀后台运行说明](https://www.honor.com/cn/support/content/zh-cn00458250/) |
| **OPPO（ColorOS）** | **设置 → 应用 → 自启动**，打开该应用。在 **设置 → 电池 → 更多设置 → 应用耗电管理**，选择「短信转发」，如有「允许后台活动」则开启。 [ColorOS 用户手册](https://s3.ap-southeast-1.amazonaws.com/sgp-sow-cms-oppo-com/cms/userManual/manual/f888f4f6-4f81-4759-8aaa-115f25e31bb4/ColorOS_14.0_User_Manual_V1.0_20231227.pdf) |
| **一加（ColorOS / OxygenOS）** | 国行 ColorOS 可参考 OPPO。OxygenOS 可在 **Settings → Apps → Auto launch** 允许该应用，再在 **Battery → More settings → App battery management** 查看该应用的后台活动设置；如有「Allow background activity」则开启。 [OxygenOS 用户手册](https://service.oneplus.com/content/dam/support/user-manuals/common/OxygenOS_14.0_User_Manual.pdf) |
| **真我（realme UI）** | **设置 → 电池 → 应用耗电管理**；有的版本在 **电池 → 省电设置** 下。找到「短信转发」，开启「允许后台活动」。如果有「允许自启动 / Auto launch」，也为该应用打开，部分版本入口在手机管家的权限管理。 [realme 后台设置](https://www.realme.com/global/support/faq) · [新版本耗电管理入口](https://www.realme.com/in/support/kw/doc/2068398?articleIndex=7&queryId=be63975ff1ff411c975c3b3c0e204299) |
| **vivo、iQOO（OriginOS / Funtouch OS）** | 在 **i 管家 → 应用管理 → 权限管理 → 自启动** 允许该应用。在 **设置 → 电池 → 后台耗电管理**（有些版本叫「后台高耗电」），选择「允许后台高耗电」或相应的允许后台选项。 [自启动入口参考](https://bbs.vivo.com.cn/newbbs/thread/32191985) · [OriginOS 后台耗电设置参考](https://bbs.vivo.com.cn/newbbs/thread/37307329) |
| **三星（One UI）** | **设置 → 电池 → 后台使用限制**，有的版本先进入「电池和设备维护」。把「短信转发」从休眠或深度休眠列表移出；仍不稳定时，将它加入「从不休眠的应用」。 [三星后台使用限制说明](https://developer.samsung.com/mobile/app-management.html) |
| **Pixel、接近原生 Android 的设备** | **设置 → 应用 → 查看所有应用 → 短信转发 → 应用电池用量**，确认允许后台使用。先保留「优化」；如果锁屏仍延迟，且该页提供「不受限制」，可单独为该应用选择。Moto 的部分版本也有 **设置 → 电池 → 后台应用使用**。 [Pixel 后台用量说明](https://support.google.com/pixelphone/answer/6090599?hl=en) · [Moto 用户手册](https://en-us.support.motorola.com/ci/fattach/get/83761444/1712008047/redirect/1/filename/moto%2Bg%2Bpower%2B5G%2B-%2B2024.US%2BRetail.Android14.UG.en-US.SSC8E12661-A.pdf) |

本项目提供 Android APK。在 HarmonyOS 5 及以上设备，华为的 Android 应用兼容方式取决于机型和具体应用，本项目尚未验证这种环境能否接收系统短信广播。 [华为应用安装说明](https://consumer.huawei.com/cn/support/content/zh-cn16061787/)

### 怎么判断卡在哪一步

1. **测试消息都发不出**：先检查 Webhook 地址、签名、关键词、IP 白名单和网络，在首页看具体回执。
2. **测试能发，真实短信在历史里完全没有**：检查首页是否已开启转发、短信权限是否允许，再用普通运营商短信分别在前台与锁屏时测试。有的系统不向第三方应用交付部分验证码短信；系统未交付的消息不会出现在历史里。 [Android 17 短信验证码保护](https://developer.android.com/about/versions/17/behavior-changes-17#otp-protection-for-standard-sms-messages)
3. **只有锁屏或清理后台时不行**：检查上表的设置，有「后台数据」或「流量节省」限制时也检查该应用能否联网。不要点「强行停止」，小米等部分系统里上划清除也可能等同于强行停止。遇到这种情况，重新打开应用后再发新短信验证。后台自启动和「无限制」也不能保证每台手机都允许广播唤醒。 [Android 停止状态说明](https://developer.android.com/reference/android/content/pm/ApplicationInfo#FLAG_STOPPED)
4. **历史显示「已跳过」**：检查对应 SIM 卡有没有分配接收端。按 SIM 卡转发时，来源未识别的短信会跳过，不会改发到默认接收端。

## 支持的接收平台

| 接收平台 | 地址示例 | 额外鉴权 |
| --- | --- | --- |
| 飞书 / Lark | `https://open.feishu.cn/open-apis/bot/v2/hook/…` 或 `open.larksuite.com` 上的同一路径 | 启用签名校验时填签名密钥 |
| 企业微信 | `https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=…` | 无，使用带 `key` 的完整地址 |
| 钉钉 | `https://oapi.dingtalk.com/robot/send?access_token=…` | 启用加签时单独填写密钥，地址使用机器人提供的原始地址 |
| Slack | `https://hooks.slack.com/services/…` | 无，使用 Slack 应用的 Incoming Webhook |
| Discord | `https://discord.com/api/webhooks/…` | 无，使用频道生成的 Webhook |
| 通用 Webhook | 接收 JSON POST 的自建服务或自动化工作流 | 可选 Bearer Token |

可以保存多个接收端。默认模式下，所有短信都发往标为「当前」的接收端。点开卡片可以编辑或切换当前接收端。删除接收端会移除指向它的 SIM 卡规则，避免将短信自动发到其他地方。

「自动识别」按完整地址选择平台，其他 HTTPS 地址使用通用 JSON。也可以手动指定「通用 Webhook」。从旧版更新时，已保存的地址会出现在接收端列表里，并沿用原来的格式与转发状态。

飞书以卡片显示，测试卡片为灰色，短信卡片为蓝色。其他群机器人和频道以文字显示。时间使用手机时区。飞书或钉钉启用关键词校验时，可以使用「短信转发」，测试和正式消息都包含这个词。如果设置了 IP 白名单，手机使用的网络出口 IP 必须在名单中。

应用会检查平台的回执。企业微信、Slack 或 Discord 的消息遇到长度限制时会标注截断。已知的限流错误会自动重试。

## SIM 卡规则和转发历史

在首页打开「SIM 卡规则」，开启「按 SIM 卡设置」，再分别给 SIM 1、SIM 2 选择接收端。只转一张卡时，把另一张设为「不转发」。规则按系统卡槽编号设置，换卡后请重新核对。按卡转发时，无法可靠识别来源的短信会跳过。关闭按卡设置后，恢复全部短信发往当前接收端。

「转发历史」显示时间、SIM 卡、接收端、脱敏发件人和处理结果，保留最近 200 条。不保存短信正文或 Webhook 密钥。系统没有交给应用的短信不会出现在历史中。变更转发规则或正在使用的目标时，待发消息会标为已取消，避免发到新接收端。

## 通用 Webhook 请求

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

用 Android Studio 打开 `SMS-Forwarder` 目录，安装 Android SDK 37，然后同步 Gradle。工程使用 Android Gradle Plugin 9.3.0、Gradle 9.9.2 和 JDK 17 或更高版本。命令行可运行 Windows 的 `gradlew.bat assembleDebug`，或者 macOS / Linux 的 `./gradlew assembleDebug`。

生成的 Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。可用上面的步骤在设备和接收端做端到端验证。

正式 APK 使用发布签名。手机若已装过采用调试签名的版本，Android 通常不允许直接覆盖。卸载旧版会清除配置和本机历史，请先确认这些数据是否还需要。 [Android 应用签名与升级说明](https://developer.android.com/studio/publish/app-signing#considerations)

- [Android 短信广播文档](https://developer.android.com/reference/android/provider/Telephony.Sms.Intents#SMS_RECEIVED_ACTION)
- [Android 短信权限文档](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS)
- [飞书自定义机器人使用指南](https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot?lang=zh-CN)
- [Google Play 短信权限政策](https://support.google.com/googleplay/android-developer/answer/10208820)

接收端图标用于标识各平台，商标归各自权利人所有。Slack 和 Discord 图标来自 [SVG Logos](https://github.com/gilbarbara/logos)，钉钉图标来自 [Ant Design Icons](https://github.com/ant-design/ant-design-icons)，飞书 / Lark 和企业微信图标来自各平台官网。


package com.smsforwarder.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.StringJoiner;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

final class Webhook {
    private static final int MAX_RESPONSE_BYTES = 8192;
    private static final int DISCORD_SUPPRESS_EMBEDS = 4;
    private static final String TRUNCATION_NOTICE = "\n（正文过长，已截断）";
    private static final DateTimeFormatter MESSAGE_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT);

    record Result(String message, boolean retry, boolean success) {
        Result(String message, boolean retry) {
            this(message, retry, false);
        }

        static Result sent(String message) {
            return new Result(message, false, true);
        }
    }

    static String validateEndpoint(String input, WebhookPlatform mode) {
        URI uri;
        try {
            uri = new URI(input.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65_535) {
                throw new IllegalArgumentException();
            }
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("请输入完整的 HTTPS Webhook 地址");
        }
        mode.resolve(uri).validate(uri);
        return uri.toASCIIString();
    }

    static String validateToken(String input) {
        String token = input.trim();
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (character < 33 || character > 126) {
                throw new IllegalArgumentException("Token 只能包含可打印的 ASCII 字符，且不能包含空格");
            }
        }
        return token;
    }

    static Result send(ForwardingStore.Settings settings, ForwardingStore.Delivery delivery) {
        HttpsURLConnection connection = null;
        try {
            URI endpoint = URI.create(validateEndpoint(settings.endpoint(), settings.platform()));
            WebhookPlatform platform = settings.platform().resolve(endpoint);
            JSONObject payload = payload(platform, settings.token(), delivery);
            byte[] json = payload.toString().getBytes(StandardCharsets.UTF_8);

            connection = (HttpsURLConnection) requestEndpoint(endpoint, platform, settings.token())
                    .toURL().openConnection();
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(12_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Idempotency-Key", delivery.eventId());
            if (platform == WebhookPlatform.GENERIC && !settings.token().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + settings.token());
            }
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(json.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(json);
            }

            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                return successResponse(platform, status, connection);
            }
            boolean retry = status == 408 || status == 425 || status == 429 || status >= 500;
            return new Result("服务器返回 HTTP " + status, retry);
        } catch (SSLHandshakeException | SSLPeerUnverifiedException exception) {
            return new Result("HTTPS 安全连接失败，请检查服务器证书与配置", false);
        } catch (IOException exception) {
            return new Result("网络请求失败", true);
        } catch (GeneralSecurityException exception) {
            return new Result("无法生成机器人签名", false);
        } catch (JSONException | IllegalArgumentException exception) {
            return new Result("请求配置或数据无效", false);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static JSONObject payload(WebhookPlatform platform, String secret, ForwardingStore.Delivery delivery)
            throws JSONException, GeneralSecurityException {
        return switch (platform) {
            case FEISHU -> feishuPayload(secret, delivery);
            case WECOM, DINGTALK -> {
                String text = messageText(delivery);
                yield new JSONObject().put("msgtype", "text").put("text", new JSONObject()
                        .put("content", platform == WebhookPlatform.WECOM ? truncateUtf8(text, 2048) : text));
            }
            case SLACK -> new JSONObject()
                    .put("text", truncateCharacters(messageText(delivery), 4000)
                            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                    .put("mrkdwn", false)
                    .put("unfurl_links", false)
                    .put("unfurl_media", false);
            case DISCORD -> new JSONObject()
                    .put("content", truncateCharacters(messageText(delivery), 2000))
                    .put("allowed_mentions", new JSONObject().put("parse", new JSONArray()))
                    .put("flags", DISCORD_SUPPRESS_EMBEDS);
            case GENERIC -> genericPayload(delivery);
            case AUTO -> throw new IllegalArgumentException("未识别 Webhook 类型");
        };
    }

    private static URI requestEndpoint(URI endpoint, WebhookPlatform platform, String secret)
            throws GeneralSecurityException, IOException {
        if (platform == WebhookPlatform.DINGTALK && !secret.isEmpty()) {
            String timestamp = Long.toString(System.currentTimeMillis());
            String signature = sign(secret, (timestamp + "\n" + secret).getBytes(StandardCharsets.UTF_8));
            return URI.create(endpoint.toASCIIString() + "&timestamp=" + timestamp
                    + "&sign=" + URLEncoder.encode(signature, StandardCharsets.UTF_8.name()));
        }
        if (platform == WebhookPlatform.DISCORD) {
            String[] url = endpoint.toASCIIString().split("\\?", 2);
            StringJoiner parameters = new StringJoiner("&");
            if (url.length == 2) {
                for (String part : url[1].split("&")) {
                    if (!part.isEmpty() && !part.equals("wait") && !part.startsWith("wait=")) {
                        parameters.add(part);
                    }
                }
            }
            parameters.add("wait=true");
            return URI.create(url[0] + "?" + parameters);
        }
        return endpoint;
    }

    private static String sign(String key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data));
    }

    private static JSONObject genericPayload(ForwardingStore.Delivery delivery) throws JSONException {
        return new JSONObject()
                .put("id", delivery.eventId())
                .put("event", delivery.test() ? "sms.test" : "sms.received")
                .put("sender", delivery.sender())
                .put("body", delivery.body())
                .put("receivedAt", delivery.receivedAt());
    }

    private static JSONObject feishuPayload(String secret, ForwardingStore.Delivery delivery)
            throws JSONException, GeneralSecurityException {
        JSONObject payload = new JSONObject()
                .put("msg_type", "interactive")
                .put("card", feishuCard(delivery));
        if (!secret.isEmpty()) {
            String timestamp = Long.toString(System.currentTimeMillis() / 1000L);
            payload.put("timestamp", timestamp);
            payload.put("sign", sign(timestamp + "\n" + secret, new byte[0]));
        }
        return payload;
    }

    private static JSONObject feishuCard(ForwardingStore.Delivery delivery) throws JSONException {
        boolean test = delivery.test();
        return new JSONObject()
                .put("header", new JSONObject()
                        .put("template", test ? "grey" : "blue")
                        .put("title", plainText(messageTitle(delivery))))
                .put("elements", new JSONArray()
                        .put(textSection(messageDetails(delivery)))
                        .put(new JSONObject().put("tag", "hr"))
                        .put(textSection(delivery.body())));
    }

    private static JSONObject textSection(String content) throws JSONException {
        return new JSONObject().put("tag", "div").put("text", plainText(content));
    }

    private static JSONObject plainText(String content) throws JSONException {
        return new JSONObject().put("tag", "plain_text").put("content", content);
    }

    private static String messageTitle(ForwardingStore.Delivery delivery) {
        return delivery.test() ? "短信转发测试" : "短信转发";
    }

    private static String messageDetails(ForwardingStore.Delivery delivery) {
        String time = delivery.receivedAt() > 0
                ? MESSAGE_TIME.format(Instant.ofEpochMilli(delivery.receivedAt()).atZone(ZoneId.systemDefault()))
                : "未知";
        String sender = delivery.sender().isEmpty() ? "未知" : delivery.sender();
        return delivery.test() ? "测试时间：" + time : "发件人：" + sender + "\n接收时间：" + time;
    }

    private static String messageText(ForwardingStore.Delivery delivery) {
        return messageTitle(delivery) + "\n" + messageDetails(delivery) + "\n\n" + delivery.body();
    }

    private static String truncateCharacters(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        int end = limit - TRUNCATION_NOTICE.length();
        if (Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) {
            end--;
        }
        return text.substring(0, end) + TRUNCATION_NOTICE;
    }

    private static String truncateUtf8(String text, int limit) {
        if (text.getBytes(StandardCharsets.UTF_8).length <= limit) {
            return text;
        }
        int remaining = limit - TRUNCATION_NOTICE.getBytes(StandardCharsets.UTF_8).length;
        int end = 0;
        while (end < text.length()) {
            int codePoint = text.codePointAt(end);
            int bytes = codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
            if (remaining < bytes) {
                break;
            }
            remaining -= bytes;
            end += Character.charCount(codePoint);
        }
        return text.substring(0, end) + TRUNCATION_NOTICE;
    }

    private static Result successResponse(WebhookPlatform platform, int status, HttpsURLConnection connection)
            throws IOException {
        if (platform == WebhookPlatform.GENERIC) {
            return Result.sent("发送成功（HTTP " + status + "）");
        }
        if (platform == WebhookPlatform.DISCORD) {
            return status == HttpsURLConnection.HTTP_OK
                    ? Result.sent("发送成功（Discord 已确认）")
                    : new Result("Discord 未确认接收消息，请检查 Webhook 配置", false);
        }
        String body;
        try (InputStream response = connection.getInputStream()) {
            body = readResponse(response);
        }
        if (body == null) {
            return new Result("无法确认" + responseLabel(platform) + "发送结果：响应过长", false);
        }
        if (platform == WebhookPlatform.SLACK) {
            return "ok".equals(body.trim()) ? Result.sent("发送成功（Slack 已确认）")
                    : new Result("Slack 未确认接收消息，请检查 Webhook 配置", false);
        }
        int code;
        try {
            JSONObject result = new JSONObject(body);
            String key = platform == WebhookPlatform.FEISHU
                    ? (result.has("code") ? "code" : "StatusCode") : "errcode";
            code = result.getInt(key);
        } catch (JSONException exception) {
            return new Result("无法确认" + responseLabel(platform) + "发送结果：响应缺少有效状态码", false);
        }
        return switch (platform) {
            case FEISHU -> feishuResult(code);
            case WECOM -> wecomResult(code);
            case DINGTALK -> dingTalkResult(code);
            default -> throw new IllegalArgumentException("未识别响应格式");
        };
    }

    private static String responseLabel(WebhookPlatform platform) {
        return platform == WebhookPlatform.FEISHU ? "飞书" : platform.label;
    }

    private static String readResponse(InputStream response) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = response.read(buffer)) != -1) {
            if (body.size() + count > MAX_RESPONSE_BYTES) {
                return null;
            }
            body.write(buffer, 0, count);
        }

        return new String(body.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Result feishuResult(int code) {
        return switch (code) {
            case 0 -> Result.sent("发送成功（飞书已确认）");
            case 19021 -> new Result("飞书签名校验失败，请检查密钥和设备时间（19021）", false);
            case 19022 -> new Result("飞书拒绝当前网络，请检查机器人 IP 白名单（19022）", false);
            case 19024 -> new Result("消息未包含机器人要求的关键词（19024）", false);
            case 11232 -> new Result("飞书限制发送频率（11232）", true);
            default -> new Result("飞书机器人返回错误（" + code + "）", false);
        };
    }

    private static Result wecomResult(int code) {
        return switch (code) {
            case 0 -> Result.sent("发送成功（企业微信已确认）");
            case 45009 -> new Result("企业微信限制发送频率（45009）", true);
            case 93000 -> new Result("企业微信机器人地址无效，请检查是否被移除（93000）", false);
            default -> new Result("企业微信机器人返回错误（" + code + "）", false);
        };
    }

    private static Result dingTalkResult(int code) {
        return switch (code) {
            case 0 -> Result.sent("发送成功（钉钉已确认）");
            case 130101 -> new Result("钉钉限制发送频率（130101）", true);
            case 310000 -> new Result("钉钉安全校验失败，请检查关键词、加签或 IP 白名单（310000）", false);
            default -> new Result("钉钉机器人返回错误（" + code + "）", false);
        };
    }

    private Webhook() {}
}

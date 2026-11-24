package com.smsforwarder.app;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

enum WebhookPlatform {
    AUTO("自动识别", "https://…", ""),
    FEISHU("飞书 / Lark", "https://open.feishu.cn/open-apis/bot/v2/hook/…", "签名密钥"),
    WECOM("企业微信", "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=…", ""),
    DINGTALK("钉钉", "https://oapi.dingtalk.com/robot/send?access_token=…", "加签密钥"),
    SLACK("Slack", "https://hooks.slack.com/services/…", ""),
    DISCORD("Discord", "https://discord.com/api/webhooks/…", ""),
    GENERIC("通用 Webhook", "https://example.com/webhook", "Bearer Token");

    private static final Pattern DISCORD_PATH =
            Pattern.compile("/api/(?:v[0-9]+/)?webhooks/[0-9]+/[A-Za-z0-9_-]+/?");

    final String label;
    final String endpointHint;
    final String credentialLabel;

    WebhookPlatform(String label, String endpointHint, String credentialLabel) {
        this.label = label;
        this.endpointHint = endpointHint;
        this.credentialLabel = credentialLabel;
    }

    static WebhookPlatform restore(String saved, String endpoint) {
        if (saved != null) {
            try {
                return valueOf(saved);
            } catch (IllegalArgumentException ignored) {
                return GENERIC;
            }
        }
        // Existing endpoints used the Feishu format or the original generic JSON.
        if (endpoint.isEmpty()) {
            return AUTO;
        }
        try {
            URI uri = URI.create(endpoint);
            return "open.feishu.cn".equalsIgnoreCase(uri.getHost()) && detect(uri) == FEISHU ? AUTO : GENERIC;
        } catch (IllegalArgumentException ignored) {
            return GENERIC;
        }
    }

    WebhookPlatform resolve(String endpoint) {
        try {
            return resolve(URI.create(endpoint.trim()));
        } catch (IllegalArgumentException ignored) {
            return this == AUTO ? GENERIC : this;
        }
    }

    WebhookPlatform resolve(URI endpoint) {
        return this == AUTO ? detect(endpoint) : this;
    }

    boolean acceptsCredential() {
        return !credentialLabel.isEmpty();
    }

    void validate(URI endpoint) {
        if (this != GENERIC && detect(endpoint) != this) {
            throw new IllegalArgumentException("请输入" + label + "的完整 Webhook 地址");
        }
        if (this == WECOM && !hasQueryValue(endpoint, "key")) {
            throw new IllegalArgumentException("企业微信地址缺少 key，请复制完整机器人地址");
        }
        if (this == DINGTALK && !hasQueryValue(endpoint, "access_token")) {
            throw new IllegalArgumentException("钉钉地址缺少 access_token，请复制完整机器人地址");
        }
        if (this == DINGTALK && (hasQueryParameter(endpoint, "timestamp") || hasQueryParameter(endpoint, "sign"))) {
            throw new IllegalArgumentException("请复制钉钉机器人原始地址，将加签密钥单独填写");
        }
    }

    private static WebhookPlatform detect(URI endpoint) {
        String host = endpoint.getHost();
        String path = endpoint.getRawPath();
        if (host == null || path == null || !"https".equalsIgnoreCase(endpoint.getScheme())) {
            return GENERIC;
        }
        return switch (host.toLowerCase(Locale.ROOT)) {
            case "open.feishu.cn", "open.larksuite.com" ->
                    hasPathSuffix(path, "/open-apis/bot/v2/hook/") ? FEISHU : GENERIC;
            case "qyapi.weixin.qq.com" -> path.equals("/cgi-bin/webhook/send") ? WECOM : GENERIC;
            case "oapi.dingtalk.com" -> path.equals("/robot/send") ? DINGTALK : GENERIC;
            case "hooks.slack.com", "hooks.slack-gov.com" ->
                    path.matches("/services/[^/]+/[^/]+/[^/]+/?") ? SLACK : GENERIC;
            case "discord.com" -> DISCORD_PATH.matcher(path).matches() ? DISCORD : GENERIC;
            default -> GENERIC;
        };
    }

    private static boolean hasPathSuffix(String path, String prefix) {
        return path.startsWith(prefix) && path.length() > prefix.length();
    }

    private static boolean hasQueryValue(URI endpoint, String key) {
        String value = queryValue(endpoint, key);
        return value != null && !value.trim().isEmpty();
    }

    private static boolean hasQueryParameter(URI endpoint, String key) {
        return queryValue(endpoint, key) != null;
    }

    private static String queryValue(URI endpoint, String key) {
        String query = endpoint.getRawQuery();
        if (query == null) {
            return null;
        }
        String prefix = key + "=";
        for (String part : query.split("&")) {
            if (part.startsWith(prefix)) {
                return part.substring(prefix.length());
            }
            if (part.equals(key)) {
                return "";
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return label;
    }
}

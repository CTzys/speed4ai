package com.speednet.module.xray.framework.panel;

import java.util.regex.Pattern;

/** Parses the first login pair, before an optional PostgreSQL credential section. */
public record XrayPanelCredentials(String username, String password) {
    public static XrayPanelCredentials parse(String output) {
        String text = output.replaceAll("\\u001B\\[[;\\d]*m", "").replace("\r", "");
        var match = Pattern.compile("(?im)^Username:[ \t]*(.+)\nPassword:[ \t]*(.+)$").matcher(text);
        if (!match.find()) throw new IllegalArgumentException("未找到面板登录账号密码");
        String username = match.group(1).strip(), password = match.group(2).strip();
        if (username.isEmpty() || password.isEmpty() || username.length() > 256 || password.length() > 1024)
            throw new IllegalArgumentException("面板登录账号密码无效");
        return new XrayPanelCredentials(username, password);
    }
}

package com.speednet.module.xray.framework.panel;

import java.util.regex.Pattern;

/** Connection settings reported by the official `x-ui setting -show true` command. */
public record XrayPanelSettings(String scheme, int port, String path) {
    private static final Pattern PORT = Pattern.compile("(?m)^port:\\s*(\\d+)\\s*$");
    private static final Pattern PATH = Pattern.compile("(?m)^webBasePath:[ \\t]*(\\S+)[ \\t]*$");

    public static XrayPanelSettings parse(String output) {
        String text = output.replaceAll("\\u001B\\[[;\\d]*m", "").replace("\r", "");
        var portMatch = PORT.matcher(text);
        var pathMatch = PATH.matcher(text);
        if (!portMatch.find() || !pathMatch.find()) throw new IllegalArgumentException("面板连接信息不完整");
        int port = Integer.parseInt(portMatch.group(1));
        String path = pathMatch.group(1);
        if (port < 1 || port > 65535 || !path.startsWith("/") || path.contains("..")
                || path.contains("?") || path.contains("#") || path.contains("\\")) {
            throw new IllegalArgumentException("面板连接信息无效");
        }
        String scheme;
        if (Pattern.compile("(?m)^Panel is secure with SSL[ \\t]*$").matcher(text).find()) scheme = "https";
        else if (Pattern.compile("(?m)^Warning: Panel is not secure with SSL[ \\t]*$").matcher(text).find()) scheme = "http";
        else throw new IllegalArgumentException("无法识别面板协议");
        return new XrayPanelSettings(scheme, port, path);
    }
}

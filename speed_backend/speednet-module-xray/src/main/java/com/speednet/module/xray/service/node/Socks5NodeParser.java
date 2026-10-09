package com.speednet.module.xray.service.node;
import com.speednet.module.xray.controller.admin.node.vo.XrayNodeSaveReqVO;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
public final class Socks5NodeParser {
    private static final Pattern COMPACT = Pattern.compile("^(\\[[^\\]]+\\]|[^:\\s]+):([0-9]+):([^:]+):(.+)$");
    private Socks5NodeParser() {}
    public static XrayNodeSaveReqVO parse(String text) {
        try {
            String input=text.trim();
            var compact=COMPACT.matcher(input);
            if(compact.matches()) {
                // Compact credentials are literal; only URI credentials are URL decoded.
                var node=new XrayNodeSaveReqVO().setHost(compact.group(1)).setPort(Integer.parseInt(compact.group(2)))
                        .setAuthType(1).setUsername(compact.group(3)).setPassword(compact.group(4));
                node.setName(compact.group(1)+":"+node.getPort());
                return normalize(node);
            }
            URI uri=URI.create(input);
            if (!"socks5".equalsIgnoreCase(uri.getScheme()) && !"socks".equalsIgnoreCase(uri.getScheme())) fail("只支持 socks5:// 或 socks:// 连接");
            if (uri.getRawQuery()!=null || (uri.getRawPath()!=null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))) fail("连接不能包含路径或查询参数");
            var node=new XrayNodeSaveReqVO().setHost(uri.getHost()).setPort(uri.getPort()).setAuthType(0).setUsername("").setPassword("");
            if (uri.getRawUserInfo()!=null) {
                String[] parts=uri.getRawUserInfo().split(":",2);
                if(parts.length!=2) fail("认证连接需要用户名和密码");
                node.setAuthType(1).setUsername(decode(parts[0])).setPassword(decode(parts[1]));
            }
            node.setName(uri.getRawFragment()==null ? node.getHost()+":"+node.getPort() : decode(uri.getRawFragment()));
            return normalize(node);
        } catch (IllegalArgumentException e) {
            // Never include the raw URI: it may contain credentials.
            throw new IllegalArgumentException("连接格式无效，请检查协议、地址、端口和认证信息");
        }
    }
    private static String decode(String v) { return URLDecoder.decode(v.replace("+","%2B"),StandardCharsets.UTF_8); }
    public static XrayNodeSaveReqVO normalize(XrayNodeSaveReqVO node) {
        String host=node.getHost()==null?"":node.getHost().trim().toLowerCase(Locale.ROOT);
        if(host.startsWith("[")&&host.endsWith("]")) host=host.substring(1,host.length()-1);
        if(host.endsWith(".")) host=host.substring(0,host.length()-1);
        if(host.isBlank() || host.length()>255 || host.contains("/") || host.contains("@") || host.chars().anyMatch(Character::isWhitespace)) fail("节点地址无效");
        try { if(new URI("socks5",null,host,1080,null,null,null).getHost()==null) fail("节点地址无效"); }
        catch(Exception e) { fail("节点地址无效"); }
        if(node.getPort()==null || node.getPort()<1 || node.getPort()>65535) fail("端口必须为 1–65535");
        if(node.getName()==null || node.getName().isBlank() || node.getName().length()>128) fail("节点名称不能为空且最多 128 字符");
        if(node.getAuthType()==null || (node.getAuthType()!=0 && node.getAuthType()!=1)) fail("认证方式无效");
        node.setHost(host).setName(node.getName().trim());
        if(node.getAuthType()==0) node.setUsername("").setPassword("");
        else {
            if(node.getUsername()==null || node.getUsername().isBlank() || node.getPassword()==null || node.getPassword().isEmpty()) fail("用户名和密码不能为空");
            if(node.getUsername().getBytes(StandardCharsets.UTF_8).length>255 || node.getPassword().getBytes(StandardCharsets.UTF_8).length>255) fail("认证信息最多 255 字节");
        }
        return node;
    }
    public static String identity(XrayNodeSaveReqVO node) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((node.getHost()+"\n"+node.getPort()+"\n"+node.getUsername()).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}

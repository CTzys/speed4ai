package com.speednet.module.xray.service.node;
import com.speednet.module.xray.dal.dataobject.node.XrayNodeDO;
import org.springframework.stereotype.Component;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
@Component
public class Socks5Probe {
    static final String TARGET="www.gstatic.com";
    public record Result(boolean success, int latencyMs, String message) {}
    public Result check(XrayNodeDO node) {
        long start=System.nanoTime();
        String stage="解析地址";
        try {
            InetAddress[] addresses=resolve(node.getHost());
            for(InetAddress address:addresses) if(!isPublic(address)) throw new IOException("节点地址不是公网地址");
            stage="TCP 连接";
            try(Socket socket=new Socket()) {
                socket.connect(new InetSocketAddress(addresses[0],node.getPort()),5000);
                socket.setSoTimeout(5000);
                // A total deadline also covers peers that keep sending one byte at a time.
                try(var deadline=Executors.newSingleThreadScheduledExecutor(r -> {Thread t=new Thread(r,"socks5-deadline");t.setDaemon(true);return t;})) {
                    var timer=deadline.schedule(() -> {try {socket.close();}catch(IOException ignored){}},15,TimeUnit.SECONDS);
                    try {
                        stage="SOCKS5 握手";
                        negotiate(socket,node);
                        stage="代理连接";
                        connectTarget(socket);
                        stage="代理 HTTPS 访问";
                        try(SSLSocket tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(socket,TARGET,443,true)) {
                            SSLParameters params=tls.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");tls.setSSLParameters(params);
                            tls.setSoTimeout(5000);tls.startHandshake();
                            tls.getOutputStream().write(("GET /generate_204 HTTP/1.1\r\nHost: "+TARGET+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                            String line=readLine(tls.getInputStream());
                            if(!line.matches("HTTP/1[.][01] 204(?: .*)?")) throw new IOException("检测目标返回异常状态");
                        }
                    } finally {timer.cancel(false);deadline.shutdownNow();}
                }
            }
            return new Result(true,(int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),"SOCKS5 认证和代理 HTTPS 请求成功（TCP）");
        } catch(Exception e) {
            return new Result(false,0,stage+"失败"+(e instanceof SocketTimeoutException?"：超时":"，请检查地址、认证及代理连通性"));
        }
    }
    protected InetAddress[] resolve(String host) throws UnknownHostException {return InetAddress.getAllByName(host);}
    static boolean isPublic(InetAddress a) {
        if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress()) return false;
        byte[] b=a.getAddress();
        if(b.length==16) return (b[0]&0xfe)!=0xfc;
        int x=b[0]&255,y=b[1]&255;
        return x!=0 && x<224 && !(x==100&&y>=64&&y<=127) && !(x==198&&(y==18||y==19));
    }
    static void negotiate(Socket socket,XrayNodeDO node) throws IOException {
        var in=new DataInputStream(socket.getInputStream());var out=socket.getOutputStream();
        int method=node.getAuthType()==1?2:0;
        out.write(new byte[]{5,1,(byte)method});out.flush();
        if(in.readUnsignedByte()!=5 || in.readUnsignedByte()!=method) throw new IOException("SOCKS5 认证方式不匹配");
        if(method==2) {
            byte[] user=node.getUsername().getBytes(StandardCharsets.UTF_8),pass=node.getPassword().getBytes(StandardCharsets.UTF_8);
            out.write(1);out.write(user.length);out.write(user);out.write(pass.length);out.write(pass);out.flush();
            if(in.readUnsignedByte()!=1 || in.readUnsignedByte()!=0) throw new IOException("认证失败");
        }
    }
    static void connectTarget(Socket socket) throws IOException {
        var in=new DataInputStream(socket.getInputStream());var out=socket.getOutputStream();byte[] domain=TARGET.getBytes(StandardCharsets.US_ASCII);
        out.write(new byte[]{5,1,0,3,(byte)domain.length});out.write(domain);out.write(new byte[]{1,(byte)187});out.flush();
        if(in.readUnsignedByte()!=5 || in.readUnsignedByte()!=0 || in.readUnsignedByte()!=0) throw new IOException("代理 CONNECT 失败");
        int type=in.readUnsignedByte();int length=switch(type){case 1 -> 4;case 4 -> 16;case 3 -> in.readUnsignedByte();default -> throw new IOException("响应地址类型无效");};
        in.readFully(new byte[length+2]);
    }
    private static String readLine(InputStream in) throws IOException {
        var result=new ByteArrayOutputStream();
        for(int i=0;i<1024;i++){int b=in.read();if(b<0)throw new EOFException();if(b==10)return result.toString(StandardCharsets.US_ASCII).trim();result.write(b);}
        throw new IOException("响应过长");
    }
}

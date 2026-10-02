package com.speednet.module.xray.service.node;
import com.speednet.module.xray.dal.dataobject.node.XrayNodeDO;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class Socks5ProbeTest {
    @Test void refusesInternalAndSharedAddresses() throws Exception {
        for(String ip:new String[]{"127.0.0.1","10.1.2.3","169.254.169.254","192.168.1.1","100.64.0.1","::1","fc00::1"})assertFalse(Socks5Probe.isPublic(InetAddress.getByName(ip)),ip);
        assertTrue(Socks5Probe.isPublic(InetAddress.getByName("8.8.8.8")));
        var r=new Socks5Probe().check(new XrayNodeDO().setHost("127.0.0.1").setPort(1).setAuthType(0));assertFalse(r.success());
    }
    @Test void usernamePasswordNegotiationAndConnectAreRealProtocol() throws Exception {
        try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());var executor=Executors.newSingleThreadExecutor()){
            var future=executor.submit(()->{try(var peer=server.accept()){
                peer.setSoTimeout(3000);var in=new DataInputStream(peer.getInputStream());var out=peer.getOutputStream();
                assertArrayEquals(new byte[]{5,1,2},in.readNBytes(3));out.write(new byte[]{5,2});out.flush();
                assertEquals(1,in.readUnsignedByte());assertEquals("user",new String(in.readNBytes(in.readUnsignedByte())));assertEquals("secret",new String(in.readNBytes(in.readUnsignedByte())));
                out.write(new byte[]{1,0});out.flush();assertArrayEquals(new byte[]{5,1,0,3},in.readNBytes(4));assertEquals(Socks5Probe.TARGET,new String(in.readNBytes(in.readUnsignedByte())));assertEquals(443,in.readUnsignedShort());
                out.write(new byte[]{5,0,0,1,0,0,0,0,0,0});out.flush();return true;
            }});
            try(var socket=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort())){socket.setSoTimeout(3000);Socks5Probe.negotiate(socket,new XrayNodeDO().setAuthType(1).setUsername("user").setPassword("secret"));Socks5Probe.connectTarget(socket);}
            assertTrue(future.get(5,TimeUnit.SECONDS));
        }
    }
    @Test void authFailureDoesNotPass() throws Exception {
        try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());var executor=Executors.newSingleThreadExecutor()){
            var future=executor.submit(()->{try(var peer=server.accept()){peer.getInputStream().readNBytes(3);peer.getOutputStream().write(new byte[]{5,(byte)255});return true;}});
            try(var socket=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort())){socket.setSoTimeout(3000);assertThrows(IOException.class,()->Socks5Probe.negotiate(socket,new XrayNodeDO().setAuthType(0)));}future.get(5,TimeUnit.SECONDS);
        }
    }
}

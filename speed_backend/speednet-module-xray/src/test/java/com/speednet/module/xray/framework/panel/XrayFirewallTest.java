package com.speednet.module.xray.framework.panel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class XrayFirewallTest {
    @TempDir Path directory;
    private void tool(String name, String script) throws Exception {
        Path file = directory.resolve(name);
        Files.writeString(file, "#!/bin/sh\n" + script + "\n");
        assertTrue(file.toFile().setExecutable(true));
    }
    private String run() throws Exception {
        return run("tcp");
    }
    private String run(String transport) throws Exception {
        var builder = new ProcessBuilder("/bin/sh", "-c", XrayFirewall.command(3706, transport));
        builder.environment().put("PATH", directory + ":/usr/bin:/bin");
        builder.environment().put("CALLS", directory.resolve("calls").toString());
        var process = builder.redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
        return output;
    }
    @Test void activeUfwOpensOnlyActualTcpPort() throws Exception {
        tool("ufw", "if [ \"$1\" = status ]; then echo \"Status: active\"; else echo \"$*\" >> \"$CALLS\"; fi");
        assertTrue(run().contains("SPEEDNET_FIREWALL_OPENED"));
        assertEquals("allow 3706/tcp\n", Files.readString(directory.resolve("calls")));
    }
    @Test void inactiveUfwDoesNotEnableOrChangeRules() throws Exception {
        tool("ufw", "if [ \"$1\" = status ]; then echo \"Status: inactive\"; else exit 91; fi");
        run();
        assertFalse(Files.exists(directory.resolve("calls")));
    }
    @Test void firewalldUsesActiveZoneWithRuntimeAndPermanentRules() throws Exception {
        tool("firewall-cmd", "echo \"$*\" >> \"$CALLS\"; case \"$1\" in --state) echo running;; --get-active-zones) printf \"public\\n  interfaces: eth0\\n\";; esac");
        assertTrue(run().contains("SPEEDNET_FIREWALL_OPENED"));
        String calls = Files.readString(directory.resolve("calls"));
        assertTrue(calls.contains("--zone=public --add-port=3706/tcp"));
        assertTrue(calls.contains("--permanent --zone=public --add-port=3706/tcp"));
        assertFalse(calls.contains("--reload"));
    }
    @Test void unknownFirewallIsNotModified() throws Exception {
        tool("iptables", "exit 91");
        assertTrue(run().contains("SPEEDNET_FIREWALL_MANUAL"));
    }
    @Test void inactiveUfwDoesNotPreventFirewalldDefaultZoneRules() throws Exception {
        tool("ufw", "if [ \"$1\" = status ]; then echo \"Status: inactive\"; else exit 91; fi");
        tool("firewall-cmd", "echo \"$*\" >> \"$CALLS\"; case \"$1\" in --state) echo running;; --get-default-zone) echo public;; esac");
        assertTrue(run().contains("SPEEDNET_FIREWALL_OPENED"));
        String calls = Files.readString(directory.resolve("calls"));
        assertTrue(calls.contains("--get-default-zone"));
        assertTrue(calls.contains("--zone=public --add-port=3706/tcp"));
        assertTrue(calls.contains("--permanent --zone=public --add-port=3706/tcp"));
        assertTrue(calls.contains("--zone=public --query-port=3706/tcp"));
        assertTrue(calls.contains("--permanent --zone=public --query-port=3706/tcp"));
    }
    @Test void invalidPortNeverGeneratesCommand() {
        assertThrows(IllegalArgumentException.class, () -> XrayFirewall.command(0));
        assertThrows(IllegalArgumentException.class, () -> XrayFirewall.command(65536));
        assertThrows(IllegalArgumentException.class, () -> XrayFirewall.command(3706, "other"));
    }
    @Test void udpInboundOpensOnlyUdp() throws Exception {
        tool("ufw", "if [ \"$1\" = status ]; then echo \"Status: active\"; else echo \"$*\" >> \"$CALLS\"; fi");
        assertTrue(run("udp").contains("SPEEDNET_FIREWALL_OPENED"));
        assertEquals("allow 3706/udp\n", Files.readString(directory.resolve("calls")));
    }
}

package com.speednet.module.xray.framework.panel;

/** Narrow, idempotent rules for the installed panel's actual TCP port. */
public final class XrayFirewall {
    private XrayFirewall() {}
    public static String command(int port) {
        return command(port, "tcp");
    }
    public static String command(int port, String transport) {
        if (!"tcp".equals(transport) && !"udp".equals(transport)) throw new IllegalArgumentException("Invalid transport");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port");
        return ("set -e; export LC_ALL=C; opened=0; "
                + "if command -v ufw >/dev/null 2>&1; then "
                + "status=$(ufw status); if printf \"%s\\n\" \"$status\" | grep -q \"^Status: active\"; then "
                + "ufw allow " + port + "/tcp; opened=1; fi; fi; "
                + "if command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then "
                + "zones=$(firewall-cmd --get-active-zones | sed -n \"/^[^ ]/p\"); "
                + "if [ -z \"$zones\" ]; then zones=$(firewall-cmd --get-default-zone); fi; "
                + "if [ -z \"$zones\" ]; then echo SPEEDNET_FIREWALL_ZONE_MISSING >&2; exit 1; fi; "
                + "for zone in $zones; do "
                + "firewall-cmd --zone=\"$zone\" --add-port=" + port + "/tcp; "
                + "firewall-cmd --permanent --zone=\"$zone\" --add-port=" + port + "/tcp; "
                + "firewall-cmd --zone=\"$zone\" --query-port=" + port + "/tcp >/dev/null; "
                + "firewall-cmd --permanent --zone=\"$zone\" --query-port=" + port + "/tcp >/dev/null; "
                + "done; opened=1; fi; "
                + "if [ \"$opened\" = 1 ]; then echo SPEEDNET_FIREWALL_OPENED; "
                + "elif command -v iptables >/dev/null 2>&1 || command -v nft >/dev/null 2>&1; then echo SPEEDNET_FIREWALL_MANUAL; "
                + "else echo SPEEDNET_FIREWALL_INACTIVE; fi").replace("/tcp", "/" + transport);
    }
}

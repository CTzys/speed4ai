package com.speednet.module.xray.service.install;

import com.speednet.framework.common.pojo.PageParam;
import com.speednet.framework.common.pojo.PageResult;
import com.speednet.module.xray.controller.admin.install.vo.XrayInstallCreateReqVO;
import com.speednet.module.xray.dal.dataobject.install.XrayInstallLogDO;
import com.speednet.module.xray.dal.dataobject.install.XrayInstallTaskDO;
import com.speednet.module.xray.dal.dataobject.server.XrayServerDO;
import com.speednet.module.xray.dal.mysql.install.XrayInstallLogMapper;
import com.speednet.module.xray.dal.mysql.install.XrayInstallTaskMapper;
import com.speednet.module.xray.dal.mysql.server.XrayServerMapper;
import com.speednet.module.xray.framework.ssh.SshExecutor;
import com.speednet.module.xray.framework.panel.XrayPanelSettings;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import java.util.concurrent.ThreadLocalRandom;

import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.speednet.module.xray.enums.ErrorCodeConstants.*;

@Service
public class XrayInstallServiceImpl implements XrayInstallService {
    private static final Pattern VERSION = Pattern.compile("^v\\d+\\.\\d+\\.\\d+$");
    @Resource private XrayServerMapper serverMapper;
    @Resource private XrayInstallTaskMapper taskMapper;
    @Resource private XrayInstallLogMapper logMapper;
    @Resource private SshExecutor sshExecutor;
    @Resource @Lazy private XrayInstallService self;
    @Value("${xray.install.panel-port-min:1024}")
    private int panelPortMin = 1024;
    @Value("${xray.install.panel-port-max:9999}")
    private int panelPortMax = 9999;

    @Override public Long createTask(XrayInstallCreateReqVO reqVO) {
        if (serverMapper.selectById(reqVO.getServerId()) == null) throw exception(SERVER_NOT_EXISTS);
        if (taskMapper.selectRunningByServerId(reqVO.getServerId()) != null) throw exception(INSTALL_TASK_RUNNING);
        if (reqVO.getVersion() != null && !reqVO.getVersion().isBlank() && !VERSION.matcher(reqVO.getVersion()).matches()) {
            throw exception(INSTALL_VERSION_INVALID);
        }
        XrayInstallTaskDO task = new XrayInstallTaskDO().setServerId(reqVO.getServerId())
                .setVersion(reqVO.getVersion()).setStatus(0).setCurrentStep("等待执行");
        taskMapper.insert(task);
        self.executeTask(task.getId());
        return task.getId();
    }

    @Async
    @Override public void executeTask(Long taskId) {
        XrayInstallTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !Integer.valueOf(0).equals(task.getStatus())) return;
        XrayServerDO server = serverMapper.selectById(task.getServerId());
        updateTask(taskId, 1, "检测服务器", null, false);
        serverMapper.updateById(new XrayServerDO().setId(server.getId()).setInstallStatus(1));
        try {
            runStep(taskId, server, "检测系统", "command -v systemctl >/dev/null && uname -sm && command -v curl >/dev/null");
            runStep(taskId, server, "检测现有安装", "if command -v x-ui >/dev/null; then x-ui status || true; else echo NOT_INSTALLED; fi");
            Privilege privilege = detectPrivilege(taskId, server);
            if (panelPortMin < 1024 || panelPortMax > 65535 || panelPortMin > panelPortMax) {
                throw new IllegalStateException("面板随机端口范围无效，应满足 1024 <= 最小端口 <= 最大端口 <= 65535");
            }
            int panelPort = ThreadLocalRandom.current().nextInt(panelPortMin, panelPortMax + 1);
            String versionArg = task.getVersion() == null || task.getVersion().isBlank() ? "" : " " + task.getVersion();
            // Version is strictly validated above. The official installer verifies release SHA256 files.
            runStep(taskId, server, "安装 3x-ui",
                    "set -e; install_file=$(mktemp /tmp/speednet-3x-ui-install.XXXXXX); "
                            + "trap 'rm -f \"$install_file\"' EXIT; "
                            + "curl -fsSL https://raw.githubusercontent.com/MHSanaei/3x-ui/master/install.sh -o \"$install_file\"; "
                            + privilege.prefix() + "env XUI_NONINTERACTIVE=1 XUI_PANEL_PORT=" + panelPort
                            + " bash \"$install_file\"" + versionArg,
                    privilege.stdin());
            runStep(taskId, server, "验证服务", privilege.prefix()
                    + "sh -c 'systemctl is-active --quiet x-ui && x-ui status'", privilege.stdin());
            var panelSettings = readPanelSettings(taskId, server, privilege);
            configureFirewall(taskId, server, privilege, panelSettings == null ? null : panelSettings.port());
            configurePanelToken(taskId, server, privilege);
            updateTask(taskId, 2, "安装完成", null, true);
            serverMapper.updateById(new XrayServerDO().setId(server.getId()).setInstallStatus(2)
                    .setHealthStatus(1).setLastCheckTime(LocalDateTime.now()).setLastError(null));
        } catch (Exception e) {
            String message = sanitize(e.getMessage(), server);
            addLog(taskId, 2, "安装失败", message);
            updateTask(taskId, 3, "安装失败", message, true);
            serverMapper.updateById(new XrayServerDO().setId(server.getId()).setInstallStatus(3).setLastError(message));
        }
    }

    @Override public void syncPanelConfig(Long serverId) {
        XrayServerDO server = serverMapper.selectById(serverId);
        if (server == null) throw exception(SERVER_NOT_EXISTS);
        try {
            Privilege privilege = detectPrivilege(null, server);
            readPanelSettings(null, server, privilege);
            recoverPanelCredentials(server, privilege);
            configurePanelToken(null, server, privilege);
        } catch (com.speednet.framework.common.exception.ServiceException e) { throw e; }
        catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw exception(PANEL_API_FAILED, "读取失败，请检查已保存的 SSH 连接信息及 root/sudo 权限");
        }
    }

    private void runStep(Long taskId, XrayServerDO server, String step, String command) throws Exception {
        runStep(taskId, server, step, command, null);
    }

    private void runStep(Long taskId, XrayServerDO server, String step, String command, String stdin) throws Exception {
        updateTask(taskId, 1, step, null, false);
        addLog(taskId, 0, step, "开始执行");
        SshExecutor.CommandResult result = sshExecutor.execute(server, command, Duration.ofMinutes(15), stdin);
        if ("安装 3x-ui".equals(step)) {
            try {
                savePanelCredentials(server, result.output());
                addLog(taskId, 0, "保存面板账号", "已加密保存面板登录用户名和密码，可在服务器编辑窗口查看");
            }
            catch (IllegalArgumentException ignored) {
                addLog(taskId, 1, "保存面板账号", "安装输出未包含登录账号密码；已有面板账号保持不变，可尝试从服务器读取");
            }
        }
        String output = sanitize(result.output(), server);
        addLog(taskId, result.exitCode() == 0 ? 0 : 2, step, output);
        if (result.exitCode() != 0) throw new IllegalStateException(step + "失败，退出码 " + result.exitCode());
    }

    private Privilege detectPrivilege(Long taskId, XrayServerDO server) throws Exception {
        updateTask(taskId, 1, "检测安装权限", null, false);
        SshExecutor.CommandResult identity = sshExecutor.execute(server, "id -u", Duration.ofSeconds(10));
        if (identity.exitCode() == 0 && "0".equals(identity.output().trim())) {
            addLog(taskId, 0, "检测安装权限", "当前 SSH 用户已是 root");
            return new Privilege("", null);
        }
        SshExecutor.CommandResult passwordless = sshExecutor.execute(server, "sudo -n id -u", Duration.ofSeconds(10));
        if (passwordless.exitCode() == 0 && "0".equals(passwordless.output().trim())) {
            addLog(taskId, 0, "检测安装权限", "当前用户可通过免密码 sudo 执行安装");
            return new Privilege("sudo -n ", null);
        }
        if (server.getSshPassword() != null && !server.getSshPassword().isBlank()) {
            String passwordInput = server.getSshPassword() + "\n";
            SshExecutor.CommandResult passwordSudo = sshExecutor.execute(server,
                    "sudo -S -p '' id -u", Duration.ofSeconds(10), passwordInput);
            if (passwordSudo.exitCode() == 0 && "0".equals(passwordSudo.output().trim())) {
                addLog(taskId, 0, "检测安装权限", "当前用户可通过 SSH 密码使用 sudo 执行安装");
                return new Privilege("sudo -S -p '' ", passwordInput);
            }
        }
        throw new IllegalStateException("当前 SSH 用户没有可用的 root/sudo 权限；请使用 root、配置免密码 sudo，或使用与 sudo 密码相同的 SSH 密码");
    }

    private record Privilege(String prefix, String stdin) {}

    private XrayPanelSettings readPanelSettings(Long taskId, XrayServerDO server, Privilege privilege) {
        updateTask(taskId, 1, "读取面板连接信息", null, false);
        try {
            // Filter on the server: older releases may print credentials alongside settings.
            String command = privilege.prefix() + "bash -o pipefail -c 'cd /usr/local/x-ui && "
                    + "./x-ui setting -show true 2>/dev/null | "
                    + "sed -n -e \"/^port:/p\" -e \"/^webBasePath:/p\" "
                    + "-e \"/^Panel is secure with SSL/p\" -e \"/^Warning: Panel is not secure with SSL/p\"'";
            var result = sshExecutor.execute(server, command, Duration.ofSeconds(30), privilege.stdin());
            if (result.exitCode() != 0) throw new IllegalStateException();
            var settings = XrayPanelSettings.parse(result.output());
            serverMapper.updateById(new XrayServerDO().setId(server.getId()).setPanelScheme(settings.scheme())
                    .setPanelPort(settings.port()).setPanelPath(settings.path()));
            addLog(taskId, 0, "读取面板连接信息", "已自动保存面板协议、端口和基础路径");
            return settings;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            if (taskId == null) throw exception(PANEL_API_FAILED, "无法读取面板连接信息，请确认 3x-ui 已安装并支持配置读取");
            // Discovery is optional: a running installation must not become an installation failure.
            addLog(taskId, 1, "读取面板连接信息", "自动读取失败，安装服务已正常运行；请在服务器管理手动配置面板连接信息");
            return null;
        }
    }

    private void savePanelCredentials(XrayServerDO server, String output) {
        var credentials = com.speednet.module.xray.framework.panel.XrayPanelCredentials.parse(output);
        server.setPanelUsername(credentials.username());
        server.setPanelPassword(credentials.password());
        serverMapper.updateById(new XrayServerDO().setId(server.getId())
                .setPanelUsername(credentials.username()).setPanelPassword(credentials.password()));
    }

    private void recoverPanelCredentials(XrayServerDO server, Privilege privilege) throws Exception {
        if (server.getPanelUsername() != null && server.getPanelPassword() != null) return;
        String command = privilege.prefix() + "bash -c 'set -e; "
                + "test -f /etc/x-ui/install-result.env; "
                + "test $(stat -c %u /etc/x-ui/install-result.env) = 0; "
                + "test $(stat -c %a /etc/x-ui/install-result.env) = 600; "
                + ". /etc/x-ui/install-result.env; "
                + "printf \"Username: %s\\nPassword: %s\\n\" \"$XUI_USERNAME\" \"$XUI_PASSWORD\"'";
        var result = sshExecutor.execute(server, command, Duration.ofSeconds(30), privilege.stdin());
        if (result.exitCode() != 0) throw exception(PANEL_API_FAILED,
                "连接信息已保存，但没有可读取的安装凭据文件；请通过服务器 x-ui 菜单重置登录账号密码并妥善记录");
        try { savePanelCredentials(server, result.output()); }
        catch (IllegalArgumentException e) { throw exception(PANEL_API_FAILED, "安装凭据文件未包含有效账号密码"); }
    }

    private void configureFirewall(Long taskId, XrayServerDO server, Privilege privilege, Integer port) {
        updateTask(taskId, 1, "配置服务器防火墙", null, false);
        if (port == null) {
            addLog(taskId, 1, "配置服务器防火墙", "未能读取实际面板端口，请手动检查防火墙放行规则");
            return;
        }
        try {
            String command = privilege.prefix() + "sh -c '" + com.speednet.module.xray.framework.panel.XrayFirewall.command(port) + "'";
            var result = sshExecutor.execute(server, command, Duration.ofSeconds(45), privilege.stdin());
            if (result.exitCode() != 0) throw new IllegalStateException();
            boolean configured = result.output().contains("SPEEDNET_FIREWALL_OPENED");
            boolean inactive = result.output().contains("SPEEDNET_FIREWALL_INACTIVE");
            addLog(taskId, configured || inactive ? 0 : 1, "配置服务器防火墙",
                    configured ? "已放行实际面板端口 " + port + "/TCP（IPv4/IPv6 按现有防火墙配置生效）"
                            : inactive ? "UFW/firewalld 未启用，未更改防火墙；仍需确认云安全组和其他网络规则"
                            : "检测到其他防火墙规则，未自动修改；请手动放行 " + port + "/TCP");
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            addLog(taskId, 1, "配置服务器防火墙", "自动放行失败，服务已正常运行；请手动放行面板端口 " + port + "/TCP 并检查云安全组");
        }
    }

    private void configurePanelToken(Long taskId, XrayServerDO server, Privilege privilege) {
        updateTask(taskId, 1, "配置面板 API Token", null, false);
        try {
            XrayServerDO latest = serverMapper.selectById(server.getId());
            if (latest != null && latest.getPanelToken() != null && !latest.getPanelToken().isBlank()) {
                addLog(taskId, 0, "配置面板 API Token", "保留已配置的 API Token");
                return;
            }
            // Check named-token support first; never rotate another application's default CLI token.
            String command = privilege.prefix() + "bash -o pipefail -c 'cd /usr/local/x-ui && "
                    + "help_text=$(./x-ui setting -h 2>&1); "
                    + "printf \"%s\\n\" \"$help_text\" | grep -q -- -tokenName || exit 1; "
                    + "./x-ui setting -tokenName speednet-manager -getApiToken=true 2>/dev/null | sed -n \"/^apiToken:/p\"'";
            var result = sshExecutor.execute(server, command, Duration.ofSeconds(30), privilege.stdin());
            var match = Pattern.compile("(?m)^apiToken:[ \t]*(\\S+)[ \t]*$").matcher(result.output().replace("\r", ""));
            if (result.exitCode() != 0 || !match.find()) throw new IllegalStateException();
            String token = match.group(1);
            if (token.length() < 16 || token.length() > 4096) throw new IllegalStateException();
            serverMapper.updateById(new XrayServerDO().setId(server.getId()).setPanelToken(token));
            server.setPanelToken(token);
            addLog(taskId, 0, "配置面板 API Token", "已自动生成并加密保存 SpeedNet 专用 API Token");
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            if (taskId == null) throw exception(PANEL_API_FAILED, "面板连接信息已保存，但 Token 自动配置失败，请检查面板版本或手动填写 Token");
            addLog(taskId, 1, "配置面板 API Token", "自动配置 Token 失败或面板版本不支持；安装服务已正常运行，请在服务器管理手动填写 API Token");
        }
    }

    private void updateTask(Long id, int status, String step, String error, boolean finished) {
        if (id == null) return;
        XrayInstallTaskDO update = new XrayInstallTaskDO().setId(id).setStatus(status).setCurrentStep(step).setErrorMessage(error);
        if (status == 1) update.setStartTime(LocalDateTime.now());
        if (finished) update.setEndTime(LocalDateTime.now());
        taskMapper.updateById(update);
    }
    private void addLog(Long taskId, int level, String step, String content) {
        if (taskId == null) return;
        logMapper.insert(new XrayInstallLogDO().setTaskId(taskId).setLevel(level).setStep(step).setContent(safe(content)));
    }
    private String sanitize(String value, XrayServerDO server) {
        String output = value == null ? "" : value.replaceAll("\\u001B\\[[;\\d]*m", "");
        for (String secret : new String[]{server.getSshPassword(), server.getSshKeyPassphrase(), server.getPanelToken(), server.getPanelUsername(), server.getPanelPassword()}) {
            if (secret != null && !secret.isBlank()) output = output.replace(secret, "******");
        }
        output = output.replaceAll("(?im)^([ \t]*(?:username|password)[ \t]*:[ \t]*).*$", "$1******");
        output = output.replaceAll("(?im)((?:api\\s*token|apiToken)\\s*:\\s*)\\S+", "$1******");
        return safe(output);
    }
    private String safe(String value) {
        if (value == null) return "未知错误";
        return value.length() > 20_000 ? value.substring(0, 20_000) + "\n...日志已截断" : value;
    }
    @Override public PageResult<XrayInstallTaskDO> getPage(PageParam pageParam) { return taskMapper.selectPage(pageParam); }
    @Override public List<XrayInstallLogDO> getLogs(Long taskId) { return logMapper.selectByTaskId(taskId); }
}

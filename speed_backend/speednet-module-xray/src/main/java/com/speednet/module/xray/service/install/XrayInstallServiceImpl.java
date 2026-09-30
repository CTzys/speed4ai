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
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

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
            String versionArg = task.getVersion() == null || task.getVersion().isBlank() ? "" : " " + task.getVersion();
            // Version is strictly validated above. The official installer verifies release SHA256 files.
            runStep(taskId, server, "安装 3x-ui",
                    "set -e; install_file=$(mktemp /tmp/speednet-3x-ui-install.XXXXXX); "
                            + "trap 'rm -f \"$install_file\"' EXIT; "
                            + "curl -fsSL https://raw.githubusercontent.com/MHSanaei/3x-ui/master/install.sh -o \"$install_file\"; "
                            + "export XUI_NONINTERACTIVE=1; " + privilege.prefix() + "bash \"$install_file\"" + versionArg,
                    privilege.stdin());
            runStep(taskId, server, "验证服务", privilege.prefix()
                    + "sh -c 'systemctl is-active --quiet x-ui && x-ui status'", privilege.stdin());
            updateTask(taskId, 2, "安装完成", null, true);
            serverMapper.updateById(new XrayServerDO().setId(server.getId()).setInstallStatus(2)
                    .setHealthStatus(1).setLastCheckTime(LocalDateTime.now()).setLastError(null));
        } catch (Exception e) {
            String message = safe(e.getMessage());
            addLog(taskId, 2, "安装失败", message);
            updateTask(taskId, 3, "安装失败", message, true);
            serverMapper.updateById(new XrayServerDO().setId(server.getId()).setInstallStatus(3).setLastError(message));
        }
    }

    private void runStep(Long taskId, XrayServerDO server, String step, String command) throws Exception {
        runStep(taskId, server, step, command, null);
    }

    private void runStep(Long taskId, XrayServerDO server, String step, String command, String stdin) throws Exception {
        updateTask(taskId, 1, step, null, false);
        addLog(taskId, 0, step, "开始执行");
        SshExecutor.CommandResult result = sshExecutor.execute(server, command, Duration.ofMinutes(15), stdin);
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

    private void updateTask(Long id, int status, String step, String error, boolean finished) {
        XrayInstallTaskDO update = new XrayInstallTaskDO().setId(id).setStatus(status).setCurrentStep(step).setErrorMessage(error);
        if (status == 1) update.setStartTime(LocalDateTime.now());
        if (finished) update.setEndTime(LocalDateTime.now());
        taskMapper.updateById(update);
    }
    private void addLog(Long taskId, int level, String step, String content) {
        logMapper.insert(new XrayInstallLogDO().setTaskId(taskId).setLevel(level).setStep(step).setContent(safe(content)));
    }
    private String sanitize(String value, XrayServerDO server) {
        String output = value == null ? "" : value;
        for (String secret : new String[]{server.getSshPassword(), server.getSshKeyPassphrase(), server.getPanelToken()}) {
            if (secret != null && !secret.isBlank()) output = output.replace(secret, "******");
        }
        return safe(output);
    }
    private String safe(String value) {
        if (value == null) return "未知错误";
        return value.length() > 20_000 ? value.substring(0, 20_000) + "\n...日志已截断" : value;
    }
    @Override public PageResult<XrayInstallTaskDO> getPage(PageParam pageParam) { return taskMapper.selectPage(pageParam); }
    @Override public List<XrayInstallLogDO> getLogs(Long taskId) { return logMapper.selectByTaskId(taskId); }
}

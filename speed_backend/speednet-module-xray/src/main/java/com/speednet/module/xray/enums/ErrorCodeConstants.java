package com.speednet.module.xray.enums;
import com.speednet.framework.common.exception.ErrorCode;
public interface ErrorCodeConstants {
 ErrorCode SERVICE_OPERATION_FAILED=new ErrorCode(1_012_001_002,"服务操作失败：{}");
 ErrorCode PANEL_CONFIG_INVALID=new ErrorCode(1_012_003_000,"请先配置有效的面板协议、主机、端口、路径和 API Token");
 ErrorCode PANEL_API_FAILED=new ErrorCode(1_012_003_001,"3x-ui API 调用失败：{}");
 ErrorCode INBOUND_CONFIG_INVALID=new ErrorCode(1_012_003_002,"入站配置无效：{}");
 ErrorCode SERVER_NOT_EXISTS=new ErrorCode(1_012_001_000,"Xray 服务器不存在");
 ErrorCode SSH_CONNECT_FAILED=new ErrorCode(1_012_001_001,"SSH 连接失败：{}");
 ErrorCode INSTALL_TASK_RUNNING=new ErrorCode(1_012_002_000,"该服务器已有安装任务正在执行");
 ErrorCode INSTALL_VERSION_INVALID=new ErrorCode(1_012_002_001,"安装版本格式不正确");
}

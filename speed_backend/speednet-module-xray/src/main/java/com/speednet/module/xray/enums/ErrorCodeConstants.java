package com.speednet.module.xray.enums;
import com.speednet.framework.common.exception.ErrorCode;
public interface ErrorCodeConstants {
 ErrorCode SERVER_NOT_EXISTS=new ErrorCode(1_012_001_000,"Xray 服务器不存在");
 ErrorCode SSH_CONNECT_FAILED=new ErrorCode(1_012_001_001,"SSH 连接失败：{}");
 ErrorCode INSTALL_TASK_RUNNING=new ErrorCode(1_012_002_000,"该服务器已有安装任务正在执行");
 ErrorCode INSTALL_VERSION_INVALID=new ErrorCode(1_012_002_001,"安装版本格式不正确");
}

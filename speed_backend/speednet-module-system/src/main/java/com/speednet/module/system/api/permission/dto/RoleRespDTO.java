package com.speednet.module.system.api.permission.dto;

import com.speednet.framework.common.enums.CommonStatusEnum;
import lombok.Data;

/**
 * 角色 Response DTO
 *
 * @author SpeedNet
 */
@Data
public class RoleRespDTO {

    /**
     * 角色编号
     */
    private Long id;
    /**
     * 角色名称
     */
    private String name;
    /**
     * 角色编码
     */
    private String code;
    /**
     * 显示顺序
     */
    private Integer sort;
    /**
     * 角色状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

}

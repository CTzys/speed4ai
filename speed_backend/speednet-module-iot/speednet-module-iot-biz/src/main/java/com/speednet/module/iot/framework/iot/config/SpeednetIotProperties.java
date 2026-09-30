package com.speednet.module.iot.framework.iot.config;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * SpeedNet IoT 全局配置类
 *
 * @author SpeedNet
 */
@Component
@Data
public class SpeednetIotProperties {

    /**
     * 设备连接超时时间
     */
    private Duration keepAliveTime = Duration.ofMinutes(10);
    /**
     * 设备连接超时时间的因子
     *
     * 因为设备可能会有网络抖动，所以需要乘以一个因子，避免误判
     */
    private double keepAliveFactor = 1.5D;

}

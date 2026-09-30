package com.speednet.module.iot.framework.tdengine.core.annotation;

import com.baomidou.dynamic.datasource.annotation.DS;

import java.lang.annotation.*;

/**
 * TDEngine 数据源
 *
 * @author SpeedNet
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DS("tdengine")
public @interface TDengineDS {
}
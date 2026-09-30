package com.speednet.module.promotion.enums.common;

import com.speednet.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

// TODO SpeedNet：弱化这个状态
/**
 * 促销活动的状态枚举
 *
 * @author SpeedNet
 */
@AllArgsConstructor
@Getter
public enum PromotionActivityStatusEnum implements ArrayValuable<Integer> {

    WAIT(10, "未开始"),
    RUN(20, "进行中"),
    END(30, "已结束"),
    CLOSE(40, "已关闭");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(PromotionActivityStatusEnum::getStatus).toArray(Integer[]::new);

    /**
     * 状态值
     */
    private final Integer status;
    /**
     * 状态名
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}

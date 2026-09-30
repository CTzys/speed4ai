package com.speednet.module.trade.enums.delivery;

import com.speednet.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 配送方式枚举
 *
 * @author SpeedNet
 */
@Getter
@AllArgsConstructor
public enum DeliveryTypeEnum implements ArrayValuable<Integer> {

    EXPRESS(1, "快递发货"),
    PICK_UP(2, "用户自提"),;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(DeliveryTypeEnum::getType).toArray(Integer[]::new);

    /**
     * 配送方式
     */
    private final Integer type;
    /**
     * 状态名
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}

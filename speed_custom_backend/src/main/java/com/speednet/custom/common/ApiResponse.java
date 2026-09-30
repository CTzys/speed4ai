package com.speednet.custom.common;

public record ApiResponse<T>(int code, T data, String msg) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(0, data, "success"); }
}

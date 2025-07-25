package com.circuitbreaker.core;

/**
 * 熔断器状态枚举
 * 
 * CLOSED: 正常状态，请求正常通过
 * OPEN: 熔断状态，直接返回失败，不执行实际调用
 * HALF_OPEN: 试探状态，允许少量请求通过以测试服务是否恢复
 */
public enum CircuitBreakerState {

    /**
     * 关闭状态 - 正常状态，请求正常通过
     */
    CLOSED,

    /**
     * 开启状态 - 熔断状态，直接返回失败，不执行实际调用
     */
    OPEN,

    /**
     * 半开状态 - 试探状态，允许少量请求通过以测试服务是否恢复
     */
    HALF_OPEN
}
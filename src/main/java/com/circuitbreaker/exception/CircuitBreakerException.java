package com.circuitbreaker.exception;

/**
 * 熔断器异常基类
 */
public class CircuitBreakerException extends RuntimeException {

    private final String circuitBreakerName;

    public CircuitBreakerException(String circuitBreakerName, String message) {
        super(message);
        this.circuitBreakerName = circuitBreakerName;
    }

    public CircuitBreakerException(String circuitBreakerName, String message, Throwable cause) {
        super(message, cause);
        this.circuitBreakerName = circuitBreakerName;
    }

    /**
     * 获取熔断器名称
     */
    public String getCircuitBreakerName() {
        return circuitBreakerName;
    }
}
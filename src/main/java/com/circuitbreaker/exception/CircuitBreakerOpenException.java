package com.circuitbreaker.exception;

/**
 * 熔断器开启异常 - 当熔断器处于OPEN状态时抛出
 */
public class CircuitBreakerOpenException extends CircuitBreakerException {

    public CircuitBreakerOpenException(String circuitBreakerName) {
        super(circuitBreakerName, "Circuit breaker '" + circuitBreakerName + "' is OPEN");
    }

    public CircuitBreakerOpenException(String circuitBreakerName, String message) {
        super(circuitBreakerName, message);
    }
}
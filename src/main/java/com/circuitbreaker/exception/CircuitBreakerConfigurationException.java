package com.circuitbreaker.exception;

/**
 * 熔断器配置异常
 * 当熔断器配置参数不合法时抛出
 */
public class CircuitBreakerConfigurationException extends RuntimeException {

    public CircuitBreakerConfigurationException(String parameterName, Object parameterValue, String message) {
        super(String.format("Invalid configuration parameter '%s' with value '%s': %s",
                parameterName, parameterValue, message));
    }

    public CircuitBreakerConfigurationException(String message) {
        super(message);
    }

    public CircuitBreakerConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
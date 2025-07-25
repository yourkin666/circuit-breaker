package com.circuitbreaker.exception;

/**
 * 并发调用超限异常 - 当并发调用数超过限制时抛出
 */
public class TooManyConcurrentCallsException extends CircuitBreakerException {

    private final int maxConcurrentCalls;
    private final int currentCalls;

    public TooManyConcurrentCallsException(String circuitBreakerName, int maxConcurrentCalls, int currentCalls) {
        super(circuitBreakerName,
                "Too many concurrent calls for circuit breaker '" + circuitBreakerName +
                        "'. Max: " + maxConcurrentCalls + ", Current: " + currentCalls);
        this.maxConcurrentCalls = maxConcurrentCalls;
        this.currentCalls = currentCalls;
    }

    /**
     * 获取最大并发调用数限制
     */
    public int getMaxConcurrentCalls() {
        return maxConcurrentCalls;
    }

    /**
     * 获取当前并发调用数
     */
    public int getCurrentCalls() {
        return currentCalls;
    }
}
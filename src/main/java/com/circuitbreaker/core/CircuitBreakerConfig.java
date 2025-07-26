package com.circuitbreaker.core;

import com.circuitbreaker.annotation.CircuitBreakerProtected;
import com.circuitbreaker.exception.CircuitBreakerConfigurationException;

/**
 * 熔断器配置类
 * 
 * 包含熔断器的所有配置参数，并提供参数验证功能
 */
public class CircuitBreakerConfig {

    // 配置常量
    private static final int MAX_SLIDING_WINDOW_SIZE = 10000;
    private static final int MAX_HALF_OPEN_CALLS = 100;
    private static final long MIN_WAIT_DURATION_MS = 1000;

    private final String name;
    private final float failureRateThreshold;
    private final int minimumNumberOfCalls;
    private final int slidingWindowSize;
    private final long waitDurationInOpenState;
    private final int permittedNumberOfCallsInHalfOpenState;
    private final int maxConcurrentCalls;

    /**
     * 通过注解创建配置
     */
    public CircuitBreakerConfig(String name, CircuitBreakerProtected annotation) {
        this.name = validateName(name);
        this.failureRateThreshold = validateFailureRateThreshold(annotation.failureRateThreshold());
        this.minimumNumberOfCalls = validateMinimumNumberOfCalls(annotation.minimumNumberOfCalls());
        this.slidingWindowSize = validateSlidingWindowSize(annotation.slidingWindowSize());
        this.waitDurationInOpenState = validateWaitDurationInOpenState(annotation.waitDurationInOpenState());
        this.permittedNumberOfCallsInHalfOpenState = validatePermittedNumberOfCallsInHalfOpenState(
                annotation.permittedNumberOfCallsInHalfOpenState());
        this.maxConcurrentCalls = validateMaxConcurrentCalls(annotation.maxConcurrentCalls());

        // 交叉验证
        validateCrossParameters();
    }

    /**
     * 验证熔断器名称
     */
    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new CircuitBreakerConfigurationException("name", name,
                    "Circuit breaker name cannot be null or empty");
        }
        return name.trim();
    }

    /**
     * 验证失败率阈值
     */
    private float validateFailureRateThreshold(float threshold) {
        if (threshold < 0.0f || threshold > 100.0f) {
            throw new CircuitBreakerConfigurationException("failureRateThreshold", threshold,
                    "Failure rate threshold must be between 0.0 and 100.0");
        }
        return threshold;
    }

    /**
     * 验证最小调用次数
     */
    private int validateMinimumNumberOfCalls(int calls) {
        if (calls <= 0) {
            throw new CircuitBreakerConfigurationException("minimumNumberOfCalls", calls,
                    "Minimum number of calls must be positive");
        }
        return calls;
    }

    /**
     * 验证滑动窗口大小
     */
    private int validateSlidingWindowSize(int size) {
        if (size <= 0) {
            throw new CircuitBreakerConfigurationException("slidingWindowSize", size,
                    "Sliding window size must be positive");
        }
        if (size > MAX_SLIDING_WINDOW_SIZE) {
            throw new CircuitBreakerConfigurationException("slidingWindowSize", size,
                    "Sliding window size should not exceed " + MAX_SLIDING_WINDOW_SIZE + " for performance reasons");
        }
        return size;
    }

    /**
     * 验证开启状态等待时间
     */
    private long validateWaitDurationInOpenState(long duration) {
        if (duration <= 0) {
            throw new CircuitBreakerConfigurationException("waitDurationInOpenState", duration,
                    "Wait duration in open state must be positive");
        }
        if (duration < MIN_WAIT_DURATION_MS) {
            throw new CircuitBreakerConfigurationException("waitDurationInOpenState", duration,
                    "Wait duration in open state should be at least " + MIN_WAIT_DURATION_MS + "ms");
        }
        return duration;
    }

    /**
     * 验证半开状态允许的调用次数
     */
    private int validatePermittedNumberOfCallsInHalfOpenState(int calls) {
        if (calls <= 0) {
            throw new CircuitBreakerConfigurationException("permittedNumberOfCallsInHalfOpenState", calls,
                    "Permitted number of calls in half-open state must be positive");
        }
        if (calls > MAX_HALF_OPEN_CALLS) {
            throw new CircuitBreakerConfigurationException("permittedNumberOfCallsInHalfOpenState", calls,
                    "Permitted number of calls in half-open state should not exceed " + MAX_HALF_OPEN_CALLS);
        }
        return calls;
    }

    /**
     * 验证最大并发调用数
     */
    private int validateMaxConcurrentCalls(int calls) {
        if (calls < 0) {
            throw new CircuitBreakerConfigurationException("maxConcurrentCalls", calls,
                    "Max concurrent calls must be non-negative (0 means unlimited)");
        }
        return calls;
    }

    /**
     * 交叉验证参数
     */
    private void validateCrossParameters() {
        // 最小调用次数不应该大于滑动窗口大小
        if (minimumNumberOfCalls > slidingWindowSize) {
            throw new CircuitBreakerConfigurationException(
                    String.format("Minimum number of calls (%d) should not exceed sliding window size (%d)",
                            minimumNumberOfCalls, slidingWindowSize));
        }

    }

    // Getter methods
    public String getName() {
        return name;
    }

    public float getFailureRateThreshold() {
        return failureRateThreshold;
    }

    public int getMinimumNumberOfCalls() {
        return minimumNumberOfCalls;
    }

    public int getSlidingWindowSize() {
        return slidingWindowSize;
    }

    public long getWaitDurationInOpenState() {
        return waitDurationInOpenState;
    }

    public int getPermittedNumberOfCallsInHalfOpenState() {
        return permittedNumberOfCallsInHalfOpenState;
    }

    public int getMaxConcurrentCalls() {
        return maxConcurrentCalls;
    }

    @Override
    public String toString() {
        return "CircuitBreakerConfig{" +
                "name='" + name + '\'' +
                ", failureRateThreshold=" + failureRateThreshold +
                ", minimumNumberOfCalls=" + minimumNumberOfCalls +
                ", slidingWindowSize=" + slidingWindowSize +
                ", waitDurationInOpenState=" + waitDurationInOpenState +
                ", permittedNumberOfCallsInHalfOpenState=" + permittedNumberOfCallsInHalfOpenState +
                ", maxConcurrentCalls=" + maxConcurrentCalls +
                '}';
    }
}
package com.circuitbreaker.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 熔断器指标类
 * 
 * 管理熔断器的所有状态和统计数据，包括：
 * - 滑动窗口统计
 * - 状态管理
 * - 并发控制
 * - 时间窗口管理
 */
public class CircuitBreakerMetrics {

    private final CircuitBreakerConfig config;
    private final SlidingWindow slidingWindow;

    // 状态管理
    private final AtomicReference<CircuitBreakerState> state = new AtomicReference<>(CircuitBreakerState.CLOSED);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong  openStateStartTime = new AtomicLong(0);

    // 半开状态管理
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenFailureCount = new AtomicInteger(0);

    // 并发控制（信号量隔离）
    private final AtomicInteger activeCalls = new AtomicInteger(0);

    public CircuitBreakerMetrics(CircuitBreakerConfig config) {
        this.config = config;
        this.slidingWindow = new SlidingWindow(config.getSlidingWindowSize());
    }

    /**
     * 记录成功调用
     */
    public void recordSuccess() {
        slidingWindow.recordSuccess();
        lastFailureTime.set(0); // 重置失败时间

        // 如果在半开状态，增加成功计数
        if (state.get() == CircuitBreakerState.HALF_OPEN) {
            int successCount = halfOpenSuccessCount.incrementAndGet();
            // 如果达到配置的成功次数，转为CLOSED状态
            if (successCount >= config.getPermittedNumberOfCallsInHalfOpenState()) {
                transitionToClosed();
            }
        }
    }

    /**
     * 记录失败调用
     */
    public void recordFailure() {
        slidingWindow.recordFailure();
        lastFailureTime.set(System.currentTimeMillis());

        CircuitBreakerState currentState = state.get();

        if (currentState == CircuitBreakerState.HALF_OPEN) {
            // 半开状态下失败，直接转为OPEN状态
            halfOpenFailureCount.incrementAndGet();
            transitionToOpen();
        } else if (currentState == CircuitBreakerState.CLOSED) {
            // 检查是否需要从CLOSED转为OPEN
            if (shouldTransitionToOpen()) {
                transitionToOpen();
            }
        }
    }

    /**
     * 检查是否允许调用
     */
    public boolean allowRequest() {
        CircuitBreakerState currentState = state.get();

        switch (currentState) {
            case CLOSED:
                return tryAcquirePermit();
            case OPEN:
                return shouldTransitionToHalfOpen() ? transitionToHalfOpenAndAllow() : false;
            case HALF_OPEN:
                return tryAcquirePermit();
            default:
                return false;
        }
    }

    /**
     * 尝试获取调用许可（并发控制）
     */
    private boolean tryAcquirePermit() {
        if (config.getMaxConcurrentCalls() <= 0) {
            return true; // 无并发限制
        }

        int current = activeCalls.get();
        if (current >= config.getMaxConcurrentCalls()) {
            return false; // 超过并发限制
        }

        return activeCalls.compareAndSet(current, current + 1);
    }

    /**
     * 释放调用许可
     */
    public void releasePermit() {
        if (config.getMaxConcurrentCalls() > 0) {
            activeCalls.decrementAndGet();
        }
    }

    /**
     * 检查是否应该从CLOSED转为OPEN
     */
    private boolean shouldTransitionToOpen() {
        return slidingWindow.hasEnoughCalls(config.getMinimumNumberOfCalls()) &&
                slidingWindow.getFailureRate() >= config.getFailureRateThreshold();
    }

    /**
     * 检查是否应该从OPEN转为HALF_OPEN
     */
    private boolean shouldTransitionToHalfOpen() {
        long currentTime = System.currentTimeMillis();
        return currentTime - openStateStartTime.get() >= config.getWaitDurationInOpenState();
    }

    /**
     * 转换到OPEN状态
     */
    private void transitionToOpen() {
        if (state.compareAndSet(CircuitBreakerState.CLOSED, CircuitBreakerState.OPEN) ||
                state.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.OPEN)) {
            openStateStartTime.set(System.currentTimeMillis());
            resetHalfOpenCounters();
        }
    }

    /**
     * 转换到HALF_OPEN状态并允许请求
     */
    private boolean transitionToHalfOpenAndAllow() {
        if (state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
            resetHalfOpenCounters();
            return tryAcquirePermit();
        }
        return false;
    }

    /**
     * 转换到CLOSED状态
     */
    private void transitionToClosed() {
        if (state.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.CLOSED)) {
            resetHalfOpenCounters();
            // 可选：重置滑动窗口以获得新的开始
            // slidingWindow.reset();
        }
    }

    /**
     * 重置半开状态计数器
     */
    private void resetHalfOpenCounters() {
        halfOpenSuccessCount.set(0);
        halfOpenFailureCount.set(0);
    }

    // Getter methods
    public CircuitBreakerState getState() {
        return state.get();
    }

    public long getTotalCalls() {
        return slidingWindow.getTotalCalls();
    }

    public int getSuccessCount() {
        return slidingWindow.getSuccessCount();
    }

    public int getFailureCount() {
        return slidingWindow.getFailureCount();
    }

    public float getFailureRate() {
        return slidingWindow.getFailureRate();
    }

    public int getActiveCalls() {
        return activeCalls.get();
    }

    public CircuitBreakerConfig getConfig() {
        return config;
    }

    @Override
    public String toString() {
        return "CircuitBreakerMetrics{" +
                "name='" + config.getName() + '\'' +
                ", state=" + state.get() +
                ", totalCalls=" + getTotalCalls() +
                ", successCount=" + getSuccessCount() +
                ", failureCount=" + getFailureCount() +
                ", failureRate=" + String.format("%.2f", getFailureRate()) + "%" +
                ", activeCalls=" + getActiveCalls() +
                '}';
    }
}
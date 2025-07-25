package com.circuitbreaker.core;

import com.circuitbreaker.exception.CircuitBreakerOpenException;
import com.circuitbreaker.exception.TooManyConcurrentCallsException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * 熔断器注册中心
 * 
 * 负责管理所有熔断器实例，提供统一的熔断器访问和执行接口
 * 线程安全，支持高并发访问
 */
public class CircuitBreakerRegistry {

    private final ConcurrentMap<String, CircuitBreakerMetrics> circuitBreakers = new ConcurrentHashMap<>();

    /**
     * 执行受保护的调用 - 核心方法
     */
    public <T> T executeSupplier(String circuitBreakerName, CircuitBreakerConfig config, Supplier<T> supplier)
            throws Exception {
        CircuitBreakerMetrics metrics = getOrCreate(circuitBreakerName, config);
        return executeWithMetrics(metrics, supplier);
    }

    /**
     * 获取熔断器 - 用于监控和测试
     */
    public CircuitBreakerMetrics get(String name) {
        return circuitBreakers.get(name);
    }

    /**
     * 检查熔断器是否存在 - 用于测试
     */
    public boolean contains(String name) {
        return circuitBreakers.containsKey(name);
    }

    /**
     * 获取熔断器数量 - 用于监控
     */
    public int size() {
        return circuitBreakers.size();
    }

    /**
     * 获取所有熔断器状态信息 - 用于监控
     */
    public String getAllStatusInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Circuit Breaker Registry Status:\n");
        sb.append("Total circuit breakers: ").append(size()).append("\n\n");

        for (CircuitBreakerMetrics metrics : circuitBreakers.values()) {
            sb.append(metrics.toString()).append("\n");
        }

        return sb.toString();
    }

    // ========== 私有方法 ==========

    /**
     * 获取或创建熔断器
     */
    private CircuitBreakerMetrics getOrCreate(String name, CircuitBreakerConfig config) {
        return circuitBreakers.computeIfAbsent(name, k -> new CircuitBreakerMetrics(config));
    }

    /**
     * 使用指标对象执行调用
     */
    private <T> T executeWithMetrics(CircuitBreakerMetrics metrics, Supplier<T> supplier) throws Exception {
        // 1. 检查是否允许请求
        if (!metrics.allowRequest()) {
            CircuitBreakerState state = metrics.getState();

            if (state == CircuitBreakerState.OPEN) {
                throw new CircuitBreakerOpenException(metrics.getConfig().getName());
            } else {
                // 并发限制
                throw new TooManyConcurrentCallsException(
                        metrics.getConfig().getName(),
                        metrics.getConfig().getMaxConcurrentCalls(),
                        metrics.getActiveCalls());
            }
        }

        try {
            // 2. 执行实际调用
            T result = supplier.get();

            // 3. 记录成功
            metrics.recordSuccess();

            return result;
        } catch (Exception ex) {
            // 4. 记录失败
            metrics.recordFailure();
            throw ex;
        } finally {
            // 5. 释放许可
            metrics.releasePermit();
        }
    }
}
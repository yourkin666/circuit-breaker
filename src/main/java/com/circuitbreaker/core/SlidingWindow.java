package com.circuitbreaker.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 滑动窗口统计类
 * 
 * 基于计数的滑动窗口，用于统计最近N次调用的成功/失败情况
 * 使用原子引用数组和计数器实现，完全线程安全，高性能
 */
public class SlidingWindow {

    private final int windowSize;
    private final AtomicReferenceArray<Boolean> results;
    private final AtomicInteger writeIndex = new AtomicInteger(0);
    private final AtomicLong totalCalls = new AtomicLong(0);
    
    // 优化：使用原子计数器减少遍历开销
    private final AtomicInteger successCounter = new AtomicInteger(0);
    private final AtomicInteger failureCounter = new AtomicInteger(0);

    public SlidingWindow(int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("Window size must be positive, got: " + windowSize);
        }
        this.windowSize = windowSize;
        this.results = new AtomicReferenceArray<>(windowSize);
    }

    /**
     * 记录一次成功调用
     */
    public void recordSuccess() {
        recordResult(true);
    }

    /**
     * 记录一次失败调用
     */
    public void recordFailure() {
        recordResult(false);
    }

    /**
     * 记录调用结果 - 优化版本，使用计数器减少遍历
     */
    private void recordResult(boolean success) {
        int index = writeIndex.getAndIncrement() % windowSize;
        Boolean oldValue = results.getAndSet(index, success);
        
        // 更新计数器：先减去被覆盖的值，再加上新值
        if (oldValue != null) {
            if (oldValue) {
                successCounter.decrementAndGet();
            } else {
                failureCounter.decrementAndGet();
            }
        }
        
        if (success) {
            successCounter.incrementAndGet();
        } else {
            failureCounter.incrementAndGet();
        }
        
        totalCalls.incrementAndGet();
    }

    /**
     * 获取总调用次数（取窗口大小和实际调用数的最小值）
     */
    public long getTotalCalls() {
        return Math.min(totalCalls.get(), windowSize);
    }

    /**
     * 获取成功次数 - 直接从计数器读取
     */
    public int getSuccessCount() {
        long currentTotalCalls = totalCalls.get();
        return currentTotalCalls < windowSize ? successCounter.get() : 
               Math.max(0, successCounter.get());
    }

    /**
     * 获取失败次数 - 直接从计数器读取
     */
    public int getFailureCount() {
        long currentTotalCalls = totalCalls.get();
        return currentTotalCalls < windowSize ? failureCounter.get() : 
               Math.max(0, failureCounter.get());
    }

    /**
     * 获取失败率 (0.0 - 100.0)
     */
    public float getFailureRate() {
        int totalCount = (int) getTotalCalls();
        if (totalCount == 0) {
            return 0.0f;
        }
        int failures = getFailureCount();
        return (failures * 100.0f) / totalCount;
    }

    /**
     * 检查是否有足够的数据进行统计
     */
    public boolean hasEnoughCalls(int minimumCalls) {
        return getTotalCalls() >= minimumCalls;
    }


    @Override
    public String toString() {
        return "SlidingWindow{" +
                "windowSize=" + windowSize +
                ", totalCalls=" + getTotalCalls() +
                ", successCount=" + getSuccessCount() +
                ", failureCount=" + getFailureCount() +
                ", failureRate=" + String.format("%.2f", getFailureRate()) + "%" +
                '}';
    }
}
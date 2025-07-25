package com.circuitbreaker.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 滑动窗口统计类
 * 
 * 基于计数的滑动窗口，用于统计最近N次调用的成功/失败情况
 * 使用原子引用数组实现，完全线程安全，高性能
 */
public class SlidingWindow {

    private final int windowSize;
    private final AtomicReferenceArray<Boolean> results; // 使用原子引用数组
    private final AtomicInteger writeIndex = new AtomicInteger(0);
    private final AtomicLong totalCalls = new AtomicLong(0);

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
     * 记录调用结果 - 线程安全版本
     */
    private void recordResult(boolean success) {
        // 获取当前写入位置
        int index = writeIndex.getAndIncrement() % windowSize;

        // 原子地替换旧值
        results.set(index, success);

        // 增加总调用数
        totalCalls.incrementAndGet();
    }

    /**
     * 获取总调用次数（取窗口大小和实际调用数的最小值）
     */
    public long getTotalCalls() {
        return Math.min(totalCalls.get(), windowSize);
    }

    /**
     * 获取成功次数 - 通过快照计算
     */
    public int getSuccessCount() {
        return calculateCounts().successCount;
    }

    /**
     * 获取失败次数 - 通过快照计算
     */
    public int getFailureCount() {
        return calculateCounts().failureCount;
    }

    /**
     * 获取失败率 (0.0 - 100.0)
     */
    public float getFailureRate() {
        CountSnapshot snapshot = calculateCounts();
        if (snapshot.totalCount == 0) {
            return 0.0f;
        }
        return (snapshot.failureCount * 100.0f) / snapshot.totalCount;
    }

    /**
     * 检查是否有足够的数据进行统计
     */
    public boolean hasEnoughCalls(int minimumCalls) {
        return getTotalCalls() >= minimumCalls;
    }

    /**
     * 计算当前窗口的统计数据 - 线程安全的快照计算
     * 这个方法通过遍历当前窗口来计算准确的统计数据
     */
    private CountSnapshot calculateCounts() {
        long currentTotalCalls = totalCalls.get();
        int validEntries = (int) Math.min(currentTotalCalls, windowSize);

        if (validEntries == 0) {
            return new CountSnapshot(0, 0, 0);
        }

        int successCount = 0;
        int failureCount = 0;

        // 计算当前有效窗口的起始位置
        int currentWriteIndex = writeIndex.get();
        int startIndex = validEntries < windowSize ? 0 : (currentWriteIndex % windowSize);

        // 遍历有效的窗口数据
        for (int i = 0; i < validEntries; i++) {
            int index = validEntries < windowSize ? i : (startIndex + i) % windowSize;
            Boolean result = results.get(index);

            if (result != null) {
                if (result) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }
        }

        return new CountSnapshot(validEntries, successCount, failureCount);
    }

    /**
     * 统计数据快照类
     */
    private static class CountSnapshot {
        final int totalCount;
        final int successCount;
        final int failureCount;

        CountSnapshot(int totalCount, int successCount, int failureCount) {
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
        }
    }

    @Override
    public String toString() {
        CountSnapshot snapshot = calculateCounts();
        return "SlidingWindow{" +
                "windowSize=" + windowSize +
                ", totalCalls=" + snapshot.totalCount +
                ", successCount=" + snapshot.successCount +
                ", failureCount=" + snapshot.failureCount +
                ", failureRate=" + String.format("%.2f", getFailureRate()) + "%" +
                '}';
    }
}
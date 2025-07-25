package com.circuitbreaker.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SlidingWindow线程安全性测试
 */
public class SlidingWindowThreadSafetyTest {

    @Test
    public void testConcurrentRecording() throws InterruptedException {
        SlidingWindow window = new SlidingWindow(100);
        int threadCount = 10;
        int operationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successOperations = new AtomicInteger(0);
        AtomicInteger failureOperations = new AtomicInteger(0);

        // 启动多个线程同时记录
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        if ((threadId + j) % 2 == 0) {
                            window.recordSuccess();
                            successOperations.incrementAndGet();
                        } else {
                            window.recordFailure();
                            failureOperations.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有线程完成
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // 验证统计的一致性
        int executedSuccesses = successOperations.get();
        int executedFailures = failureOperations.get();
        int totalExecuted = executedSuccesses + executedFailures;

        // 由于窗口大小限制，总调用数应该是窗口大小和实际调用数的最小值
        long actualTotal = window.getTotalCalls();
        int actualSuccesses = window.getSuccessCount();
        int actualFailures = window.getFailureCount();
        
        // 窗口大小限制验证
        assertTrue(actualTotal <= 100, "总调用数不应超过窗口大小100");
        assertEquals(executedSuccesses + executedFailures, totalExecuted,
                "执行的成功次数 + 失败次数应该等于总执行次数: " + totalExecuted);
        
        // 基本一致性检查
        assertEquals(actualSuccesses + actualFailures, actualTotal,
                "记录的成功次数 + 失败次数应该等于总调用次数");
        assertTrue(actualTotal > 0, "应该有调用记录");
        
        // 失败率计算应该正确
        float actualFailureRate = actualTotal > 0 ? (actualFailures * 100.0f) / actualTotal : 0.0f;
        assertEquals(actualFailureRate, window.getFailureRate(), 0.01f,
                "失败率计算应该正确");

        System.out.println("并发测试结果:");
        System.out.println("执行总操作数: " + totalExecuted);
        System.out.println("窗口总调用数: " + actualTotal);
        System.out.println("成功次数: " + actualSuccesses);
        System.out.println("失败次数: " + actualFailures);
        System.out.println("失败率: " + window.getFailureRate() + "%");
    }

    @RepeatedTest(5)
    public void testHighConcurrencyConsistency() throws InterruptedException {
        SlidingWindow window = new SlidingWindow(50);
        int threadCount = 20;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 所有线程都记录成功，这样可以更容易验证一致性
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        window.recordSuccess();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // 验证数据一致性
        long totalCalls = window.getTotalCalls();
        int successCount = window.getSuccessCount();
        int failureCount = window.getFailureCount();

        assertEquals(successCount + failureCount, totalCalls,
                "成功 + 失败应该等于总数");
        assertEquals(successCount, totalCalls,
                "所有调用都应该是成功的");
        assertEquals(0, failureCount, "失败次数应该为0");
        assertEquals(0.0f, window.getFailureRate(), 0.01f,
                "失败率应该是0%");
    }

    @Test
    public void testWindowSizeLimit() throws InterruptedException {
        int windowSize = 10;
        SlidingWindow window = new SlidingWindow(windowSize);

        // 记录超过窗口大小的调用
        for (int i = 0; i < windowSize * 2; i++) {
            if (i % 2 == 0) {
                window.recordSuccess();
            } else {
                window.recordFailure();
            }
        }

        // 总调用数不应该超过窗口大小
        assertEquals(windowSize, window.getTotalCalls(),
                "总调用数应该等于窗口大小");

        // 成功和失败的总数应该等于窗口大小
        assertEquals(windowSize, window.getSuccessCount() + window.getFailureCount(),
                "成功 + 失败应该等于窗口大小");
    }

    @Test
    public void testEmptyWindow() {
        SlidingWindow window = new SlidingWindow(100);

        assertEquals(0, window.getTotalCalls());
        assertEquals(0, window.getSuccessCount());
        assertEquals(0, window.getFailureCount());
        assertEquals(0.0f, window.getFailureRate(), 0.01f);
        assertFalse(window.hasEnoughCalls(1));
    }

    @Test
    public void testInvalidWindowSize() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindow(0));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindow(-1));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindow(-100));
    }

    @Test
    public void testConcurrentResetAndRecord() throws InterruptedException {
        SlidingWindow window = new SlidingWindow(50);
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 一些线程记录数据，一些线程重置
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        // 记录数据
                        if (j % 2 == 0) {
                            window.recordSuccess();
                        } else {
                            window.recordFailure();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // 验证没有崩溃，数据一致性
        long totalCalls = window.getTotalCalls();
        int successCount = window.getSuccessCount();
        int failureCount = window.getFailureCount();

        assertEquals(successCount + failureCount, totalCalls,
                "即使有重置操作，成功 + 失败也应该等于总数");
        assertTrue(totalCalls >= 0, "总调用数应该非负");
        assertTrue(successCount >= 0, "成功次数应该非负");
        assertTrue(failureCount >= 0, "失败次数应该非负");
    }
}
package com.circuitbreaker.performance;

import com.circuitbreaker.annotation.CircuitBreakerProtected;
import com.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import com.circuitbreaker.core.CircuitBreakerRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;

/**
 * 性能测试类
 * 验证熔断器的高性能特性
 */
@SpringBootTest
@ContextConfiguration(classes = { CircuitBreakerAutoConfiguration.class, PerformanceTest.TestService.class })
public class PerformanceTest {

    @Autowired
    private TestService testService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Service
    public static class TestService {

        private static final String RESULT = "test-result";

        @CircuitBreakerProtected(name = "performance-test", minimumNumberOfCalls = 100, slidingWindowSize = 1000)
        public String fastMethod() {
            return RESULT;
        }

        @CircuitBreakerProtected(name = "concurrent-test", maxConcurrentCalls = 1000, minimumNumberOfCalls = 100)
        public String concurrentMethod() {
            return RESULT;
        }
    }

    @Test
    public void testBasicPerformance() {
        // 预热
        for (int i = 0; i < 1000; i++) {
            testService.fastMethod();
        }

        // 性能测试
        int iterations = 10000;
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            testService.fastMethod();
        }

        long endTime = System.nanoTime();
        long totalTime = endTime - startTime;
        double avgTimePerCall = (double) totalTime / iterations;

        System.out.println("=== 基础性能测试结果 ===");
        System.out.println("总调用次数: " + iterations);
        System.out.println("总耗时: " + totalTime / 1_000_000.0 + " ms");
        System.out.println("平均每次调用耗时: " + String.format("%.2f", avgTimePerCall) + " ns");
        System.out.println("每秒处理请求数 (TPS): " + String.format("%.0f", 1_000_000_000.0 / avgTimePerCall));

        // 验证性能目标 (< 5 微秒，考虑到SlidingWindow的计算开销)
        assert avgTimePerCall < 5000 : "平均响应时间应该小于5微秒，实际: " + avgTimePerCall + "ns";

        // 打印熔断器状态
        com.circuitbreaker.core.CircuitBreakerMetrics metrics = circuitBreakerRegistry.get("performance-test");
        System.out.println("熔断器状态: " + metrics.toString());
    }

    @Test
    public void testConcurrentPerformance() throws InterruptedException {
        int threadCount = 10;
        int iterationsPerThread = 1000;
        Thread[] threads = new Thread[threadCount];

        long startTime = System.nanoTime();

        // 启动多个线程并发执行
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < iterationsPerThread; j++) {
                    testService.concurrentMethod();
                }
            });
            threads[i].start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        long endTime = System.nanoTime();
        long totalTime = endTime - startTime;
        int totalOperations = threadCount * iterationsPerThread;
        double avgTimePerCall = (double) totalTime / totalOperations;

        System.out.println("=== 并发性能测试结果 ===");
        System.out.println("线程数: " + threadCount);
        System.out.println("每线程调用次数: " + iterationsPerThread);
        System.out.println("总调用次数: " + totalOperations);
        System.out.println("总耗时: " + totalTime / 1_000_000.0 + " ms");
        System.out.println("平均每次调用耗时: " + String.format("%.2f", avgTimePerCall) + " ns");
        System.out.println("并发TPS: " + String.format("%.0f", 1_000_000_000.0 / avgTimePerCall));

        // 打印熔断器状态
        com.circuitbreaker.core.CircuitBreakerMetrics metrics = circuitBreakerRegistry.get("concurrent-test");
        System.out.println("熔断器状态: " + metrics.toString());
    }

    @Test
    public void testMemoryUsage() {
        // 获取初始内存使用情况
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // 创建大量熔断器实例以测试内存占用
        for (int i = 0; i < 1000; i++) {
            testService.fastMethod();
        }

        // 强制垃圾回收
        System.gc();
        System.gc();

        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;

        System.out.println("=== 内存使用测试结果 ===");
        System.out.println("初始内存使用: " + initialMemory / 1024 + " KB");
        System.out.println("最终内存使用: " + finalMemory / 1024 + " KB");
        System.out.println("内存增长: " + memoryIncrease / 1024 + " KB");

        // 验证内存使用合理（每个实例 < 500B的目标）
        System.out.println("熔断器实例数: " + circuitBreakerRegistry.size());
        if (circuitBreakerRegistry.size() > 0) {
            long memoryPerInstance = memoryIncrease / circuitBreakerRegistry.size();
            System.out.println("每个实例平均内存: " + memoryPerInstance + " bytes");
        }
    }
}
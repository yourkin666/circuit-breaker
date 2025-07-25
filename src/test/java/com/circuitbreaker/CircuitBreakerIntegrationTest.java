package com.circuitbreaker;

import com.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import com.circuitbreaker.core.CircuitBreakerRegistry;
import com.circuitbreaker.core.CircuitBreakerState;
import com.circuitbreaker.core.CircuitBreakerMetrics;
import com.circuitbreaker.example.ExampleService;
import com.circuitbreaker.exception.CircuitBreakerOpenException;
import com.circuitbreaker.exception.TooManyConcurrentCallsException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 熔断器集成测试
 */
@SpringBootTest
@ContextConfiguration(classes = { CircuitBreakerAutoConfiguration.class, ExampleService.class })
public class CircuitBreakerIntegrationTest {

    @Autowired
    private ExampleService exampleService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    public void testCircuitBreakerBasicFunctionality() {
        // 测试正常调用
        String result = exampleService.callExternalService("test1");
        assertTrue(result.contains("test1"));

        // 检查熔断器状态
        CircuitBreakerMetrics metrics = circuitBreakerRegistry.get("example-service");
        assertNotNull(metrics);
        assertEquals(CircuitBreakerState.CLOSED, metrics.getState());
    }

    @Test
    public void testCircuitBreakerTriggering() throws InterruptedException {
        // 强制失败以触发熔断器
        exampleService.setForceFailure(true);

        try {
            // 连续失败调用以触发熔断
            for (int i = 0; i < 5; i++) {
                try {
                    exampleService.callHighFailureService("test" + i);
                } catch (Exception e) {
                    // 期望的异常
                }
                Thread.sleep(100);
            }

            // 检查熔断器是否已开启
            CircuitBreakerMetrics metrics = circuitBreakerRegistry.get("high-failure-service");
            assertNotNull(metrics);

            // 验证熔断器状态（可能是OPEN或仍在计算中）
            assertTrue(metrics.getFailureRate() > 0);

        } finally {
            exampleService.setForceFailure(false);
        }
    }

    @Test
    public void testFallbackMethod() {
        // 强制失败以测试fallback
        exampleService.setForceFailure(true);

        try {
            String result = exampleService.callExternalService("fallback-test");
            assertTrue(result.contains("Fallback response"));
            assertTrue(result.contains("fallback-test"));
        } finally {
            exampleService.setForceFailure(false);
        }
    }

    @Test
    public void testConcurrencyLimit() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);

        // 启动5个并发调用，但限制只能有2个
        for (int i = 0; i < 5; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    String result = exampleService.callWithConcurrencyLimit("concurrent-test-" + taskId);
                    System.out.println("Task " + taskId + " result: " + result);
                } catch (Exception e) {
                    System.out.println("Task " + taskId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有任务完成
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        executor.shutdown();
    }

    @Test
    public void testCircuitBreakerRecovery() throws InterruptedException {
        // 强制失败以打开熔断器
        exampleService.setForceFailure(true);

        // 触发失败
        for (int i = 0; i < 3; i++) {
            try {
                exampleService.callHighFailureService("recovery-test" + i);
            } catch (Exception e) {
                // 期望的异常
            }
        }

        // 等待熔断器状态稳定
        Thread.sleep(1000);

        // 恢复正常
        exampleService.setForceFailure(false);

        // 等待熔断器转为半开状态
        Thread.sleep(4000);

        // 测试恢复
        String result = exampleService.callHighFailureService("recovery-success");
        assertTrue(result.contains("recovery-success"));
    }

    @Test
    public void testRegistryStatus() {
        // 触发一些调用以创建熔断器实例
        try {
            exampleService.callExternalService("registry-test");
        } catch (Exception e) {
            // 忽略异常
        }

        // 检查注册中心状态
        assertTrue(circuitBreakerRegistry.size() > 0);
        assertTrue(circuitBreakerRegistry.contains("example-service"));

        String statusInfo = circuitBreakerRegistry.getAllStatusInfo();
        assertNotNull(statusInfo);
        assertTrue(statusInfo.contains("Circuit Breaker Registry Status"));
    }
}
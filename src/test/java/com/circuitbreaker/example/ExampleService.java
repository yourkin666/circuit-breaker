package com.circuitbreaker.example;

import com.circuitbreaker.annotation.CircuitBreakerProtected;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * 示例服务类，用于测试熔断器功能
 */
@Service
public class ExampleService {

    private final Random random = new Random();
    private volatile boolean forceFailure = false;

    /**
     * 基础熔断器示例
     */
    @CircuitBreakerProtected(name = "example-service", fallbackMethod = "fallbackMethod", failureRateThreshold = 50, minimumNumberOfCalls = 3, slidingWindowSize = 10, waitDurationInOpenState = 5000)
    public String callExternalService(String param) {
        if (forceFailure || random.nextInt(100) < 30) { // 30% 失败率
            throw new RuntimeException("External service call failed for: " + param);
        }
        return "Success response for: " + param;
    }

    /**
     * fallback方法
     */
    public String fallbackMethod(String param, Exception ex) {
        return "Fallback response for: " + param + " (error: " + ex.getMessage() + ")";
    }

    /**
     * 并发限制示例
     */
    @CircuitBreakerProtected(name = "concurrent-limited-service", fallbackMethod = "concurrentFallback", maxConcurrentCalls = 2, failureRateThreshold = 80, minimumNumberOfCalls = 5)
    public String callWithConcurrencyLimit(String param) throws InterruptedException {
        // 模拟长时间执行
        Thread.sleep(1000);

        if (random.nextInt(100) < 20) { // 20% 失败率
            throw new RuntimeException("Service call failed: " + param);
        }
        return "Concurrent response for: " + param;
    }

    /**
     * 并发限制fallback方法
     */
    public String concurrentFallback(String param, Exception ex) {
        return "Concurrent fallback for: " + param + " (error: " + ex.getMessage() + ")";
    }

    /**
     * 高失败率示例（容易触发熔断）
     */
    @CircuitBreakerProtected(name = "high-failure-service", fallbackMethod = "highFailureFallback", failureRateThreshold = 30, minimumNumberOfCalls = 2, slidingWindowSize = 5, waitDurationInOpenState = 3000)
    public String callHighFailureService(String param) {
        if (forceFailure || random.nextInt(100) < 70) { // 70% 失败率
            throw new RuntimeException("High failure service error: " + param);
        }
        return "High failure service success: " + param;
    }

    /**
     * 高失败率fallback方法
     */
    public String highFailureFallback(String param, Exception ex) {
        return "High failure fallback: " + param + " (error: " + ex.getMessage() + ")";
    }

    // 控制方法，用于测试
    public void setForceFailure(boolean forceFailure) {
        this.forceFailure = forceFailure;
    }

    public boolean isForceFailure() {
        return forceFailure;
    }
}
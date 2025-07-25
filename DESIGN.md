# Java 轻量级熔断器组件技术设计文档

## 1. 项目概述

### 1.1 项目目标

设计并实现一个轻量级、高性能的 Java 熔断器组件，可作为 Maven 依赖被其他项目集成使用。

### 1.2 核心特性

- **轻量级**: 最小外部依赖，jar 包大小 < 30KB
- **高性能**: 纳秒级响应时间，支持高并发（>5,000 TPS）
- **线程安全**: 完全线程安全的实现，基于无锁数据结构
- **简单易用**: 基于注解的声明式使用方式，零配置开箱即用
- **双重保护**: 熔断器 + 信号量隔离，防止系统过载
- **易于集成**: 深度集成 Spring Boot，自动配置

## 2. 架构设计

### 2.1 核心概念

#### 2.1.1 熔断器状态

```
CLOSED (关闭) -> OPEN (开启) -> HALF_OPEN (半开) -> CLOSED/OPEN
```

- **CLOSED**: 正常状态，请求正常通过
- **OPEN**: 熔断状态，直接返回失败，不执行实际调用
- **HALF_OPEN**: 试探状态，允许少量请求通过以测试服务是否恢复

#### 2.1.2 状态转换规则

- CLOSED -> OPEN: 失败率超过阈值
- OPEN -> HALF_OPEN: 超过指定时间窗口
- HALF_OPEN -> CLOSED: 试探请求成功
- HALF_OPEN -> OPEN: 试探请求失败

### 2.2 模块架构

```
circuit-breaker/
├── src/main/java/com/circuitbreaker/
│   ├── core/
│   │   ├── CircuitBreakerState.java      # 状态枚举
│   │   ├── CircuitBreakerConfig.java     # 配置类
│   │   ├── CircuitBreakerRegistry.java   # 熔断器注册中心
│   │   ├── SlidingWindow.java            # 滑动窗口
│   │   └── CircuitBreakerMetrics.java    # 基础指标
│   ├── exception/
│   │   ├── CircuitBreakerException.java       # 熔断异常基类
│   │   ├── CircuitBreakerOpenException.java   # 熔断器开启异常
│   │   └── TooManyConcurrentCallsException.java # 并发调用超限异常
│   ├── annotation/
│   │   └── CircuitBreakerProtected.java  # 方法保护注解
│   └── autoconfigure/
│       ├── CircuitBreakerAspect.java     # AOP切面
│       └── CircuitBreakerAutoConfiguration.java # 自动配置
└── src/test/java/                        # 测试用例
```

## 3. 核心设计

### 3.1 注解定义

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CircuitBreakerProtected {

    /**
     * 熔断器名称，用于标识和配置
     */
    String name();

    /**
     * 失败回退方法名
     */
    String fallbackMethod() default "";

    /**
     * 失败率阈值 (0.0 - 100.0)
     */
    float failureRateThreshold() default 50;

    /**
     * 最小调用次数
     */
    int minimumNumberOfCalls() default 10;

    /**
     * 滑动窗口大小
     */
    int slidingWindowSize() default 100;

    /**
     * OPEN状态等待时间(毫秒)
     */
    long waitDurationInOpenState() default 60000;

    /**
     * 半开状态允许的调用次数
     */
    int permittedNumberOfCallsInHalfOpenState() default 3;

    /**
     * 最大并发调用数（信号量隔离）
     * 0 表示不限制并发
     */
    int maxConcurrentCalls() default 0;
}
```

### 3.2 配置类设计

```java
public class CircuitBreakerConfig {

    // 失败率阈值 (0.0 - 100.0)
    private final float failureRateThreshold;

    // 最小调用次数
    private final int minimumNumberOfCalls;

    // 滑动窗口大小
    private final int slidingWindowSize;

    // OPEN状态等待时间(毫秒)
    private final long waitDurationInOpenState;

    // 半开状态允许的调用次数
    private final int permittedNumberOfCallsInHalfOpenState;

    // 最大并发调用数
    private final int maxConcurrentCalls;

    public CircuitBreakerConfig(String name, CircuitBreakerProtected annotation) {
        this.failureRateThreshold = annotation.failureRateThreshold();
        this.minimumNumberOfCalls = annotation.minimumNumberOfCalls();
        this.slidingWindowSize = annotation.slidingWindowSize();
        this.waitDurationInOpenState = annotation.waitDurationInOpenState();
        this.permittedNumberOfCallsInHalfOpenState = annotation.permittedNumberOfCallsInHalfOpenState();
        this.maxConcurrentCalls = annotation.maxConcurrentCalls();
    }

    // getter方法
    public float getFailureRateThreshold() { return failureRateThreshold; }
    public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
    public int getSlidingWindowSize() { return slidingWindowSize; }
    public long getWaitDurationInOpenState() { return waitDurationInOpenState; }
    public int getPermittedNumberOfCallsInHalfOpenState() { return permittedNumberOfCallsInHalfOpenState; }
    public int getMaxConcurrentCalls() { return maxConcurrentCalls; }
}
```

## 4. 实现细节

### 4.1 滑动窗口算法

使用基于时间的滑动窗口来统计成功/失败次数：

- 将时间窗口分为多个桶(bucket)
- 每个桶记录该时间段内的调用统计
- 定期清理过期的桶
- 实时计算失败率

### 4.2 状态管理

使用 AtomicReference 确保状态变更的线程安全：

```java
private final AtomicReference<CircuitBreakerState> state;
private final AtomicLong lastFailureTime;
private final AtomicInteger halfOpenSuccessCount;
```

### 4.3 自动配置机制

通过 Spring Boot 自动配置机制实现零配置使用：

- 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册自动配置类
- `CircuitBreakerAutoConfiguration` 自动创建 AOP 切面和相关 Bean
- 检测到 `@CircuitBreakerProtected` 注解时自动激活熔断功能

### 4.4 性能优化

- 使用无锁数据结构(AtomicReference, AtomicLong 等)
- 延迟计算失败率，避免每次调用都计算
- 预分配对象，减少 GC 压力
- 快速失败路径，OPEN 状态下直接返回

## 5. 使用方式

引入 Maven 依赖后，熔断器功能会自动启用，无需任何额外配置。直接在需要保护的方法上使用 `@CircuitBreakerProtected` 注解即可。

### 5.1 使用注解保护方法

#### 基础使用

```java
@Service
public class ApiService {

    @CircuitBreakerProtected(
        name = "external-api",
        fallbackMethod = "getFallbackData"
    )
    public String getData() {
        return externalApiClient.fetchData();
    }

    public String getFallbackData(Exception ex) {
        return "Default data";
    }
}
```

#### 自定义配置参数

```java
@Service
public class PaymentService {

    @CircuitBreakerProtected(
        name = "payment-gateway",
        fallbackMethod = "fallbackPayment",
        failureRateThreshold = 0.3,
        minimumNumberOfCalls = 5,
        waitDurationInOpenState = 30000,
        slidingWindowSize = 30
    )
    public PaymentResult processPayment(PaymentRequest request) {
        return paymentGateway.process(request);
    }

    public PaymentResult fallbackPayment(PaymentRequest request, Exception ex) {
        return PaymentResult.failed("Payment service temporarily unavailable");
    }
}
```

#### 指定异常类型和滑动窗口

```java
@Service
public class DatabaseService {

    @CircuitBreakerProtected(
        name = "database-query",
        fallbackMethod = "getCachedData",
        slidingWindowType = SlidingWindowType.TIME_BASED,
        slidingWindowSize = 30,
        minimumNumberOfCalls = 5,
        failureRateThreshold = 60,
        recordFailureExceptions = {SQLException.class, TimeoutException.class},
        ignoreExceptions = {ValidationException.class}
    )
    public UserData getUserData(Long userId) {
        return userRepository.findById(userId);
    }

    public UserData getCachedData(Long userId, Exception ex) {
        log.warn("Fallback for getUserData, exception: {}", ex.getMessage());
        return cacheService.getUserData(userId);
    }
}
```

#### 慢调用检测

```java
@Service
public class ExternalApiService {

    @CircuitBreakerProtected(
        name = "external-api",
        fallbackMethod = "getDefaultResponse",
        slowCallRateThreshold = 30,
        slowCallDurationThreshold = 5000,
        automaticTransitionFromOpenToHalfOpenEnabled = true
    )
    public ApiResponse callExternalApi(String param) {
        return externalApiClient.call(param);
    }

    public ApiResponse getDefaultResponse(String param, Exception ex) {
        return ApiResponse.defaultResponse();
    }
}
```

#### 基于配置文件的配置

```java
@Service
public class NotificationService {

    // 使用配置文件中的 shared 配置
    @CircuitBreakerProtected(
        name = "notification-service",
        baseConfig = "shared",
        fallbackMethod = "sendNotificationFallback"
    )
    public void sendNotification(String message) {
        notificationClient.send(message);
    }

    public void sendNotificationFallback(String message, Exception ex) {
        // 记录到本地队列稍后重试
        messageQueue.enqueue(message);
    }
}
```

#### 事件监听使用

```java
@Component
public class CircuitBreakerMonitor {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @PostConstruct
    public void registerEventListeners() {
        circuitBreakerRegistry.circuitBreaker("payment-service")
            .getEventPublisher()
            .onStateTransition(event -> {
                log.info("Payment service circuit breaker state changed: {} -> {}",
                    event.getFromState(), event.getToState());

                // 发送告警通知
                if (event.getToState() == CircuitBreakerState.OPEN) {
                    alertService.sendAlert("Payment service circuit breaker opened!");
                }
            })
            .onCallNotPermitted(event -> {
                metrics.incrementCounter("circuit.breaker.rejected.calls",
                    "service", "payment-service");
            });
    }
}
```

## 6. 配置参数说明

| 参数                                  | 类型  | 默认值 | 说明                     |
| ------------------------------------- | ----- | ------ | ------------------------ |
| failureRateThreshold                  | float | 50     | 失败率阈值(0-100)        |
| minimumNumberOfCalls                  | int   | 10     | 最小调用次数             |
| slidingWindowSize                     | int   | 100    | 滑动窗口大小             |
| waitDurationInOpenState               | long  | 60000  | OPEN 状态等待时间(毫秒)  |
| permittedNumberOfCallsInHalfOpenState | int   | 3      | 半开状态允许调用次数     |
| maxConcurrentCalls                    | int   | 0      | 最大并发调用数(0=不限制) |

## 7. 使用方式

引入 Maven 依赖后，熔断器功能会自动启用，无需任何额外配置。直接在需要保护的方法上使用 `@CircuitBreakerProtected` 注解即可。

### 7.1 基础使用

```java
@Service
public class ApiService {

    @CircuitBreakerProtected(
        name = "external-api",
        fallbackMethod = "getFallbackData"
    )
    public String getData() {
        return externalApiClient.fetchData();
    }

    public String getFallbackData(Exception ex) {
        return "Default data";
    }
}
```

### 7.2 自定义配置参数

```java
@Service
public class PaymentService {

    @CircuitBreakerProtected(
        name = "payment-gateway",
        fallbackMethod = "fallbackPayment",
        failureRateThreshold = 30,
        minimumNumberOfCalls = 5,
        waitDurationInOpenState = 30000
    )
    public PaymentResult processPayment(PaymentRequest request) {
        return paymentGateway.process(request);
    }

    public PaymentResult fallbackPayment(PaymentRequest request, Exception ex) {
        return PaymentResult.failed("Payment service temporarily unavailable");
    }
}
```

### 7.3 信号量隔离使用

```java
@Service
public class ExternalApiService {

    @CircuitBreakerProtected(
        name = "external-api",
        fallbackMethod = "fallbackResponse",
        maxConcurrentCalls = 10  // 最多同时10个并发调用
    )
    public ApiResponse callExternalApi(String param) {
        return externalApiClient.call(param);
    }

    public ApiResponse fallbackResponse(String param, Exception ex) {
        log.warn("调用失败，原因: {}", ex.getMessage());
        return ApiResponse.defaultResponse();
    }
}
```

### 7.4 完整配置示例

```java
@Service
public class CriticalService {

    @CircuitBreakerProtected(
        name = "critical-service",
        fallbackMethod = "fallbackMethod",
        failureRateThreshold = 20,      // 失败率达到20%即熔断
        minimumNumberOfCalls = 5,       // 至少5次调用才计算失败率
        maxConcurrentCalls = 20,        // 最多20个并发调用
        waitDurationInOpenState = 30000 // 熔断30秒后尝试恢复
    )
    public ServiceResult performCriticalOperation(Request request) {
        return criticalServiceClient.execute(request);
    }

    public ServiceResult fallbackMethod(Request request, Exception ex) {
        return ServiceResult.degraded("Service temporarily unavailable");
    }
}
```

## 8. 实现细节

### 8.1 滑动窗口算法

使用基于计数的滑动窗口来统计成功/失败次数：

- 固定大小的环形缓冲区
- 记录最近 N 次调用的结果
- 实时计算失败率

### 8.2 状态管理

使用 AtomicReference 确保状态变更的线程安全：

```java
private final AtomicReference<CircuitBreakerState> state;
private final AtomicLong lastFailureTime;
private final AtomicInteger halfOpenSuccessCount;
```

### 8.3 信号量隔离实现

使用原子计数器实现轻量级并发控制：

```java
public class CircuitBreakerWithSemaphore {
    private final AtomicInteger activeCalls = new AtomicInteger(0);
    private final int maxConcurrentCalls;

    public <T> T execute(Supplier<T> supplier) throws Exception {
        // 1. 检查并发限制
        if (maxConcurrentCalls > 0) {
            int current = activeCalls.get();
            if (current >= maxConcurrentCalls) {
                throw new TooManyConcurrentCallsException(
                    "超过最大并发数: " + maxConcurrentCalls);
            }
        }

        // 2. 检查熔断器状态
        if (state.get() == OPEN) {
            throw new CircuitBreakerOpenException();
        }

        // 3. 执行调用
        if (maxConcurrentCalls > 0) {
            activeCalls.incrementAndGet();
        }

        try {
            T result = supplier.get();
            recordSuccess();
            return result;
        } catch (Exception ex) {
            recordFailure();
            throw ex;
        } finally {
            if (maxConcurrentCalls > 0) {
                activeCalls.decrementAndGet();
            }
        }
    }
}
```

### 8.4 性能优化

- 使用无锁数据结构(AtomicReference, AtomicLong, AtomicInteger 等)
- 快速失败路径，OPEN 状态下直接返回
- 信号量检查的开销极小，只有一次原子操作
- 预分配固定大小的数据结构

## 9. 依赖配置

### Maven 依赖

在 Spring Boot 项目的 `pom.xml` 中添加以下依赖：

```xml
<dependency>
    <groupId>com.circuitbreaker</groupId>
    <artifactId>circuit-breaker-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 兼容性

- **Spring Boot**: 2.3+
- **JDK**: 1.8+

## 10. 性能指标目标

- **响应时间**: < 1 微秒（无锁快速路径）
- **内存占用**: < 500B per instance
- **并发性能**: 支持 > 5,000 TPS
- **AOP 开销**: < 5% 的性能损耗
- **线程安全**: 基于原子操作，无锁设计

---

## 总结

这个设计文档定义了一个真正轻量级的 Java 熔断器组件：

### 🎯 **核心优势**

1. **极简设计**: 只做最核心的熔断功能，避免过度设计
2. **零配置**: 引入依赖即可使用，无需任何配置
3. **高性能**: 无锁设计，微秒级响应时间
4. **易于使用**: 只需一个注解即可保护方法
5. **轻量级**: 最小依赖，jar 包 < 30KB

### 🚀 **设计亮点**

- **专注注解式**: 只支持注解方式，避免 API 复杂性
- **简化配置**: 只有 6 个核心配置参数
- **双重保护**: 熔断器 + 信号量隔离，完整的故障保护
- **状态管理**: 经典的三状态熔断器模式
- **Spring 原生**: 深度集成 Spring Boot 生态

### 💡 **对比优势**

相比复杂的熔断器框架，我们的设计：

- **更轻量**: 去除了不必要的功能和复杂性
- **更简单**: 学习成本低，上手即用
- **更专注**: 只做熔断，做到极致

通过简化设计，保留最有价值的功能，这个组件在轻量级和实用性之间取得了最佳平衡，既有完整的故障保护能力，又保持了极佳的易用性。

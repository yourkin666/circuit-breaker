# Java 轻量级熔断器组件

[![License](https://img.shields.io/badge/license-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/java-8%2B-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/spring--boot-2.7%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)

超轻量级、高性能的 Java 熔断器组件，专为 Spring Boot 应用设计。

## 核心特性

- **极致轻量**：jar 包 < 30KB，零外部依赖
- **高性能**：O(1) 算法复杂度，纳秒级响应
- **简单易用**：单注解即可实现熔断保护
- **线程安全**：基于原子操作，支持高并发

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.circuitbreaker</groupId>
    <artifactId>circuit-breaker-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 使用注解

```java
@Service
public class UserService {

    // 基础用法
    @CircuitBreakerProtected(name = "user-api")
    public User getUserById(Long userId) {
        return userApiClient.getUser(userId);
    }
    
    // 带降级策略
    @CircuitBreakerProtected(
        name = "user-service",
        fallbackMethod = "getUserFromCache",
        failureRateThreshold = 40,
        maxConcurrentCalls = 100
    )
    public User getUserFromRemote(Long userId) {
        return remoteUserService.getUser(userId);
    }

    // 降级方法
    public User getUserFromCache(Long userId, Exception ex) {
        return cacheService.getUser(userId);
    }
}
```

## 配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `name` | - | 熔断器名称（必填） |
| `fallbackMethod` | "" | 降级方法名 |
| `failureRateThreshold` | 50 | 失败率阈值(%) |
| `minimumNumberOfCalls` | 10 | 最小统计样本 |
| `slidingWindowSize` | 100 | 滑动窗口大小 |
| `waitDurationInOpenState` | 60000 | 熔断等待时间(ms) |
| `permittedNumberOfCallsInHalfOpenState` | 3 | 试探请求数 |
| `maxConcurrentCalls` | 0 | 最大并发数 |

## 项目结构

```
src/main/java/com/circuitbreaker/
├── annotation/          # 注解定义
│   └── CircuitBreakerProtected.java
├── aop/                # AOP切面处理
│   └── CircuitBreakerAspect.java
├── autoconfigure/      # Spring Boot自动配置
│   └── CircuitBreakerAutoConfiguration.java
├── core/              # 核心功能模块
│   ├── CircuitBreakerConfig.java      # 配置管理
│   ├── CircuitBreakerMetrics.java     # 指标统计
│   ├── CircuitBreakerRegistry.java    # 注册中心
│   ├── CircuitBreakerState.java       # 状态枚举
│   └── SlidingWindow.java             # 滑动窗口
└── exception/         # 异常定义
    ├── CircuitBreakerException.java
    ├── CircuitBreakerOpenException.java
    ├── CircuitBreakerConfigurationException.java
    └── TooManyConcurrentCallsException.java
```

## 工作原理

熔断器有三种状态：

- **CLOSED（关闭）**：正常状态，请求直接通过
- **OPEN（开启）**：熔断状态，直接调用降级方法
- **HALF_OPEN（半开）**：试探状态，少量请求测试服务恢复

## 许可证

本项目基于 [MIT 许可证](LICENSE) 开源。
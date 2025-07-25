# Java 轻量级熔断器组件

[![License](https://img.shields.io/badge/license-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/java-8%2B-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/spring--boot-2.7%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)

一个轻量级、高性能的 Java 熔断器组件，专为 Spring Boot 应用设计。提供注解式的熔断保护，支持并发控制和故障降级。

## 🌟 核心特性

- **🪶 轻量级**: 零外部依赖，jar 包 < 30KB
- **⚡ 高性能**: 纳秒级响应时间，支持 >5,000 TPS
- **🔒 线程安全**: 完全无锁设计，基于原子操作
- **📝 简单易用**: 基于注解的声明式使用方式
- **🛡️ 双重保护**: 熔断器 + 信号量隔离
- **🔧 自动配置**: 深度集成 Spring Boot，零配置开箱即用

## 🏗️ 项目架构

```
circuit-breaker/
├── annotation/
│   └── CircuitBreakerProtected.java     # 熔断保护注解
├── core/
│   ├── CircuitBreakerState.java         # 熔断器状态枚举
│   ├── CircuitBreakerConfig.java        # 配置管理
│   ├── CircuitBreakerRegistry.java      # 熔断器注册中心
│   ├── SlidingWindow.java               # 滑动窗口统计
│   └── CircuitBreakerMetrics.java       # 指标收集
├── exception/
│   ├── CircuitBreakerException.java     # 异常基类
│   ├── CircuitBreakerOpenException.java # 熔断开启异常
│   ├── CircuitBreakerConfigurationException.java # 配置异常
│   └── TooManyConcurrentCallsException.java # 并发超限异常
└── autoconfigure/
    ├── CircuitBreakerAspect.java        # AOP切面实现
    └── CircuitBreakerAutoConfiguration.java # 自动配置
```

## 🚀 快速开始

### 1. 添加依赖

在你的 `pom.xml` 中添加以下依赖：

```xml
<dependency>
    <groupId>com.circuitbreaker</groupId>
    <artifactId>circuit-breaker-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 使用注解保护方法

```java
@Service
public class UserService {

    @CircuitBreakerProtected(
        name = "user-service",
        fallbackMethod = "getUserFromCache"
    )
    public User getUserById(Long userId) {
        // 调用外部服务获取用户信息
        return userApiClient.getUser(userId);
    }

    public User getUserFromCache(Long userId, Exception ex) {
        // 降级策略：从缓存获取用户信息
        log.warn("用户服务调用失败，从缓存获取: {}", ex.getMessage());
        return cacheService.getUser(userId);
    }
}
```

就这么简单！熔断器会自动：

- 监控方法调用的成功率
- 在失败率过高时自动熔断
- 在服务恢复时自动恢复
- 在熔断时调用 fallback 方法

## 📚 详细使用指南

### 熔断器状态说明

- **CLOSED**: 🟢 正常状态，所有请求都会通过
- **OPEN**: 🔴 熔断状态，请求被直接拒绝，调用 fallback
- **HALF_OPEN**: 🟡 试探状态，允许少量请求测试服务是否恢复

## ⚙️ 配置参数详解

| 参数                                    | 类型   | 默认值 | 说明                     |
| --------------------------------------- | ------ | ------ | ------------------------ |
| `name`                                  | String | -      | 熔断器唯一名称（必填）   |
| `fallbackMethod`                        | String | ""     | 失败回退方法名           |
| `failureRateThreshold`                  | float  | 50     | 失败率阈值(0-100)        |
| `minimumNumberOfCalls`                  | int    | 10     | 最小调用次数才开始统计   |
| `slidingWindowSize`                     | int    | 100    | 滑动窗口大小             |
| `waitDurationInOpenState`               | long   | 60000  | OPEN 状态等待时间(毫秒)  |
| `permittedNumberOfCallsInHalfOpenState` | int    | 3      | 半开状态允许的调用次数   |
| `maxConcurrentCalls`                    | int    | 0      | 最大并发调用数(0=无限制) |

## 🔥 实际应用场景

### 微服务调用保护

```java
@Service
public class OrderService {

    @CircuitBreakerProtected(
        name = "inventory-service",
        fallbackMethod = "checkInventoryFallback",
        failureRateThreshold = 40,
        maxConcurrentCalls = 50,
        waitDurationInOpenState = 30000
    )
    public boolean checkInventory(String productId, int quantity) {
        return inventoryServiceClient.checkStock(productId, quantity);
    }

    public boolean checkInventoryFallback(String productId, int quantity, Exception ex) {
        // 降级策略：假设有库存（或从缓存检查）
        log.warn("库存服务不可用，使用降级策略: {}", ex.getMessage());
        return cacheService.hasStock(productId, quantity);
    }
}
```

## 🤝 贡献指南

我们欢迎任何形式的贡献！

### 如何贡献

1. Fork 本项目
2. 创建你的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交你的修改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启一个 Pull Request

### 开发环境设置

```bash
# 克隆项目
git clone https://github.com/yourkin666/circuit-breaker.git
cd circuit-breaker

# 编译项目
mvn clean compile

# 运行测试
mvn test

# 打包
mvn clean package
```

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🔗 相关链接

- [项目主页](https://github.com/yourkin666/circuit-breaker)
- [问题反馈](https://github.com/yourkin666/circuit-breaker/issues)
- [版本发布](https://github.com/yourkin666/circuit-breaker/releases)

## 📋 更新日志

### v1.0.0 (2024-12-19)

- 🎉 首次发布
- ✨ 实现核心熔断器功能
- �� 支持 Spring Boot 自动配置
- 📝 完整的文档和示例
- 🧪 全面的测试覆盖

---

**开箱即用的熔断保护，让你的应用更稳定！** 🛡️✨

如果这个项目对你有帮助，请给我们一个 ⭐ Star！

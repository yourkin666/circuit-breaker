package com.circuitbreaker.annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 熔断器保护注解
 * 
 * 在方法上使用此注解可为方法提供熔断保护，当方法失败率超过阈值时自动熔断
 */
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
     * 回退方法必须在同一个类中，且参数列表与原方法相同，但最后增加一个Exception参数
     */
    String fallbackMethod() default "";

    /**
     * 失败率阈值 (0.0 - 100.0)
     * 当失败率超过此阈值时，熔断器将从CLOSED状态转为OPEN状态
     */
    float failureRateThreshold() default 50;

    /**
     * 最小调用次数
     * 只有当调用次数达到此阈值时，才开始计算失败率
     */
    int minimumNumberOfCalls() default 10;

    /**
     * 滑动窗口大小
     * 用于统计成功/失败次数的滑动窗口大小
     */
    int slidingWindowSize() default 100;

    /**
     * OPEN状态等待时间(毫秒)
     * 熔断器在OPEN状态下等待多长时间后转为HALF_OPEN状态
     */
    long waitDurationInOpenState() default 60000;

    /**
     * 半开状态允许的调用次数
     * 在HALF_OPEN状态下，允许多少次调用来测试服务是否恢复
     */
    int permittedNumberOfCallsInHalfOpenState() default 3;

    /**
     * 最大并发调用数（信号量隔离）
     * 0 表示不限制并发
     */
    int maxConcurrentCalls() default 0;
}
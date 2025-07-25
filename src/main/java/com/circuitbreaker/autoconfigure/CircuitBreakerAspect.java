package com.circuitbreaker.autoconfigure;

import com.circuitbreaker.annotation.CircuitBreakerProtected;
import com.circuitbreaker.core.CircuitBreakerConfig;
import com.circuitbreaker.core.CircuitBreakerRegistry;
import com.circuitbreaker.exception.CircuitBreakerException;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 熔断器AOP切面
 * 
 * 拦截标注了@CircuitBreakerProtected注解的方法，
 * 为其提供熔断保护和fallback机制
 */
@Aspect
@Component
public class CircuitBreakerAspect {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerAspect.class);

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * 环绕通知：拦截所有标注了@CircuitBreakerProtected的方法
     */
    @Around("@annotation(circuitBreakerProtected)")
    public Object around(ProceedingJoinPoint joinPoint, CircuitBreakerProtected circuitBreakerProtected)
            throws Throwable {
        String circuitBreakerName = circuitBreakerProtected.name();

        log.debug("Executing method {} with circuit breaker {}",
                joinPoint.getSignature().toShortString(), circuitBreakerName);

        try {
            // 创建配置并执行受保护的调用
            CircuitBreakerConfig config = new CircuitBreakerConfig(circuitBreakerName, circuitBreakerProtected);

            return circuitBreakerRegistry.executeSupplier(
                    circuitBreakerName,
                    config,
                    () -> {
                        try {
                            return joinPoint.proceed();
                        } catch (Throwable throwable) {
                            // 简单的异常转换
                            throw throwable instanceof RuntimeException
                                    ? (RuntimeException) throwable
                                    : new RuntimeException(throwable);
                        }
                    });

        } catch (CircuitBreakerException ex) {
            log.warn("Circuit breaker {} triggered: {}", circuitBreakerName, ex.getMessage());
            return tryFallback(joinPoint, circuitBreakerProtected, ex);

        } catch (Exception ex) {
            log.error("Error executing method {} with circuit breaker {}",
                    joinPoint.getSignature().toShortString(), circuitBreakerName, ex);
            return tryFallback(joinPoint, circuitBreakerProtected, ex);
        }
    }

    /**
     * 尝试执行fallback方法，如果没有配置则抛出原异常
     */
    private Object tryFallback(ProceedingJoinPoint joinPoint,
            CircuitBreakerProtected annotation,
            Exception originalException) throws Throwable {
        if (annotation.fallbackMethod().isEmpty()) {
            throw originalException;
        }
        return executeFallback(joinPoint, annotation.fallbackMethod(), originalException);
    }

    /**
     * 执行fallback方法
     */
    private Object executeFallback(ProceedingJoinPoint joinPoint, String fallbackMethodName,
            Exception originalException) throws Throwable {
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        try {
            // 构造fallback方法的参数类型（原参数 + Exception）
            Class<?>[] parameterTypes = signature.getParameterTypes();
            Class<?>[] fallbackParameterTypes = new Class<?>[parameterTypes.length + 1];
            System.arraycopy(parameterTypes, 0, fallbackParameterTypes, 0, parameterTypes.length);
            fallbackParameterTypes[parameterTypes.length] = Exception.class;

            // 构造fallback方法的参数值（原参数 + 异常对象）
            Object[] fallbackArgs = new Object[args.length + 1];
            System.arraycopy(args, 0, fallbackArgs, 0, args.length);
            fallbackArgs[args.length] = originalException;

            // 查找并执行fallback方法
            Method fallbackMethod = target.getClass().getDeclaredMethod(fallbackMethodName, fallbackParameterTypes);
            fallbackMethod.setAccessible(true);

            log.debug("Executing fallback method {} for circuit breaker", fallbackMethodName);
            return fallbackMethod.invoke(target, fallbackArgs);

        } catch (NoSuchMethodException ex) {
            log.error("Fallback method {} not found. Expected signature: {}({}, Exception)",
                    fallbackMethodName, fallbackMethodName,
                    signature.getParameterTypes());
            throw new RuntimeException("Fallback method not found: " + fallbackMethodName, originalException);
        } catch (Exception ex) {
            log.error("Error executing fallback method {}", fallbackMethodName, ex);
            throw new RuntimeException("Fallback execution failed", originalException);
        }
    }
}
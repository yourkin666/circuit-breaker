package com.circuitbreaker.autoconfigure;

import com.circuitbreaker.core.CircuitBreakerRegistry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 熔断器自动配置类
 * 
 * 自动配置熔断器相关的Bean，包括：
 * - CircuitBreakerRegistry：熔断器注册中心
 * - CircuitBreakerAspect：AOP切面
 * 
 * 当类路径下存在相关依赖时自动激活
 */
@Configuration
@ConditionalOnClass({ CircuitBreakerRegistry.class })
@EnableAspectJAutoProxy
public class CircuitBreakerAutoConfiguration {

    /**
     * 配置熔断器注册中心
     */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return new CircuitBreakerRegistry();
    }

    /**
     * 配置AOP切面
     */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerAspect circuitBreakerAspect() {
        return new CircuitBreakerAspect();
    }
}
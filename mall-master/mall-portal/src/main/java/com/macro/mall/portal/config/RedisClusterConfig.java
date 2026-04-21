package com.macro.mall.portal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis集群配置
 * 将单点Redis改为集群模式，支持数据分片和高可用
 */
@Configuration
public class RedisClusterConfig {

    @Value("${redis.cluster.nodes:192.168.1.1:7001,192.168.1.1:7002,192.168.1.1:7003,192.168.1.1:7004,192.168.1.1:7005,192.168.1.1:7006}")
    private String clusterNodes;

    @Value("${redis.cluster.max-redirects:3}")
    private int maxRedirects;

    @Value("${redis.cluster.timeout:6000}")
    private long timeout;

    @Value("${redis.cluster.pool.max-active:20}")
    private int maxActive;

    @Value("${redis.cluster.pool.max-idle:10}")
    private int maxIdle;

    @Value("${redis.cluster.pool.min-idle:5}")
    private int minIdle;

    @Value("${redis.cluster.pool.max-wait:3000}")
    private long maxWait;

    /**
     * Redis集群配置
     */
    @Bean
    public RedisClusterConfiguration redisClusterConfiguration() {
        RedisClusterConfiguration config = new RedisClusterConfiguration();
        config.setMaxRedirects(maxRedirects);
        
        List<RedisNode> nodes = new ArrayList<>();
        String[] nodeArray = clusterNodes.split(",");
        for (String node : nodeArray) {
            String[] parts = node.trim().split(":");
            if (parts.length == 2) {
                nodes.add(new RedisNode(parts[0], Integer.parseInt(parts[1])));
            }
        }
        config.setClusterNodes(nodes);
        
        return config;
    }

    /**
     * 连接池配置
     */
    @Bean
    public GenericObjectPoolConfig<?> genericObjectPoolConfig() {
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(maxActive);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setMaxWait(Duration.ofMillis(maxWait));
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        return poolConfig;
    }

    /**
     * Lettuce客户端配置
     */
    @Bean
    public LettucePoolingClientConfiguration lettucePoolingClientConfiguration(
            GenericObjectPoolConfig<?> poolConfig) {
        return LettucePoolingClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(timeout))
                .poolConfig(poolConfig)
                .build();
    }

    /**
     * Redis连接工厂 - 集群模式
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            RedisClusterConfiguration redisClusterConfiguration,
            LettucePoolingClientConfiguration lettucePoolingClientConfiguration) {
        return new LettuceConnectionFactory(redisClusterConfiguration, lettucePoolingClientConfiguration);
    }

    /**
     * RedisTemplate配置 - 集群模式
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 使用String序列化key
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        
        // 使用JSON序列化value
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }

    /**
     * StringRedisTemplate - 集群模式
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
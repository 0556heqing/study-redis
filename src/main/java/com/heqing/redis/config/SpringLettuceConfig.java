package com.heqing.redis.config;

import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;

import static java.util.Collections.singletonMap;

@Configuration
@ComponentScan("com.heqing.redis.*")
@EnableCaching
public class SpringLettuceConfig extends CachingConfigurerSupport {

    @Override
    @Bean
    public KeyGenerator keyGenerator() {
        return new KeyGenerator() {
            @Override
            public Object generate(Object target, Method method, Object... params) {
                StringBuffer sb = new StringBuffer();
                sb.append(target.getClass().getName());
                sb.append(method.getName());
                for (Object obj : params) {
                    //????????????????????????, ?????????key??????????????????
                    sb.append(obj.toString());
                }
                return sb.toString();
            }
        };
    }

    /**
     * ???????????????
     */
    @Bean
    public CacheManager cacheManager(LettuceConnectionFactory lettuceConnectionFactory) {
        /* ??????????????? ?????????????????????30??????=30*60s=1800s */
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(1800L)).disableCachingNullValues();
        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(RedisCacheWriter.lockingRedisCacheWriter(lettuceConnectionFactory))
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(singletonMap("business_data", RedisCacheConfiguration.defaultCacheConfig() .entryTtl(Duration.ofSeconds(3600L)) .disableCachingNullValues()))
                .withInitialCacheConfigurations(singletonMap("system_data", RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(3600L)).disableCachingNullValues()))
                .withInitialCacheConfigurations(singletonMap("common_data", RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(3600L)).disableCachingNullValues()))
                .withInitialCacheConfigurations(singletonMap("test_data", RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(60L)).disableCachingNullValues()))
                .transactionAware();
        RedisCacheManager cacheManager = builder.build();
        return cacheManager;
    }

    @Profile("single")
    @Bean
    public LettuceConnectionFactory singleLettuceFactory(RedisProperty redisProperty) {
        // ??????redis??????
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(redisProperty.getHost(), redisProperty.getPort());
        if(!StringUtils.isEmpty(redisProperty.getPassword())) {
            configuration.setPassword(redisProperty.getPassword());
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.setValidateConnection(true);
        return factory;
    }

    @Profile("sentinel")
    @Bean
    public LettuceConnectionFactory sentinelLettuceFactory(RedisProperty redisProperty) {
        // ????????????redis??????
        RedisSentinelConfiguration configuration = new RedisSentinelConfiguration(redisProperty.getMaster(), redisProperty.getSentinelNodes());
        configuration.setPassword(redisProperty.getPassword());
        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.setValidateConnection(true);
        return factory;
    }

    @Profile("cluster")
    @Bean
    public LettuceConnectionFactory clusterLettuceFactory(RedisProperty redisProperty) {
        // ????????????redis??????
        ClusterTopologyRefreshOptions clusterTopologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(Duration.ofSeconds(30))
                .enableAllAdaptiveRefreshTriggers()
                .build();
        ClusterClientOptions clusterClientOptions = ClusterClientOptions.builder()
                .validateClusterNodeMembership(false)
                .topologyRefreshOptions(clusterTopologyRefreshOptions)
                .build();
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .shutdownTimeout(Duration.ZERO)
                .clientOptions(clusterClientOptions)
                .build();

        RedisClusterConfiguration serverConfig = new RedisClusterConfiguration(redisProperty.getClusterNodes());

        serverConfig.setPassword(redisProperty.getPassword());
        // ????????????RedisStandaloneConfiguration?????????
        //??????Redis Standolone????????????????????????????????????
        //??????hostname???port
        LettuceConnectionFactory factory = new LettuceConnectionFactory(serverConfig, clientConfig);
        factory.setValidateConnection(true);
        return factory;
    }

    @Profile("lettuce")
    @Bean
    public RedisTemplate<String, Object> redisLettuceTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
        // ???????????????
        // ??????redisTemplate
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(lettuceConnectionFactory);
        RedisSerializer<?> stringSerializer = new StringRedisSerializer();
        // key?????????
        redisTemplate.setKeySerializer(stringSerializer);
        // value?????????
        redisTemplate.setValueSerializer(stringSerializer);
        // Hash key?????????
        redisTemplate.setHashKeySerializer(stringSerializer);
        // Hash value?????????
        redisTemplate.setHashValueSerializer(stringSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    @Profile("lettuce")
    @Bean
    public RedisMessageListenerContainer container(LettuceConnectionFactory connectionFactory, MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new PatternTopic("test"));
        return container;
    }
}

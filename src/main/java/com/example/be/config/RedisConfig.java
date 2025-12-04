package com.example.be.config;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.URI;

@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RedisConfig {

    @Value("${REDIS_URL}")
    String redisUrl;

    @Bean
    public JedisConnectionFactory jedisConnectionFactory() {
        URI uri = URI.create(redisUrl);

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(uri.getHost());
        config.setPort(uri.getPort());
        config.setPassword(uri.getUserInfo().split(":", 2)[1]);

        JedisClientConfiguration clientConfig = JedisClientConfiguration.builder()
                .useSsl()
                .build();

        return new JedisConnectionFactory(config, clientConfig);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate() {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }
}

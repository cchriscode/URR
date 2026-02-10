package com.tiketi.ticketservice.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class RedisConfig {

    @Bean
    public DefaultRedisScript<List> admissionScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/admission_control.lua")));
        script.setResultType(List.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<List> staleCleanupScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/stale_cleanup.lua")));
        script.setResultType(List.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<List> seatLockAcquireScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/seat_lock_acquire.lua")));
        script.setResultType(List.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> seatLockReleaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/seat_lock_release.lua")));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> paymentVerifyScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/payment_verify.lua")));
        script.setResultType(Long.class);
        return script;
    }
}

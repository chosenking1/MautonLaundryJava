package com.work.mautonlaundry.services;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ActiveAgentMetrics {
    private static final String AGENT_ONLINE_PATTERN = "agent:online:*";

    public ActiveAgentMetrics(MeterRegistry registry, StringRedisTemplate redisTemplate) {
        registry.gauge("active_agents", redisTemplate, template -> {
            Set<String> keys = template.keys(AGENT_ONLINE_PATTERN);
            return keys == null ? 0 : keys.size();
        });
    }
}

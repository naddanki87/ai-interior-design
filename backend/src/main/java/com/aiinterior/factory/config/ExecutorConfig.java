package com.aiinterior.factory.config;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {

    /** Every agent message is handled on its own virtual thread. */
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService agentExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("agent-", 0).factory());
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

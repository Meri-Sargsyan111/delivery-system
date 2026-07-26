package com.example.courierservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Backs CourierAssignmentServiceImpl's one-shot offer-timeout checks (see
 * scheduleOfferTimeout). Being the only TaskScheduler bean in this context, Spring also
 * uses it to run every existing @Scheduled method (e.g. LocationSimulatorService's periodic
 * ticks) - sized generously enough for both, since these are all lightweight, short-lived
 * tasks rather than long-blocking work.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(6);
        scheduler.setThreadNamePrefix("courier-scheduler-");
        scheduler.initialize();
        return scheduler;
    }
}
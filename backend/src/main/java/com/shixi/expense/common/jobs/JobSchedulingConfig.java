package com.shixi.expense.common.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobSchedulingConfig { }

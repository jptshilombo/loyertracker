package com.loyertracker.batch;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active la planification {@code @Scheduled} (US-30 batch échéances ; alertes S04 ultérieures). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

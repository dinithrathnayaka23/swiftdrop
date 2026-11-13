package com.swiftdrop.merchant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables scheduled merchant jobs such as webhook retries. */
@Configuration
@EnableScheduling
public class MerchantSchedulingConfig {
}

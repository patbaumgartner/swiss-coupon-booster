package com.patbaumgartner.couponbooster.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling only for the long-running server profile.
 */
@Configuration
@Profile("server")
@EnableScheduling
public class ServerSchedulingConfig {

}

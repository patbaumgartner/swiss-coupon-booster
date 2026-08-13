package com.patbaumgartner.couponbooster.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Central registration of GraalVM native-image hints for the application.
 * <p>
 * Imports {@link JacksonBindingRuntimeHints} (DTOs bound by Jackson via the REST client
 * and the manual trigger controller) and {@link ConfigurationPropertiesRuntimeHints}
 * (validated configuration property records).
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints({ JacksonBindingRuntimeHints.class, ConfigurationPropertiesRuntimeHints.class,
		HibernateValidatorRuntimeHints.class })
public class NativeHintsConfiguration {

}

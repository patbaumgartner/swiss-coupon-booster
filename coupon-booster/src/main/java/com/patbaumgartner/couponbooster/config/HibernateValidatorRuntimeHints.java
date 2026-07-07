package com.patbaumgartner.couponbooster.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * Registers GraalVM native-image reflection hints for Hibernate Validator's JBoss Logging
 * implementation classes.
 * <p>
 * Validated {@code @ConfigurationProperties} (e.g. the coop/migros user properties)
 * initialise Hibernate Validator, whose logger is obtained reflectively by JBoss Logging
 * via {@code Class.forName("<interface>_$logger")}. These generated classes are not
 * covered by the reachability metadata, so without these hints the native image fails
 * with {@code ExceptionInInitializerError} / "Invalid logger interface
 * org.hibernate.validator.internal.util.logging.Log (implementation not found)".
 */
public class HibernateValidatorRuntimeHints implements RuntimeHintsRegistrar {

	private static final String[] JBOSS_LOGGING_TYPES = { "org.hibernate.validator.internal.util.logging.Log_$logger",
			"org.hibernate.validator.internal.util.logging.Messages_$bundle" };

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		for (String type : JBOSS_LOGGING_TYPES) {
			hints.reflection().registerType(TypeReference.of(type), MemberCategory.values());
		}
	}

}

package com.patbaumgartner.couponbooster.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

/**
 * Registers GraalVM native-image reflection hints for Hibernate Validator internals.
 * <p>
 * Validated {@code @ConfigurationProperties} (e.g. the coop/migros properties, which use
 * {@code @Pattern}, {@code @NotBlank}, ...) drive Hibernate Validator, which reflects on
 * two kinds of generated/internal classes not covered by reachability metadata:
 * <ul>
 * <li><b>JBoss Logging</b> loggers ({@code Log_$logger}, {@code Messages_$bundle}) loaded
 * via {@code Class.forName("<interface>_$logger")} — otherwise "Invalid logger interface
 * ... implementation not found".</li>
 * <li><b>Constraint validators</b> under
 * {@code org.hibernate.validator.internal.constraintvalidators} which are instantiated
 * reflectively via their no-arg constructor — otherwise "NoSuchMethodException:
 * ...PatternValidator.&lt;init&gt;()".</li>
 * </ul>
 */
public class HibernateValidatorRuntimeHints implements RuntimeHintsRegistrar {

	private static final Logger log = LoggerFactory.getLogger(HibernateValidatorRuntimeHints.class);

	private static final String[] JBOSS_LOGGING_TYPES = { "org.hibernate.validator.internal.util.logging.Log_$logger",
			"org.hibernate.validator.internal.util.logging.Messages_$bundle" };

	private static final String CONSTRAINT_VALIDATORS_PACKAGE = "org.hibernate.validator.internal.constraintvalidators";

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		for (String type : JBOSS_LOGGING_TYPES) {
			hints.reflection().registerType(TypeReference.of(type), MemberCategory.values());
		}
		registerConstraintValidators(hints, classLoader);
	}

	private void registerConstraintValidators(RuntimeHints hints, ClassLoader classLoader) {
		var scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter((TypeFilter) (metadataReader, metadataReaderFactory) -> true);

		int count = 0;
		for (var candidate : scanner.findCandidateComponents(CONSTRAINT_VALIDATORS_PACKAGE)) {
			String className = candidate.getBeanClassName();
			if (className == null) {
				continue;
			}
			try {
				Class<?> type = ClassUtils.forName(className, classLoader);
				hints.reflection().registerType(type, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
				count++;
			}
			catch (Throwable ex) {
				// Skip classes that cannot be loaded; they are not needed at runtime.
				log.trace("Skipping constraint validator for native hints: {} ({})", className, ex.getMessage());
			}
		}
		log.debug("Registered native reflection hints for {} Hibernate Validator constraint validators", count);
	}

}

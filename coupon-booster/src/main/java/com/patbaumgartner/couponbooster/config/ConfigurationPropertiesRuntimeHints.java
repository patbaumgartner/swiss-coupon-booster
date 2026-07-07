package com.patbaumgartner.couponbooster.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

/**
 * Registers full reflection for the application's {@code @ConfigurationProperties}
 * records and their nested types.
 * <p>
 * These records are validated with Jakarta Bean Validation ({@code @Valid}, {@code @URL},
 * {@code @NotBlank}). At runtime Hibernate Validator reads the record fields
 * reflectively, which requires field-level reflection hints that Spring Boot's binding
 * hints do not cover for nested validated records. Registering every type in the
 * {@code *.properties} packages with all member categories keeps this robust as new
 * properties are added.
 */
public class ConfigurationPropertiesRuntimeHints implements RuntimeHintsRegistrar {

	private static final Logger log = LoggerFactory.getLogger(ConfigurationPropertiesRuntimeHints.class);

	private static final List<String> PROPERTIES_PACKAGES = List.of("com.patbaumgartner.couponbooster.coop.properties",
			"com.patbaumgartner.couponbooster.migros.properties");

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		var scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter((TypeFilter) (metadataReader, metadataReaderFactory) -> true);

		int count = 0;
		for (String basePackage : PROPERTIES_PACKAGES) {
			for (var candidate : scanner.findCandidateComponents(basePackage)) {
				String className = candidate.getBeanClassName();
				if (className == null) {
					continue;
				}
				try {
					Class<?> type = ClassUtils.forName(className, classLoader);
					hints.reflection().registerType(type, MemberCategory.values());
					count++;
				}
				catch (ClassNotFoundException ex) {
					log.warn("Skipping configuration properties type for native hints: {}", className);
				}
			}
		}
		log.debug("Registered native reflection hints for {} configuration properties types", count);
	}

}

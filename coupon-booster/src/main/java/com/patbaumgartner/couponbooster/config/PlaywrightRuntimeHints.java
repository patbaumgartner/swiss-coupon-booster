package com.patbaumgartner.couponbooster.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

/**
 * Registers GraalVM native-image hints required by Microsoft Playwright for Java.
 * <p>
 * Playwright is not part of the GraalVM reachability metadata repository, so the
 * following must be registered explicitly for a native image to work:
 * <ul>
 * <li><b>Reflection & serialization</b> for every {@code com.microsoft.playwright} type.
 * Playwright serialises its wire-protocol messages and API option objects with Gson,
 * which relies on reflective field access and unsafe allocation at runtime.</li>
 * <li><b>Driver resources</b> ({@code driver/package/**} – the bundled Node.js CLI – and
 * {@code driver/linux/**} – the Linux Node binary). {@code DriverJar} extracts these from
 * the classpath into a temporary directory at runtime; without them Playwright cannot
 * spawn its driver process.</li>
 * </ul>
 * Only the Linux driver binaries are registered because the container image targets
 * Linux; the macOS/Windows/ARM binaries in the driver bundle are intentionally excluded
 * to keep the native image small.
 */
public class PlaywrightRuntimeHints implements RuntimeHintsRegistrar {

	private static final Logger log = LoggerFactory.getLogger(PlaywrightRuntimeHints.class);

	private static final String PLAYWRIGHT_BASE_PACKAGE = "com.microsoft.playwright";

	private static final String[] DRIVER_RESOURCE_LOCATIONS = { "classpath*:driver/package/**",
			"classpath*:driver/linux/**" };

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		registerPlaywrightReflection(hints, classLoader);
		registerDriverResources(hints, classLoader);
	}

	private void registerPlaywrightReflection(RuntimeHints hints, ClassLoader classLoader) {
		var scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter((TypeFilter) (metadataReader, metadataReaderFactory) -> true);

		int count = 0;
		for (var candidate : scanner.findCandidateComponents(PLAYWRIGHT_BASE_PACKAGE)) {
			String className = candidate.getBeanClassName();
			if (className == null) {
				continue;
			}
			try {
				Class<?> type = ClassUtils.forName(className, classLoader);
				hints.reflection().registerType(type, MemberCategory.values());
				count++;
			}
			catch (Throwable ex) {
				// Skip classes that cannot be loaded (e.g. optional/platform-specific
				// types); they are not needed for the Linux runtime.
				log.trace("Skipping Playwright type for native hints: {} ({})", className, ex.getMessage());
			}
		}
		log.debug("Registered native reflection hints for {} Playwright types", count);
	}

	private void registerDriverResources(RuntimeHints hints, ClassLoader classLoader) {
		var resolver = new PathMatchingResourcePatternResolver(classLoader);
		int count = 0;
		for (String location : DRIVER_RESOURCE_LOCATIONS) {
			try {
				for (Resource resource : resolver.getResources(location)) {
					String path = classpathRelativePath(resource);
					if (path != null && !path.endsWith("/")) {
						// An exact path (no wildcards) unambiguously includes that single
						// resource in the native image regardless of Spring version.
						hints.resources().registerPattern(path);
						count++;
					}
				}
			}
			catch (IOException ex) {
				log.warn("Failed to resolve Playwright driver resources for '{}': {}", location, ex.getMessage());
			}
		}
		log.debug("Registered {} Playwright driver resources for native image", count);
	}

	/**
	 * Extracts the classpath-relative path (e.g. {@code driver/package/cli.js}) from a
	 * resource that lives inside a dependency jar.
	 * @param resource the resolved classpath resource
	 * @return the entry path within the jar, or {@code null} if it cannot be determined
	 */
	private String classpathRelativePath(Resource resource) {
		try {
			String url = resource.getURL().toString();
			int jarSeparator = url.lastIndexOf("!/");
			if (jarSeparator >= 0) {
				return url.substring(jarSeparator + 2);
			}
			int driverIndex = url.indexOf("driver/");
			return (driverIndex >= 0) ? url.substring(driverIndex) : null;
		}
		catch (IOException ex) {
			log.trace("Could not resolve URL for driver resource: {}", ex.getMessage());
			return null;
		}
	}

}

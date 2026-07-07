package com.patbaumgartner.couponbooster.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.util.ClassUtils;

/**
 * Registers reflection hints for the DTOs that Jackson binds at runtime.
 * <p>
 * Spring Boot's AOT engine does not automatically discover types that are only referenced
 * as {@code RestClient} request/response bodies or as REST controller return values, so
 * they must be registered explicitly for a native image. The types are referenced by name
 * because several are {@code private} nested records.
 */
public class JacksonBindingRuntimeHints implements RuntimeHintsRegistrar {

	private static final Logger log = LoggerFactory.getLogger(JacksonBindingRuntimeHints.class);

	private static final List<String> BINDING_TYPE_NAMES = List.of(
			// Manual REST trigger response (server profile)
			"com.patbaumgartner.couponbooster.scheduler.ActivationOutcome",
			// Migros Cumulus API response bodies (deserialised)
			"com.patbaumgartner.couponbooster.migros.service.CumulusCouponService$CouponsResponse",
			"com.patbaumgartner.couponbooster.migros.service.CumulusCouponService$RawCoupon",
			// Coop Supercard API request bodies (serialised)
			"com.patbaumgartner.couponbooster.coop.service.SupercardCouponService$DigitalCouponCollection",
			"com.patbaumgartner.couponbooster.coop.service.SupercardCouponService$DigitalCoupon");

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		var binding = new BindingReflectionHintsRegistrar();
		for (String typeName : BINDING_TYPE_NAMES) {
			try {
				Class<?> type = ClassUtils.forName(typeName, classLoader);
				binding.registerReflectionHints(hints.reflection(), type);
			}
			catch (ClassNotFoundException ex) {
				log.warn("Skipping Jackson binding hint; type not found: {}", typeName);
			}
		}
	}

}

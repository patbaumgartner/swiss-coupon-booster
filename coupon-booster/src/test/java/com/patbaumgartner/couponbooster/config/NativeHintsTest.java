package com.patbaumgartner.couponbooster.config;

import com.patbaumgartner.couponbooster.coop.properties.CoopUserProperties;
import com.patbaumgartner.couponbooster.coop.properties.SupercardProperties;
import com.patbaumgartner.couponbooster.migros.properties.CumulusProperties;
import com.patbaumgartner.couponbooster.migros.properties.MigrosUserProperties;
import com.patbaumgartner.couponbooster.scheduler.ActivationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The native image is the production artefact, so a missing hint is a production outage
 * that a JVM test run would never notice.
 */
class NativeHintsTest {

	private RuntimeHints hints;

	@BeforeEach
	void setUp() {
		hints = new RuntimeHints();
		ClassLoader classLoader = getClass().getClassLoader();
		new JacksonBindingRuntimeHints().registerHints(hints, classLoader);
		new ConfigurationPropertiesRuntimeHints().registerHints(hints, classLoader);
		new HibernateValidatorRuntimeHints().registerHints(hints, classLoader);
	}

	@Test
	void restResponseTypesAreReflectivelyAvailable() {
		assertThat(RuntimeHintsPredicates.reflection().onType(ActivationOutcome.class)).accepts(hints);
	}

	@Test
	void jacksonBoundApiPayloadsAreRegistered() throws ClassNotFoundException {
		for (String type : new String[] {
				"com.patbaumgartner.couponbooster.migros.service.CumulusCouponService$CouponsResponse",
				"com.patbaumgartner.couponbooster.migros.service.CumulusCouponService$RawCoupon",
				"com.patbaumgartner.couponbooster.coop.service.SupercardCouponService$DigitalCouponCollection" }) {
			assertThat(RuntimeHintsPredicates.reflection().onType(Class.forName(type)))
				.as("Jackson binding hint for %s", type)
				.accepts(hints);
		}
	}

	@Test
	void everyValidatedConfigurationPropertiesRecordIsRegistered() {
		for (Class<?> type : new Class<?>[] { CoopUserProperties.class, MigrosUserProperties.class,
				SupercardProperties.class, CumulusProperties.class }) {
			assertThat(RuntimeHintsPredicates.reflection().onType(type))
				.as("configuration properties hint for %s", type.getSimpleName())
				.accepts(hints);
		}
	}

	@Test
	void hibernateValidatorInternalsAreRegistered() {
		assertThat(hints.reflection().typeHints()).anyMatch(hint -> hint.getType().getName().endsWith("Log_$logger"))
			.anyMatch(hint -> hint.getType().getName().contains("constraintvalidators"));
	}

}

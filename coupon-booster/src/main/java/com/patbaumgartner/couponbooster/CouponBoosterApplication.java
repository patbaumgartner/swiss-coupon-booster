package com.patbaumgartner.couponbooster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Swiss Coupon Booster application entry point.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CouponBoosterApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(CouponBoosterApplication.class, args);

		// One-shot mode: the startup runs are already finished when run() returns, so
		// close the context and report their outcome as the process exit code. In the
		// server profile the application must keep running instead.
		if (!context.getEnvironment().matchesProfiles("server")) {
			System.exit(SpringApplication.exit(context));
		}
	}

}

package com.patbaumgartner.swiss.coupon.booster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.patbaumgartner.swiss.coupon.booster.settings.CoopSupercardSettings;
import com.patbaumgartner.swiss.coupon.booster.settings.MigrosAccountSettings;
import com.patbaumgartner.swiss.coupon.booster.settings.MigrosCumulusSettings;
import com.patbaumgartner.swiss.coupon.booster.settings.SwissCouponBoosterSettings;

@SpringBootApplication
@EnableConfigurationProperties({ SwissCouponBoosterSettings.class, CoopSupercardSettings.class,
		MigrosAccountSettings.class, MigrosCumulusSettings.class })
public class SwissCouponBoosterApplication {

	public static void main(String[] args) {
		SpringApplication.run(SwissCouponBoosterApplication.class, args);
	}

}

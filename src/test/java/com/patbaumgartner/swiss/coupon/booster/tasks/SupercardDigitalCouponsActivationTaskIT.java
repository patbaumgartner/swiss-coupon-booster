package com.patbaumgartner.swiss.coupon.booster.tasks;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patbaumgartner.swiss.coupon.booster.settings.CoopSupercardSettings;
import com.patbaumgartner.swiss.coupon.booster.settings.SwissCouponBoosterSettings;

@SpringBootTest
class SupercardDigitalCouponsActivationTaskIT {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RestClient.Builder restClientBuilder;

	@Autowired
	private SwissCouponBoosterSettings swissCouponBoosterSettings;

	@Autowired
	private CoopSupercardSettings coopSupercardSettings;

	@Test
	void executeTaskWithInjectedConfiguration() {
		SupercardDigitalCouponsActivationTask task = new SupercardDigitalCouponsActivationTask(objectMapper,
				restClientBuilder, swissCouponBoosterSettings, coopSupercardSettings);
		task.execute();
	}

}

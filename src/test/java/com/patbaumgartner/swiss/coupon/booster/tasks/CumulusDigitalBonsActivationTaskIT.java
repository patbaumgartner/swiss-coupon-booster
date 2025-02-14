package com.patbaumgartner.swiss.coupon.booster.tasks;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patbaumgartner.swiss.coupon.booster.settings.MigrosAccountSettings;
import com.patbaumgartner.swiss.coupon.booster.settings.MigrosCumulusSettings;

@SpringBootTest
class CumulusDigitalBonsActivationTaskIT {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RestClient.Builder restClientBuilder;

	@Autowired
	private MigrosAccountSettings migrosAccountSettings;

	@Autowired
	private MigrosCumulusSettings migrosCumulusSettings;

	@Test
	void executeTaskWithInjectedConfiguration() {
		CumulusDigitalBonsActivationTask task = new CumulusDigitalBonsActivationTask(objectMapper, restClientBuilder,
				migrosAccountSettings, migrosCumulusSettings);
		task.execute();
	}

}

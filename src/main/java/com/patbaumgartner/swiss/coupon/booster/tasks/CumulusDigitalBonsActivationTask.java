package com.patbaumgartner.swiss.coupon.booster.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patbaumgartner.swiss.coupon.booster.apis.MigrosDigitalCouponsApi;
import com.patbaumgartner.swiss.coupon.booster.apis.MigrosDigitalCouponsApi.CumulusDigitalCoupon;
import com.patbaumgartner.swiss.coupon.booster.settings.MigrosAccountSettings;
import com.patbaumgartner.swiss.coupon.booster.settings.MigrosCumulusSettings;

@Slf4j
@Component
@RequiredArgsConstructor
public class CumulusDigitalBonsActivationTask implements ActivationTask {

	private final ObjectMapper objectMapper;

	private final RestClient.Builder restClientBuilder;

	private final MigrosAccountSettings migrosAccountSettings;

	private final MigrosCumulusSettings migrosCumulusSettings;

	@Override
	public void execute() {

		if (!migrosCumulusSettings.enabled()) {
			log.info("Cumulus Digital Bons Activation Task is disabled.");
			return;
		}

		MigrosDigitalCouponsApi digitalCouponsApi = new MigrosDigitalCouponsApi(restClientBuilder, objectMapper,
				migrosAccountSettings, migrosCumulusSettings);
		digitalCouponsApi.loginMigrosAccount();
		digitalCouponsApi.loginCumulus();

		List<CumulusDigitalCoupon> allDigitalCoupons = digitalCouponsApi.collectCumulusDigitalCoupons();

		digitalCouponsApi.activateCumulusDigitalCoupons(allDigitalCoupons);

		log.info("Cumulus Digital Bons Activation Task activated.");
	}

}

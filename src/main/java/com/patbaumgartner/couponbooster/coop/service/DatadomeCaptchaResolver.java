package com.patbaumgartner.couponbooster.coop.service;

import com.patbaumgartner.couponbooster.coop.properties.TwoCaptchaProperties;
import com.twocaptcha.TwoCaptcha;
import com.twocaptcha.captcha.DataDome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DatadomeCaptchaResolver {

	private static final Logger logger = LoggerFactory.getLogger(DatadomeCaptchaResolver.class);

	private final TwoCaptchaProperties twoCaptchaProperties;

	private final TwoCaptcha solver;

	public DatadomeCaptchaResolver(TwoCaptchaProperties twoCaptchaProperties) {
		this.twoCaptchaProperties = twoCaptchaProperties;
		solver = new TwoCaptcha(twoCaptchaProperties.apiKey());
	}

	public String resolveCaptchaAndExtractCookie(String captchaUrl, String userAgent) {
		DataDome captcha = new DataDome();
		captcha.setCaptchaUrl(captchaUrl);
		captcha.setUrl(twoCaptchaProperties.websiteUrl());
		captcha.setUserAgent(userAgent);
		captcha.setProxy("HTTP", "212.51.157.178:8080");

		try {
			solver.solve(captcha);
			logger.info("Captcha solved: {}", captcha.getCode());
		}
		catch (Exception e) {
			logger.error("Error occurred while solving captcha: {}", e.getMessage(), e);
		}

		return captcha.getCode();
	}

}

package com.patbaumgartner.couponbooster.coop.service;

import com.patbaumgartner.couponbooster.coop.properties.TwoCaptchaProperties;
import com.patbaumgartner.couponbooster.util.cookie.CookieParser;
import com.patbaumgartner.couponbooster.util.cookie.ParsedCookie;
import com.patbaumgartner.couponbooster.util.proxy.ProxyResolver;
import com.twocaptcha.TwoCaptcha;
import com.twocaptcha.captcha.DataDome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DatadomeCaptchaResolver {

	private static final Logger logger = LoggerFactory.getLogger(DatadomeCaptchaResolver.class);

	private final ProxyResolver proxyResolver;

	private final TwoCaptcha solver;

	public DatadomeCaptchaResolver(TwoCaptchaProperties twoCaptchaProperties, ProxyResolver proxyResolver) {
		this.proxyResolver = proxyResolver;
		solver = new TwoCaptcha(twoCaptchaProperties.apiKey());
	}

	public ParsedCookie resolveCaptcha(String captchaUrl, String pageUrl, String userAgent) {
		DataDome captcha = new DataDome();
		captcha.setCaptchaUrl(captchaUrl);
		captcha.setUrl(pageUrl);
		captcha.setUserAgent(userAgent);
		captcha.setProxy("HTTP, ", proxyResolver.getCurrentProxy().toString());

		try {
			solver.solve(captcha);
			logger.info("Captcha solved: {}", captcha.getCode());
		}
		catch (Exception e) {
			logger.error("Error occurred while solving captcha: {}", e.getMessage(), e);
		}

		return CookieParser.parse(captcha.getCode());
	}

}

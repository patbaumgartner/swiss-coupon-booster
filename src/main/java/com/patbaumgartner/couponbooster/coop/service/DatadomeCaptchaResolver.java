package com.patbaumgartner.couponbooster.coop.service;

import com.patbaumgartner.couponbooster.coop.properties.TwoCaptchaProperties;
import com.patbaumgartner.couponbooster.exception.CouponBoosterException;
import com.patbaumgartner.couponbooster.util.cookie.CookieParser;
import com.patbaumgartner.couponbooster.util.cookie.ParsedCookie;
import com.patbaumgartner.couponbooster.util.proxy.ProxyResolver;
import com.twocaptcha.TwoCaptcha;
import com.twocaptcha.captcha.DataDome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for resolving Datadome CAPTCHA challenges using the 2Captcha service.
 * <p>
 * This service constructs a {@link DataDome} CAPTCHA object with the necessary
 * parameters, sends it to the 2Captcha API for solving, and parses the resulting cookie
 * from the response. It also handles proxy configuration for the solver.
 *
 * @see TwoCaptcha
 * @see DataDome
 * @see CookieParser
 */
@Service
public class DatadomeCaptchaResolver {

	private static final Logger logger = LoggerFactory.getLogger(DatadomeCaptchaResolver.class);

	private final ProxyResolver proxyResolver;

	private final TwoCaptcha solver;

	/**
	 * Constructs a new {@code DatadomeCaptchaResolver} with the specified properties and
	 * resolver.
	 * @param twoCaptchaProperties the properties for configuring the 2Captcha API client
	 * @param proxyResolver the resolver for obtaining the proxy to use for solving the
	 * CAPTCHA
	 */
	public DatadomeCaptchaResolver(TwoCaptchaProperties twoCaptchaProperties, ProxyResolver proxyResolver) {
		this.proxyResolver = proxyResolver;
		solver = new TwoCaptcha(twoCaptchaProperties.apiKey());
	}

	/**
	 * Resolves a Datadome CAPTCHA challenge.
	 * @param captchaUrl the URL of the CAPTCHA challenge
	 * @param pageUrl the URL of the page where the CAPTCHA is present
	 * @param userAgent the user agent of the browser
	 * @return a {@link ParsedCookie} containing the Datadome cookie
	 */
	public ParsedCookie resolveCaptcha(String captchaUrl, String pageUrl, String userAgent) {
		DataDome captcha = new DataDome();
		captcha.setCaptchaUrl(captchaUrl);
		captcha.setUrl(pageUrl);
		captcha.setUserAgent(userAgent);
		captcha.setProxy("http", "http://" + proxyResolver.getCurrentProxy().toString());

		try {
			solver.solve(captcha);
			logger.info("Captcha solved: {}", captcha.getCode());
		}
		catch (Exception e) {
			logger.error("Error occurred while solving captcha: {}", e.getMessage(), e);
			throw new CouponBoosterException("Failed to solve Datadome captcha", e);
		}

		return CookieParser.parse(captcha.getCode());
	}

}

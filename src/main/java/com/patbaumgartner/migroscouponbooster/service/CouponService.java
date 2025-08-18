package com.patbaumgartner.migroscouponbooster.service;

import com.microsoft.playwright.options.Cookie;
import com.patbaumgartner.migroscouponbooster.model.CouponActivationResult;

import java.util.List;

public interface CouponService {

	CouponActivationResult activateAllAvailableCoupons(List<Cookie> sessionCookies);

}

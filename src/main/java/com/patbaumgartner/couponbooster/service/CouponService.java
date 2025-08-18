package com.patbaumgartner.couponbooster.service;

import com.microsoft.playwright.options.Cookie;
import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;

import java.util.List;

public interface CouponService {

	CouponActivationResult activateAllAvailableCoupons(List<Cookie> sessionCookies);

}

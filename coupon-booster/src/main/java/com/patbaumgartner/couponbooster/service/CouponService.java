package com.patbaumgartner.couponbooster.service;

import com.patbaumgartner.couponbooster.migros.model.CouponActivationResult;
import com.patbaumgartner.couponbooster.model.SessionCookie;

import java.util.List;

public interface CouponService {

	CouponActivationResult activateAllAvailableCoupons(List<SessionCookie> sessionCookies, String userAgent,
			String language);

}

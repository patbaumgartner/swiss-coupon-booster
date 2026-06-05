package com.patbaumgartner.couponbooster.exception;

/**
 * Domain-specific runtime exception for the Coupon Booster application. Thrown when
 * business logic errors occur during coupon activation processes.
 */
public class CouponBoosterException extends RuntimeException {

	/**
	 * Creates a new CouponBoosterException with a descriptive error message.
	 * @param message detailed error message explaining what went wrong
	 */
	public CouponBoosterException(String message) {
		super(message);
	}

	/**
	 * Creates a new CouponBoosterException with a descriptive error message and root
	 * cause.
	 * @param message detailed error message explaining what went wrong
	 * @param cause the underlying exception that caused this error
	 */
	public CouponBoosterException(String message, Throwable cause) {
		super(message, cause);
	}

}

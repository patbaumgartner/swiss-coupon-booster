package com.patbaumgartner.couponbooster.util.cookie;

import java.text.Normalizer;
import java.util.Locale;

/** SameSite attribute values. */
public enum SameSite {

	Lax, Strict, None, Unknown;

	static SameSite from(String raw) {
		if (raw == null) {
			return Unknown;
		}
		String v = Normalizer.normalize(raw, Normalizer.Form.NFC).replace("\"", "").trim().toLowerCase(Locale.ROOT);
		return switch (v) {
			case "lax" -> Lax;
			case "strict" -> Strict;
			case "none" -> None;
			default -> Unknown;
		};
	}

}

package com.patbaumgartner.couponbooster.coop.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Configuration properties for Coop SuperCard API integration including endpoint URLs and
 * browser simulation settings.
 */
@ConfigurationProperties(prefix = "supercard")
@Validated
public record SupercardProperties(@Valid @NotNull Urls urls, @Valid @NotNull CouponFilter couponFilter) {

	/**
	 * API endpoint URLs for SuperCard coupon management operations.
	 *
	 * @param baseUrl base URL for the SuperCard API
	 * @param configUrl URL for retrieving PWA configuration and JWT token
	 * @param configUrlReferer referer header value for config requests
	 * @param couponsUrl URL for retrieving available digital coupons
	 * @param couponsActivationUrl URL for activating digital coupons
	 * @param couponsDeactivationUrl URL for deactivating digital coupons
	 */
	public record Urls(
			@NotBlank(message = "Base URL is required") @URL(message = "Base URL must be a valid URL") String baseUrl,

			@NotBlank(message = "Config URL is required") @URL(
					message = "Config URL must be a valid URL") String configUrl,

			@NotBlank(message = "Config URL referer is required") @URL(
					message = "Config URL referer must be a valid URL") String configUrlReferer,

			@NotBlank(message = "Coupons URL is required") @URL(
					message = "Coupons URL must be a valid URL") String couponsUrl,

			@NotBlank(message = "Coupons activation URL is required") @URL(
					message = "Coupons activation URL must be a valid URL") String couponsActivationUrl,

			@NotBlank(message = "Coupons deactivation URL is required") @URL(
					message = "Coupons deactivation URL must be a valid URL") String couponsDeactivationUrl

	) {
	}

	/**
	 * Configuration for selecting which coupons to activate.
	 *
	 * @param maxActiveCoupons upper bound on coupons activated in one run; Supercard caps
	 * how many may be active at once
	 * @param includeShop only coupons redeemable in this shop channel are activated (e.g.
	 * {@code retail} for physical stores)
	 * @param alwaysIncludeDiscountMarker coupons whose discount text contains this marker
	 * are activated regardless of product type; blank disables the exemption
	 * @param includeProductTypes product types eligible for activation. A coupon
	 * qualifies only when every one of its product types appears in this list.
	 * <p>
	 * Available product types:
	 * <ul>
	 * <li>"39" - Aktuelles Bonheft</li>
	 * <li>"20" - Baby, Kind</li>
	 * <li>"11" - Beauty</li>
	 * <li>"23" - Bijouterie, Bekleidung</li>
	 * <li>"01" - Bio, Nachhaltigkeit</li>
	 * <li>"17" - Blumen, Pflanzen</li>
	 * <li>"07" - Brot, Backwaren</li>
	 * <li>"12" - Drogerie</li>
	 * <li>"18" - Elektronik, Büro</li>
	 * <li>"09" - Fertiggerichte, Tiefkühlprodukte</li>
	 * <li>"04" - Fleisch, Fisch</li>
	 * <li>"19" - Freizeit, Sport</li>
	 * <li>"03" - Früchte, Gemüse</li>
	 * <li>"16" - Garten, Handwerk</li>
	 * <li>"06" - Getränke, alkoholisch</li>
	 * <li>"05" - Getränke, nicht alkoholisch</li>
	 * <li>"14" - Haushalt, Küche, Wohnen</li>
	 * <li>"02" - Milchprodukte, Eier</li>
	 * <li>"41" - Reisen, Ferien</li>
	 * <li>"08" - Snacks, Süsses</li>
	 * <li>"37" - Spezielle Ernährungsformen</li>
	 * <li>"40" - Super Bons</li>
	 * <li>"30" - Superpunkteangebote</li>
	 * <li>"38" - Take-Away, Menüs</li>
	 * <li>"21" - Tierwelt</li>
	 * <li>"31" - Treibstoff, Fahrzeugbedarf</li>
	 * <li>"36" - Vorräte</li>
	 * </ul>
	 */
	public record CouponFilter(

			@Min(value = 1, message = "At least one coupon must be activatable") @Max(value = 200,
					message = "Activating more than 200 coupons in one run is not supported") int maxActiveCoupons,

			@NotBlank(message = "Include shop is required") String includeShop,

			String alwaysIncludeDiscountMarker,

			List<String> includeProductTypes) {

		/**
		 * Compact constructor that creates a defensive copy of the includeProductTypes
		 * list.
		 */
		public CouponFilter {
			includeProductTypes = includeProductTypes == null ? List.of() : List.copyOf(includeProductTypes);
			alwaysIncludeDiscountMarker = alwaysIncludeDiscountMarker == null ? "" : alwaysIncludeDiscountMarker;
		}

	}
}

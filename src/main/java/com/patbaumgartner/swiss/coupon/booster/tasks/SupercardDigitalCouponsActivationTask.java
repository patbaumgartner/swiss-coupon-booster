package com.patbaumgartner.swiss.coupon.booster.tasks;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patbaumgartner.swiss.coupon.booster.apis.CoopDigitalBonsApi;
import com.patbaumgartner.swiss.coupon.booster.apis.CoopDigitalBonsApi.SupercardDigitalBon;
import com.patbaumgartner.swiss.coupon.booster.settings.CoopSupercardSettings;
import com.patbaumgartner.swiss.coupon.booster.settings.SwissCouponBoosterSettings;

@Slf4j
@Component
@Setter
@RequiredArgsConstructor
public class SupercardDigitalCouponsActivationTask implements ActivationTask {

	private final ObjectMapper objectMapper;

	private final RestClient.Builder restClientBuilder;

	private final SwissCouponBoosterSettings swissCouponBoosterSettings;

	private final CoopSupercardSettings coopSupercardSettings;

	@Override
	public void execute() {

		if (!coopSupercardSettings.enabled()) {
			log.info("Supercard Digital Bons Activation Task is disabled.");
			return;
		}

		CoopDigitalBonsApi coopDigitalBonsApi = new CoopDigitalBonsApi(restClientBuilder, objectMapper,
				swissCouponBoosterSettings, coopSupercardSettings);
		coopDigitalBonsApi.loginSupercardAccount();

		coopDigitalBonsApi.extractJwtTokenFromPwaConfigs();

		// Fetch all available bons and filter them by status
		List<SupercardDigitalBon> allDigitalBons = coopDigitalBonsApi.collectSupercardDigitalBons();

		// De-Activate all ACTIVE digital bons
		List<SupercardDigitalBon> activeDigitalBons = allDigitalBons.stream()
			.filter(item -> "ACTIVE".equals(item.status()))
			.toList();

		coopDigitalBonsApi.deactivateSupercardDigitalBons(activeDigitalBons);

		// Activate all OPEN digital bons
		allDigitalBons = coopDigitalBonsApi.collectSupercardDigitalBons();
		List<SupercardDigitalBon> inactiveDigitalBons = allDigitalBons.stream()
			.filter(item -> "OPEN".equals(item.status()))
			.filter(item -> "retail".equals(item.shop()) || "pronto".equals(item.shop()))
			.filter(this::filterProductTypes)
			.limit(20)
			.toList();

		coopDigitalBonsApi.activateSupercardDigitalBons(inactiveDigitalBons);

		log.info("Supercard Digital Bons activated.");
	}

	public boolean filterProductTypes(SupercardDigitalBon bon) {
		List<String> includeList = List.of("39" // Aktuelles Bonheft
		// ,"20" //Baby, Kind
				, "11" // Beauty
				// ,"23" //Bijouterie, Bekleidung
				, "01" // Bio, Nachhaltigkeit
				// ,"17" //Blumen, Pflanzen
				// ,"07" //Brot, Backwaren
				, "12" // Drogerie
				// ,"18" //Elektronik, Büro
				, "09" // Fertiggerichte, Tiefkühlprodukte
				// ,"04" //Fleisch, Fisch
				// ,"19" //Freizeit, Sport
				, "03" // Früchte, Gemüse
				// ,"16" //Garten, Handwerk
				, "06" // Getränke, alkoholisch
				, "05" // Getränke, nicht alkoholisch
				, "14" // Haushalt, Küche, Wohnen
				, "02" // Milchprodukte, Eier
				// ,"41" //Reisen, Ferien
				, "08" // Snacks, Süsses
				// ,"37" //Spezielle Ernährungsformen
				, "40" // Super Bons
				, "30" // Superpunkteangebote
				// ,"38" //Take-Away, Menüs
				// ,"21" //Tierwelt
				, "31" // Treibstoff, Fahrzeugbedarf
				, "36" // Vorräte
		);

		for (String productType : bon.productTypes()) {
			if (!includeList.contains(productType)) {
				return false;
			}
		}
		return true;
	}

}

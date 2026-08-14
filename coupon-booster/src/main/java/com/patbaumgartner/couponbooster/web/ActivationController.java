package com.patbaumgartner.couponbooster.web;

import com.patbaumgartner.couponbooster.coop.scheduler.CoopCouponBoosterScheduler;
import com.patbaumgartner.couponbooster.migros.scheduler.MigrosCouponBoosterScheduler;
import com.patbaumgartner.couponbooster.scheduler.AbstractCouponBoosterScheduler;
import com.patbaumgartner.couponbooster.scheduler.ActivationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manual trigger for coupon-activation runs in the long-running {@code server} profile.
 * <p>
 * Each endpoint runs the same flow as the daily scheduler and blocks until the run
 * completes, returning the {@link ActivationOutcome}. The underlying scheduler guards
 * against overlapping runs, so a manual trigger during an in-progress run returns
 * {@code 409 Conflict}.
 * <p>
 * Failures are reported as {@link ProblemDetail} (RFC 9457), so success and error
 * responses are both JSON with a documented shape.
 * <p>
 * The coupon-booster container exposes no port by default, so this endpoint is reachable
 * only from inside the Docker network (e.g. {@code docker compose exec}). Add a port
 * mapping and protect it before exposing it to a host or network.
 */
@RestController
@Profile("server")
@RequestMapping("/activations")
public class ActivationController {

	private static final Logger log = LoggerFactory.getLogger(ActivationController.class);

	private final ObjectProvider<CoopCouponBoosterScheduler> coopScheduler;

	private final ObjectProvider<MigrosCouponBoosterScheduler> migrosScheduler;

	/**
	 * Constructs the controller. Schedulers are injected lazily so the endpoint stays
	 * available even when a provider's scheduler is disabled.
	 * @param coopScheduler provider for the Coop scheduler bean
	 * @param migrosScheduler provider for the Migros scheduler bean
	 */
	public ActivationController(ObjectProvider<CoopCouponBoosterScheduler> coopScheduler,
			ObjectProvider<MigrosCouponBoosterScheduler> migrosScheduler) {
		this.coopScheduler = coopScheduler;
		this.migrosScheduler = migrosScheduler;
	}

	/**
	 * Triggers a Coop coupon-activation run.
	 * @return the activation outcome
	 */
	@PostMapping("/coop")
	public ActivationOutcome triggerCoop() {
		return trigger("Coop", coopScheduler.getIfAvailable());
	}

	/**
	 * Triggers a Migros coupon-activation run.
	 * @return the activation outcome
	 */
	@PostMapping("/migros")
	public ActivationOutcome triggerMigros() {
		return trigger("Migros", migrosScheduler.getIfAvailable());
	}

	private ActivationOutcome trigger(String provider, AbstractCouponBoosterScheduler scheduler) {
		if (scheduler == null) {
			log.warn("Manual {} activation requested but its scheduler is not enabled", provider);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"%s scheduler is not enabled".formatted(provider));
		}

		log.info("Manual {} activation triggered via REST", provider);
		return scheduler.runActivation()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
					"%s activation already in progress".formatted(provider)));
	}

}

package com.patbaumgartner.swiss.coupon.booster.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;

import org.springframework.stereotype.Component;

import com.patbaumgartner.swiss.coupon.booster.tasks.ActivationTask;

@Slf4j
@Component
@RequiredArgsConstructor
public class CuponActivationRunner {

	private final List<ActivationTask> activationTasks;

	@Job(name = "Cupon Activation Job")
	@Recurring(id = "cupon-activation-job", cron = "0 2 * * *")
	public void recurringJob() {
		log.info("The cupon activation Job has begun.");
		try {
			activationTasks.forEach(ActivationTask::execute);
		}
		catch (Exception e) {
			log.error("Error while executing cupon activation job", e);
		}
		finally {
			log.info("The cupon activation job has finished...");
		}
	}

}

package com.patbaumgartner.swiss.coupon.booster.configs;

import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JobRunrConfig {

	@Bean
	StorageProvider storageProvider(JobMapper jobMapper) {
		InMemoryStorageProvider storageProvider = new InMemoryStorageProvider();
		storageProvider.setJobMapper(new JobMapper(new JacksonJsonMapper()));
		return storageProvider;
	}

}

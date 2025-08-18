package com.patbaumgartner.migroscouponbooster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CouponBoosterApplication {

	public static void main(String[] args) {
		SpringApplication.run(CouponBoosterApplication.class, args);
	}

}

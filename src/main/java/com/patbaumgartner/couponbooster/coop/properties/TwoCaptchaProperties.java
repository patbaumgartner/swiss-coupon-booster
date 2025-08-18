package com.patbaumgartner.couponbooster.coop.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "twocaptcha")
@Validated
public record TwoCaptchaProperties(@NotBlank(message = "API_KEY is required") String apiKey,
		@NotBlank(message = "Website url is required") String websiteUrl) {
}

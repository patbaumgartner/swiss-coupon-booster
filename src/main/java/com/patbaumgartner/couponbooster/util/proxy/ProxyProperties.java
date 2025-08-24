package com.patbaumgartner.couponbooster.util.proxy;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "proxy")
@Validated
public record ProxyProperties(boolean enabled, @NotBlank(message = "List url is required") String listUrl) {
}

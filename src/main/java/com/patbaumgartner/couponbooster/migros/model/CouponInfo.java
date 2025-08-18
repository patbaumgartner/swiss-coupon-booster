package com.patbaumgartner.couponbooster.migros.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CouponInfo(@JsonProperty("id") String id, @JsonProperty("name") String name,
		@JsonProperty("description") String description, @JsonProperty("validUntil") String validUntil,
		@JsonProperty("activated") boolean activated) {
}

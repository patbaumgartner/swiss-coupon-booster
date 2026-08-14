package com.patbaumgartner.couponbooster.web;

import com.patbaumgartner.couponbooster.coop.scheduler.CoopCouponBoosterScheduler;
import com.patbaumgartner.couponbooster.migros.scheduler.MigrosCouponBoosterScheduler;
import com.patbaumgartner.couponbooster.scheduler.ActivationOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivationController.class)
@ActiveProfiles("server")
class ActivationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CoopCouponBoosterScheduler coopScheduler;

	@MockitoBean
	private MigrosCouponBoosterScheduler migrosScheduler;

	@Test
	void completedRunIsReturnedAsJson() throws Exception {
		when(coopScheduler.runActivation())
			.thenReturn(Optional.of(new ActivationOutcome("Coop", true, 5, 1, 4210L, "Activation completed")));

		mockMvc.perform(post("/activations/coop"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.provider").value("Coop"))
			.andExpect(jsonPath("$.authenticated").value(true))
			.andExpect(jsonPath("$.activated").value(5))
			.andExpect(jsonPath("$.failed").value(1))
			.andExpect(jsonPath("$.authDurationMs").value(4210))
			.andExpect(jsonPath("$.message").value("Activation completed"));
	}

	@Test
	void migrosEndpointTriggersOnlyTheMigrosScheduler() throws Exception {
		when(migrosScheduler.runActivation())
			.thenReturn(Optional.of(new ActivationOutcome("Migros", true, 2, 0, 100L, "Activation completed")));

		mockMvc.perform(post("/activations/migros")).andExpect(status().isOk());

		verify(migrosScheduler).runActivation();
		verify(coopScheduler, never()).runActivation();
	}

	@Test
	void overlappingRunIsRejectedAsProblemDetail() throws Exception {
		when(coopScheduler.runActivation()).thenReturn(Optional.empty());

		mockMvc.perform(post("/activations/coop"))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.detail").value("Coop activation already in progress"));
	}

	@Test
	void getIsNotAllowed() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/activations/coop"))
			.andExpect(status().isMethodNotAllowed());
	}

}

package de.signaliduna.elpa.hint.config;

import de.signaliduna.elpa.hint.core.HintService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Wir importieren den Controller explizit, damit er sicher gefunden wird
@WebMvcTest()
@Import({WebSecurityConfig.class, WebSecurityConfigTest.TestController.class})
@TestPropertySource(properties = "authorization.users=S123456,U123456")
class WebSecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private HintService hintService;

	@Test
	void accessGrantedWhenUserIsInListByName() throws Exception {
		mockMvc.perform(get("/test-auth")
				.with(jwt().jwt(builder -> builder.subject("S123456"))))
			.andExpect(status().isOk());
	}

	@Test
	void accessGrantedWhenUserIsInListByUid() throws Exception {
		mockMvc.perform(get("/test-auth")
				.with(jwt().jwt(builder -> builder.subject("unknown").claim("uid", "U123456"))))
			.andExpect(status().isOk());
	}

	@Test
	void accessDeniedWhenUserIsNotInList() throws Exception {
		mockMvc.perform(get("/test-auth")
				.with(jwt().jwt(builder -> builder.subject("U000000").claim("uid", "dave"))))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "U000000")
	void accessDeniedWhenNotJwtAndNameNotAuthorized() throws Exception {
		mockMvc.perform(get("/test-auth"))
			.andExpect(status().isForbidden());
	}

	@Test
	void accessDeniedWhenUidClaimMissing() throws Exception {
		mockMvc.perform(get("/test-auth")
				.with(jwt().jwt(builder -> builder.subject("U000000"))))
			.andExpect(status().isForbidden());
	}

	/**
	 * Der Controller muss public static sein und explizit importiert werden,
	 * damit der Web-Kontext ihn sicher findet.
	 */
	@RestController
	public static class TestController {
		@GetMapping("/test-auth")
		@PreAuthorize("isAuthorizedUser()")
		public String test() {
			return "OK";
		}
	}
}



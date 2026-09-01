package com.shyblack.cryptosignals;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shyblack.cryptosignals.security.oauth2.GoogleTokenVerifier;
import com.shyblack.cryptosignals.security.oauth2.GoogleUserInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GoogleTokenVerifier googleTokenVerifier;

	@Test
	void signupLoginMeAndRefresh() throws Exception {
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"trader@example.com","password":"secret123","fullName":"Ada Trader"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.message").value("Account created successfully"));

		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"trader@example.com","password":"secret123","fullName":"Ada Trader"}
								"""))
				.andExpect(status().isConflict());

		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"weak@example.com","password":"nodigits","fullName":"Weak"}
								"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"trader@example.com","password":"wrongpass1"}
								"""))
				.andExpect(status().isUnauthorized());

		MvcResult login = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"trader@example.com","password":"secret123"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andReturn();

		String accessToken = json(login, "accessToken");
		String refreshToken = json(login, "refreshToken");

		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/users/me")
						.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("trader@example.com"))
				.andExpect(jsonPath("$.fullName").value("Ada Trader"));

		mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\":\"" + refreshToken + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
	}

	@Test
	void googleLoginCreatesUser() throws Exception {
		when(googleTokenVerifier.verify(anyString()))
				.thenReturn(new GoogleUserInfo("google-sub-1", "google.user@example.com", "Google User"));

		mockMvc.perform(post("/api/auth/google")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"idToken":"fake-google-id-token"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty());
	}

	@Test
	void googleLoginExistingEmailIssuesTokens() throws Exception {
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"linked.google@example.com","password":"secret123","fullName":"Linked User"}
								"""))
				.andExpect(status().isCreated());

		when(googleTokenVerifier.verify(anyString()))
				.thenReturn(new GoogleUserInfo("google-sub-2", "linked.google@example.com", "Linked User"));

		mockMvc.perform(post("/api/auth/google")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"idToken":"fake-google-id-token"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty());
	}

	@Test
	void corsPreflightAllowsLocalhostFlutterWeb() throws Exception {
		mockMvc.perform(options("/api/auth/login")
						.header(HttpHeaders.ORIGIN, "http://localhost:5555")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type, authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5555"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}

	private static String json(MvcResult result, String field) throws Exception {
		String body = result.getResponse().getContentAsString();
		int start = body.indexOf("\"" + field + "\":\"") + field.length() + 4;
		int end = body.indexOf('"', start);
		return body.substring(start, end);
	}
}

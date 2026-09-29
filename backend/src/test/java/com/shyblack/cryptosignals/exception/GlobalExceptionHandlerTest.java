package com.shyblack.cryptosignals.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Guards the 404-vs-500 contract: an unmapped path must map to 404 using the
 * app's standard {@link ApiError} envelope, not the generic 500 handler.
 */
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void unmappedResourceReturns404Not500() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/does-not-exist");

		NoResourceFoundException ex =
				new NoResourceFoundException(HttpMethod.GET, "/api/does-not-exist", "No static resource");

		ResponseEntity<ApiError> response = handler.handleNoResource(ex, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo(404);
		assertThat(response.getBody().path()).isEqualTo("/api/does-not-exist");
	}

	@Test
	void genericFailureStillReturns500() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/boom");

		ResponseEntity<ApiError> response =
				handler.handleGeneric(new IllegalStateException("boom"), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo(500);
	}
}

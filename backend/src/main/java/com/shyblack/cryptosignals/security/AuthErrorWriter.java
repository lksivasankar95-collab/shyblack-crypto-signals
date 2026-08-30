package com.shyblack.cryptosignals.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class AuthErrorWriter {

	public void write(HttpServletRequest request, HttpServletResponse response, int status, String error, String message)
			throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		String json = """
				{"timestamp":"%s","status":%d,"error":"%s","message":"%s","path":"%s","details":[]}
				""".formatted(
				Instant.now(),
				status,
				escape(error),
				escape(message),
				escape(request.getRequestURI())
		);
		response.getWriter().write(json);
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}

package com.shyblack.cryptosignals.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SignupRequest(
		@Email @NotBlank String email,
		@NotBlank
		@Pattern(
				regexp = "^(?=.*\\d).{8,}$",
				message = "Password must be at least 8 characters and contain at least one number"
		)
		String password,
		@NotBlank String fullName
) {
}

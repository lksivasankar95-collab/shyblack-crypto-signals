package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.user.UpdateProfileRequest;
import com.shyblack.cryptosignals.dto.user.UserResponse;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Current user profile")
@SecurityRequirement(name = "bearer-jwt")
public class UserController {

	private final UserService userService;

	@Operation(summary = "Get the logged-in user's profile")
	@GetMapping("/me")
	public UserResponse me() {
		return userService.me();
	}

	@Operation(summary = "Update the logged-in user's profile")
	@PatchMapping("/me")
	public UserResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
			throw new ResourceNotFoundException("User not found");
		}
		return userService.updateMe(principal, request);
	}

	@GetMapping
	public List<UserResponse> list() {
		return userService.findAll();
	}

	@GetMapping("/{id}")
	public UserResponse getById(@PathVariable UUID id) {
		return userService.findById(id);
	}
}

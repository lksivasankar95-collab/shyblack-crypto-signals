package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.user.UpdateProfileRequest;
import com.shyblack.cryptosignals.dto.user.UserResponse;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.security.UserPrincipal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	public List<UserResponse> findAll() {
		return List.of();
	}

	public UserResponse findById(UUID id) {
		return userRepository.findById(id)
				.map(UserResponse::from)
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	@Transactional(readOnly = true)
	public UserResponse me() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
			throw new ResourceNotFoundException("User not found");
		}
		User user = userRepository.findById(principal.getId())
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
		return UserResponse.from(user);
	}

	@Transactional
	public UserResponse updateMe(UserPrincipal principal, UpdateProfileRequest request) {
		User user = userRepository.findById(principal.getId())
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
		boolean changed = false;
		if (request.fullName() != null && !request.fullName().isBlank()) {
			user.setFullName(request.fullName().trim());
			changed = true;
		}
		if (request.phoneNumber() != null) {
			user.setPhoneNumber(request.phoneNumber().isBlank() ? null : request.phoneNumber().trim());
			changed = true;
		}
		if (request.country() != null) {
			user.setCountry(request.country().isBlank() ? null : request.country().trim());
			changed = true;
		}
		if (request.timezone() != null && !request.timezone().isBlank()) {
			user.setTimezone(request.timezone().trim());
			changed = true;
		}
		if (changed) {
			userRepository.save(user);
		}
		return UserResponse.from(user);
	}
}

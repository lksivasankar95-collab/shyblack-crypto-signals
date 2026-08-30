package com.shyblack.cryptosignals.service;

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
}

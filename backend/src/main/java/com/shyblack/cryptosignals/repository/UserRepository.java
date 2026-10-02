package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	Optional<User> findByGoogleSub(String googleSub);

	boolean existsByEmail(String email);

	/**
	 * Users currently in live-account mode.
	 *
	 * <p>Used by the Portfolio synchronization coordinator to find which accounts may hold an
	 * exchange user-data stream. Derived rather than cached, so switching a user back to paper
	 * removes them from the next pass without any bookkeeping of its own.
	 */
	List<User> findByAccountType(AccountType accountType);
}

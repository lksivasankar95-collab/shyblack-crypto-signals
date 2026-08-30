package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	Optional<User> findByGoogleSub(String googleSub);

	boolean existsByEmail(String email);
}

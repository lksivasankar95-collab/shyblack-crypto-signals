package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.UserSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {

	Optional<UserSettings> findByUser_Id(UUID userId);

	boolean existsByUser_Id(UUID userId);
}
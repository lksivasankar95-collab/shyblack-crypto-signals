package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.NotificationPreference;
import com.shyblack.cryptosignals.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, java.util.UUID> {

    Optional<NotificationPreference> findByUser(User user);
}

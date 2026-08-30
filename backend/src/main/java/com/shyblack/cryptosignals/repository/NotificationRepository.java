package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.Notification;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.NotificationCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	List<Notification> findByUser(User user);

	List<Notification> findByUserAndCategory(User user, NotificationCategory category);
}

package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.DeviceToken;
import com.shyblack.cryptosignals.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, java.util.UUID> {

    List<DeviceToken> findByUserAndActiveTrue(User user);

    List<DeviceToken> findByActiveTrue();

    boolean existsByToken(String token);
}

package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.entity.NotificationPreference;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repo;

    public boolean signalsEnabledFor(User user) {
        return repo.findByUser(user).map(NotificationPreference::isSignalsEnabled).orElse(true);
    }
}

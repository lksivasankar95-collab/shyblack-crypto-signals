package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.settings.NotificationPreferenceRequest;
import com.shyblack.cryptosignals.dto.settings.NotificationPreferenceResponse;
import com.shyblack.cryptosignals.entity.NotificationPreference;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.repository.NotificationPreferenceRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repo;
    private final UserRepository userRepository;

    public boolean signalsEnabledFor(User user) {
        return repo.findByUser(user).map(NotificationPreference::isSignalsEnabled).orElse(true);
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse get(UserPrincipal principal) {
        User user = currentUser(principal);
        NotificationPreference pref = getOrCreate(user);
        return toResponse(pref);
    }

    @Transactional
    public NotificationPreferenceResponse update(UserPrincipal principal, NotificationPreferenceRequest request) {
        User user = currentUser(principal);
        NotificationPreference pref = getOrCreate(user);
        boolean changed = false;
        if (request.signalsEnabled() != null) { pref.setSignalsEnabled(request.signalsEnabled()); changed = true; }
        if (request.buyAlertsEnabled() != null) { pref.setBuyAlertsEnabled(request.buyAlertsEnabled()); changed = true; }
        if (request.sellAlertsEnabled() != null) { pref.setSellAlertsEnabled(request.sellAlertsEnabled()); changed = true; }
        if (request.newsAlertsEnabled() != null) { pref.setNewsAlertsEnabled(request.newsAlertsEnabled()); changed = true; }
        if (request.systemAlertsEnabled() != null) { pref.setSystemAlertsEnabled(request.systemAlertsEnabled()); changed = true; }
        if (request.minimumSignalGrade() != null) { pref.setMinimumSignalGrade(request.minimumSignalGrade().isBlank() ? null : request.minimumSignalGrade().trim()); changed = true; }
        if (changed) { repo.save(pref); }
        return toResponse(pref);
    }

    private NotificationPreference getOrCreate(User user) {
        return repo.findByUser(user).orElseGet(() -> {
            NotificationPreference pref = new NotificationPreference();
            pref.setUser(user);
            pref.setSignalsEnabled(true);
            pref.setBuyAlertsEnabled(true);
            pref.setSellAlertsEnabled(true);
            pref.setNewsAlertsEnabled(true);
            pref.setSystemAlertsEnabled(true);
            pref.setMinimumSignalGrade(null);
            return repo.save(pref);
        });
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference pref) {
        return new NotificationPreferenceResponse(
                pref.getId(),
                pref.isSignalsEnabled(),
                pref.isBuyAlertsEnabled(),
                pref.isSellAlertsEnabled(),
                pref.isNewsAlertsEnabled(),
                pref.isSystemAlertsEnabled(),
                pref.getMinimumSignalGrade()
        );
    }

    private User currentUser(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}

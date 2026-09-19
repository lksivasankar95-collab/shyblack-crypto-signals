package com.shyblack.cryptosignals.dto.settings;

import java.util.UUID;

public record NotificationPreferenceResponse(
        UUID id,
        boolean signalsEnabled,
        boolean buyAlertsEnabled,
        boolean sellAlertsEnabled,
        boolean newsAlertsEnabled,
        boolean systemAlertsEnabled,
        String minimumSignalGrade
) {
}

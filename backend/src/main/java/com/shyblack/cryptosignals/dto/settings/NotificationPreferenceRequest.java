package com.shyblack.cryptosignals.dto.settings;

public record NotificationPreferenceRequest(
        Boolean signalsEnabled,
        Boolean buyAlertsEnabled,
        Boolean sellAlertsEnabled,
        Boolean newsAlertsEnabled,
        Boolean systemAlertsEnabled,
        String minimumSignalGrade
) {
}

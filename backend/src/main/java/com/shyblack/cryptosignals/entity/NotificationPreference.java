package com.shyblack.cryptosignals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private boolean signalsEnabled = true;

    @Column(nullable = false)
    private boolean buyAlertsEnabled = true;

    @Column(nullable = false)
    private boolean sellAlertsEnabled = true;

    @Column(nullable = false)
    private boolean newsAlertsEnabled = true;

    @Column(nullable = false)
    private boolean systemAlertsEnabled = true;

    @Column(nullable = true)
    private String minimumSignalGrade;
}

package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.AuthProvider;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import com.shyblack.cryptosignals.entity.enums.Role;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity {

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false)
	private String fullName;

	private String passwordHash;

	private String phoneNumber;

	private String country;

	@Column(nullable = false)
	private String timezone = "UTC";

	@Column(unique = true)
	private String googleSub;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AuthProvider authProvider = AuthProvider.LOCAL;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role = Role.USER;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountType accountType = AccountType.PAPER;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TradingMode tradingMode = TradingMode.SPOT;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RiskProfile riskProfile = RiskProfile.MODERATE;

	@Column(nullable = false)
	private boolean enabled = true;
}

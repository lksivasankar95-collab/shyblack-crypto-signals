package com.shyblack.cryptosignals.dto.user;

import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
		UUID id,
		String email,
		String fullName,
		String phoneNumber,
		String country,
		String timezone,
		AccountType accountType,
		TradingMode tradingMode,
		RiskProfile riskProfile,
		Instant createdAt,
		Instant updatedAt
) {

	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				user.getPhoneNumber(),
				user.getCountry(),
				user.getTimezone(),
				user.getAccountType(),
				user.getTradingMode(),
				user.getRiskProfile(),
				user.getCreatedAt(),
				user.getUpdatedAt()
		);
	}
}

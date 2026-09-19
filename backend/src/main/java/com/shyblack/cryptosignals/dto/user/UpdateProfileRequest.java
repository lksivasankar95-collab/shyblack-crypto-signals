package com.shyblack.cryptosignals.dto.user;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 1, max = 200) String fullName,
        @Size(max = 50) String phoneNumber,
        @Size(max = 100) String country,
        @Size(max = 100) String timezone
) {
}

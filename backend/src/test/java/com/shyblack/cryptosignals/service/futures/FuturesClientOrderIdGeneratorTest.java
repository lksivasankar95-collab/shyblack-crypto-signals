package com.shyblack.cryptosignals.service.futures;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.FuturesOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FuturesClientOrderIdGeneratorTest {

	private final FuturesClientOrderIdGenerator gen = new FuturesClientOrderIdGenerator();

	@Test
	void deterministic_sameInputsSameId() {
		UUID user = UUID.fromString("11111111-1111-1111-1111-111111111111");
		UUID sig = UUID.fromString("22222222-2222-2222-2222-222222222222");
		String a = gen.forSignal(user, sig, "BTCUSDT", PositionSide.LONG, FuturesOrderPurpose.ENTRY);
		String b = gen.forSignal(user, sig, "BTCUSDT", PositionSide.LONG, FuturesOrderPurpose.ENTRY);
		assertThat(a).isEqualTo(b);
		assertThat(a).startsWith("SBF-").hasSizeLessThanOrEqualTo(32);
	}

	@Test
	void futuresId_isDistinct_fromSpotId() {
		// Futures ids are prefixed SBF-, spot ids SB- — they cannot collide.
		UUID user = UUID.randomUUID();
		UUID sig = UUID.randomUUID();
		String futures = gen.forSignal(user, sig, "BTCUSDT", PositionSide.LONG, FuturesOrderPurpose.ENTRY);
		assertThat(futures).startsWith("SBF-");
	}

	@Test
	void differentPositionSide_yieldsDifferentId() {
		UUID user = UUID.randomUUID();
		UUID sig = UUID.randomUUID();
		String longId = gen.forSignal(user, sig, "BTCUSDT", PositionSide.LONG, FuturesOrderPurpose.ENTRY);
		String shortId = gen.forSignal(user, sig, "BTCUSDT", PositionSide.SHORT, FuturesOrderPurpose.ENTRY);
		assertThat(longId).isNotEqualTo(shortId);
	}
}

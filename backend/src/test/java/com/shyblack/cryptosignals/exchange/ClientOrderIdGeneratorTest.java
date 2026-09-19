package com.shyblack.cryptosignals.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientOrderIdGeneratorTest {

	private final ClientOrderIdGenerator gen = new ClientOrderIdGenerator();

	@Test
	void deterministic_forSignal_sameInputsSameId() {
		UUID user = UUID.fromString("11111111-1111-1111-1111-111111111111");
		UUID sig = UUID.fromString("22222222-2222-2222-2222-222222222222");
		String a = gen.forSignal(user, sig, "BTCUSDT", PositionSide.LONG, LiveOrderPurpose.ENTRY);
		String b = gen.forSignal(user, sig, "BTCUSDT", PositionSide.LONG, LiveOrderPurpose.ENTRY);
		assertThat(a).isEqualTo(b);
		assertThat(a).startsWith("SB-").hasSizeLessThanOrEqualTo(32);
	}

	@Test
	void differentSignal_yieldsDifferentId() {
		UUID user = UUID.randomUUID();
		String a = gen.forSignal(user, UUID.randomUUID(), "BTCUSDT",
				PositionSide.LONG, LiveOrderPurpose.ENTRY);
		String b = gen.forSignal(user, UUID.randomUUID(), "BTCUSDT",
				PositionSide.LONG, LiveOrderPurpose.ENTRY);
		assertThat(a).isNotEqualTo(b);
	}

	@Test
	void differentPurpose_yieldsDifferentId() {
		UUID user = UUID.randomUUID();
		UUID sig = UUID.randomUUID();
		String entry = gen.forSignal(user, sig, "BTCUSDT",
				PositionSide.LONG, LiveOrderPurpose.ENTRY);
		String stop = gen.forSignal(user, sig, "BTCUSDT",
				PositionSide.LONG, LiveOrderPurpose.STOP_LOSS);
		assertThat(entry).isNotEqualTo(stop);
	}
}

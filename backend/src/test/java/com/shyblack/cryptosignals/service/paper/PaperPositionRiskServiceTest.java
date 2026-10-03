package com.shyblack.cryptosignals.service.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.repository.PositionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SL/TP repositioning on an open paper position.
 *
 * <p>The failure this guards against is silent: a level on the wrong side of
 * entry can never be touched by the engine, so the position would run with no
 * effective protection while the UI happily displayed a stop price.</p>
 */
class PaperPositionRiskServiceTest {

	private final PositionRepository positions = mock(PositionRepository.class);

	private PaperPositionRiskService service;
	private User owner;
	private Portfolio portfolio;

	@BeforeEach
	void setUp() {
		service = new PaperPositionRiskService(positions);
		owner = new User();
		owner.setId(UUID.randomUUID());
		portfolio = new Portfolio();
		portfolio.setId(UUID.randomUUID());
		portfolio.setUser(owner);
		portfolio.setAccountType(AccountType.PAPER);
		portfolio.setAvailableBalance(new BigDecimal("1000"));
		portfolio.setInvested(BigDecimal.ZERO);
		when(positions.save(any(Position.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private Position open(PositionSide side, String entry) {
		Position p = new Position();
		p.setId(UUID.randomUUID());
		p.setPortfolio(portfolio);
		p.setSymbol("BTCUSDT");
		p.setSide(side);
		p.setStatus(PositionStatus.OPEN);
		p.setEntryPrice(new BigDecimal(entry));
		p.setStopLoss(side == PositionSide.LONG ? new BigDecimal("90") : new BigDecimal("110"));
		p.setTakeProfit1(side == PositionSide.LONG ? new BigDecimal("120") : new BigDecimal("80"));
		when(positions.findByPortfolio_UserOrderByCreatedAtDesc(owner)).thenReturn(List.of(p));
		when(positions.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));
		return p;
	}

	// ── Happy path ───────────────────────────────────────────────

	@Test
	void long_stopAndTarget_areRepositioned() {
		Position p = open(PositionSide.LONG, "100");

		Position updated = service.updateRisk(owner, p.getId(),
				new BigDecimal("95"), new BigDecimal("130"));

		assertThat(updated.getStopLoss()).isEqualByComparingTo("95");
		assertThat(updated.getTakeProfit1()).isEqualByComparingTo("130");
	}

	@Test
	void short_stopAndTarget_areRepositioned() {
		Position p = open(PositionSide.SHORT, "100");

		Position updated = service.updateRisk(owner, p.getId(),
				new BigDecimal("105"), new BigDecimal("70"));

		assertThat(updated.getStopLoss()).isEqualByComparingTo("105");
		assertThat(updated.getTakeProfit1()).isEqualByComparingTo("70");
	}

	@Test
	void omittedTarget_leavesTheExistingStopAndTargetAlone() {
		Position p = open(PositionSide.LONG, "100");

		Position updated = service.updateRisk(owner, p.getId(), new BigDecimal("95"), null);

		assertThat(updated.getStopLoss()).isEqualByComparingTo("95");
		assertThat(updated.getTakeProfit1())
				.as("a null field means 'leave alone', never 'clear'")
				.isEqualByComparingTo("120");
	}

	@Test
	void omittedStop_leavesTheExistingTargetAlone() {
		Position p = open(PositionSide.LONG, "100");

		Position updated = service.updateRisk(owner, p.getId(), null, new BigDecimal("140"));

		assertThat(updated.getStopLoss()).isEqualByComparingTo("90");
		assertThat(updated.getTakeProfit1()).isEqualByComparingTo("140");
	}

	@Test
	void aPositionMayStartWithNoLevelsAtAll() {
		Position p = open(PositionSide.LONG, "100");
		p.setStopLoss(null);
		p.setTakeProfit1(null);

		Position updated = service.updateRisk(owner, p.getId(),
				new BigDecimal("97"), new BigDecimal("111"));

		assertThat(updated.getStopLoss()).isEqualByComparingTo("97");
		assertThat(updated.getTakeProfit1()).isEqualByComparingTo("111");
	}

	// ── Rejected values ──────────────────────────────────────────

	@Test
	void longStopAtOrAboveEntry_isRejected() {
		Position p = open(PositionSide.LONG, "100");

		assertThatThrownBy(() -> service.updateRisk(owner, p.getId(), new BigDecimal("100"), null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("below the entry price");
		assertThatThrownBy(() -> service.updateRisk(owner, p.getId(), new BigDecimal("105"), null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("below the entry price");
		verify(positions, never()).save(any());
	}

	@Test
	void longTargetAtOrBelowEntry_isRejected() {
		Position p = open(PositionSide.LONG, "100");

		assertThatThrownBy(() -> service.updateRisk(owner, p.getId(), null, new BigDecimal("100")))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("above the entry price");
		assertThatThrownBy(() -> service.updateRisk(owner, p.getId(), null, new BigDecimal("95")))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("above the entry price");
	}

	@Test
	void shortStopAtOrBelowEntry_isRejected() {
		Position p = open(PositionSide.SHORT, "100");

		assertThatThrownBy(() -> service.updateRisk(owner, p.getId(), new BigDecimal("99"), null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("above the entry price");
	}

	@Test
	void shortTargetAtOrAboveEntry_isRejected() {
		Position p = open(PositionSide.SHORT, "100");

		assertThatThrownBy(() -> service.updateRisk(owner, p.getId(), null, new BigDecimal("101")))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("below the entry price");
	}

	@Test
	void nonPositiveLevels_areRejected() {
		Position p = open(PositionSide.LONG, "100");

		assertThatThrownBy(() -> service.updateRisk(owner, p.getId(), BigDecimal.ZERO, null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("greater than zero");
		assertThatThrownBy(() -> service.updateRisk(owner, p.getId(), null, new BigDecimal("-5")))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("greater than zero");
	}

	/**
	 * The entry-anchored checks also rule out an inverted stop/target pair:
	 * for a LONG, stop &lt; entry &lt; target implies stop &lt; target, and for a
	 * SHORT the mirror holds. So no separate cross-check is needed — this pins
	 * that the invariant genuinely follows from validation, so a future change
	 * to either rule cannot silently open an unprotected position.
	 */
	@Test
	void longStopAboveTarget_isImpossibleToReachThroughValidation() {
		Position p = open(PositionSide.LONG, "100");

		// stop 119 fails the entry rule on its own, before any pair check.
		assertThatThrownBy(() -> service.updateRisk(owner, p.getId(),
				new BigDecimal("119"), new BigDecimal("110")))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("below the entry price");

		// A stop below entry can only pair with a target above entry.
		Position ok = service.updateRisk(owner, p.getId(),
				new BigDecimal("99"), new BigDecimal("110"));
		assertThat(ok.getStopLoss()).isLessThan(ok.getTakeProfit1());
	}

	@Test
	void shortStopBelowTarget_isImpossibleToReachThroughValidation() {
		Position p = open(PositionSide.SHORT, "100");

		assertThatThrownBy(() -> service.updateRisk(owner, p.getId(),
				new BigDecimal("81"), new BigDecimal("90")))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("above the entry price");

		Position ok = service.updateRisk(owner, p.getId(),
				new BigDecimal("110"), new BigDecimal("90"));
		assertThat(ok.getStopLoss()).isGreaterThan(ok.getTakeProfit1());
	}

	// ── State + ownership guards ─────────────────────────────────

	@Test
	void aClosedPosition_cannotHaveItsRiskEdited() {
		Position p = open(PositionSide.LONG, "100");
		p.setStatus(PositionStatus.CLOSED);

		assertThatThrownBy(() -> service.updateRisk(owner, p.getId(), new BigDecimal("95"), null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("not open");
	}

	@Test
	void anotherUsersPosition_isNotFound() {
		Position p = open(PositionSide.LONG, "100");
		User intruder = new User();
		intruder.setId(UUID.randomUUID());
		when(positions.findByPortfolio_UserOrderByCreatedAtDesc(intruder)).thenReturn(List.of());

		assertThatThrownBy(() -> service.updateRisk(intruder, p.getId(), new BigDecimal("95"), null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("not found");
		verify(positions, never()).save(any());
	}

	@Test
	void aLivePortfolioPosition_isRefused() {
		Position p = open(PositionSide.LONG, "100");
		portfolio.setAccountType(AccountType.LIVE);

		assertThatThrownBy(() -> service.updateRisk(owner, p.getId(), new BigDecimal("95"), null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("only be edited on paper positions");
		verify(positions, never()).save(any());
	}

	@Test
	void aPositionWithNoEntryPrice_isRejectedRatherThanGuessed() {
		Position p = open(PositionSide.LONG, "100");
		p.setEntryPrice(null);

		assertThatThrownBy(() -> service.updateRisk(owner, p.getId(), new BigDecimal("95"), null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("no usable entry price");
	}

	@Test
	void nullUserOrId_isRejected() {
		assertThatThrownBy(() -> service.updateRisk(null, UUID.randomUUID(), BigDecimal.ONE, null))
				.isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> service.updateRisk(owner, null, BigDecimal.ONE, null))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void levelsSurviveARoundTripWithEightDecimalPlaces() {
		Position p = open(PositionSide.LONG, "100");

		Position updated = service.updateRisk(owner, p.getId(),
				new BigDecimal("99.12345678"), new BigDecimal("101.87654321"));

		assertThat(updated.getStopLoss()).isEqualByComparingTo("99.12345678");
		assertThat(updated.getTakeProfit1()).isEqualByComparingTo("101.87654321");
	}
}

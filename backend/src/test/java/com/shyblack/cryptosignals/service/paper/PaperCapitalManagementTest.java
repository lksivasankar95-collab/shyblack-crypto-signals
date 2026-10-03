package com.shyblack.cryptosignals.service.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.entity.PaperCapitalEvent;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.PaperCapitalEventType;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.repository.PaperCapitalEventRepository;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Paper capital management: add, reduce, reset and the audited ledger.
 *
 * <p>The load-bearing invariant is {@code totalBalance == availableBalance +
 * invested}. Every movement must preserve it, must be audited, and must never
 * be able to reach a LIVE account.</p>
 */
class PaperCapitalManagementTest {

	private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
	private final PaperCapitalEventRepository events = mock(PaperCapitalEventRepository.class);

	private PaperTradingAccountService service;
	private User user;

	@BeforeEach
	void setUp() {
		PaperTradingProperties props = new PaperTradingProperties(null, null, null, 0, null, null);
		service = new PaperTradingAccountService(portfolios, props, events);
		user = new User();
		user.setId(UUID.randomUUID());
		when(portfolios.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));
		when(events.save(any(PaperCapitalEvent.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	/** available = 60, invested = 40 -> total 100. */
	private Portfolio account(String available, String invested) {
		Portfolio p = new Portfolio();
		p.setId(UUID.randomUUID());
		p.setUser(user);
		p.setAccountType(AccountType.PAPER);
		p.setQuoteCurrency("USDT");
		p.setInitialBalance(new BigDecimal("100"));
		p.setAvailableBalance(new BigDecimal(available));
		p.setInvested(new BigDecimal(invested));
		p.setTotalBalance(new BigDecimal(available).add(new BigDecimal(invested)));
		p.setRealizedPnl(BigDecimal.ZERO);
		p.setTotalFees(BigDecimal.ZERO);
		return p;
	}

	private Portfolio locked(Portfolio p) {
		when(portfolios.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));
		return p;
	}

	private PaperCapitalEvent capturedEvent() {
		ArgumentCaptor<PaperCapitalEvent> captor = ArgumentCaptor.forClass(PaperCapitalEvent.class);
		verify(events).save(captor.capture());
		return captor.getValue();
	}

	// ── ADD ───────────────────────────────────────────────────────

	@Test
	void addCapital_raisesAvailableAndTotalByTheSameAmount() {
		Portfolio p = locked(account("60", "40"));

		Portfolio saved = service.addCapital(p, new BigDecimal("500"), "top up");

		assertThat(saved.getAvailableBalance()).isEqualByComparingTo("560");
		assertThat(saved.getInvested()).isEqualByComparingTo("40");
		assertThat(saved.getTotalBalance()).isEqualByComparingTo("600");
	}

	@Test
	void addCapital_neverRewritesTheSeedBalance() {
		Portfolio p = locked(account("60", "40"));

		service.addCapital(p, new BigDecimal("500"), "top up");

		assertThat(p.getInitialBalance())
				.as("the ledger explains the balance; the seed constant stays put")
				.isEqualByComparingTo("100");
	}

	@Test
	void addCapital_writesAnAuditedAddEvent() {
		Portfolio p = locked(account("60", "40"));

		service.addCapital(p, new BigDecimal("500"), "deposit bonus");

		PaperCapitalEvent e = capturedEvent();
		assertThat(e.getEventType()).isEqualTo(PaperCapitalEventType.ADD);
		assertThat(e.getAmount()).isEqualByComparingTo("500");
		assertThat(e.getPreviousBalance()).isEqualByComparingTo("100");
		assertThat(e.getNewBalance()).isEqualByComparingTo("600");
		assertThat(e.getReason()).isEqualTo("deposit bonus");
		assertThat(e.getPortfolio()).isSameAs(p);
	}

	@Test
	void addCapital_allowsNoReason() {
		Portfolio p = locked(account("60", "40"));

		service.addCapital(p, new BigDecimal("25"), "  ");

		assertThat(capturedEvent().getReason())
				.as("a blank reason is stored as null, not as whitespace")
				.isNull();
	}

	@Test
	void addCapital_rejectsZero() {
		Portfolio p = locked(account("60", "40"));
		assertThatThrownBy(() -> service.addCapital(p, BigDecimal.ZERO, null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("greater than zero");
		verify(events, never()).save(any());
	}

	@Test
	void addCapital_rejectsNegativeRatherThanInvertingTheDirection() {
		Portfolio p = locked(account("60", "40"));
		assertThatThrownBy(() -> service.addCapital(p, new BigDecimal("-100"), null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("greater than zero");
	}

	@Test
	void addCapital_rejectsNull() {
		Portfolio p = locked(account("60", "40"));
		assertThatThrownBy(() -> service.addCapital(p, null, null))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void addCapital_supportsEightDecimalPlaces() {
		Portfolio p = locked(account("60", "40"));

		Portfolio saved = service.addCapital(p, new BigDecimal("0.00000001"), null);

		assertThat(saved.getAvailableBalance()).isEqualByComparingTo("60.00000001");
		assertThat(saved.getAvailableBalance().scale()).isEqualTo(8);
	}

	// ── REDUCE ────────────────────────────────────────────────────

	@Test
	void reduceCapital_lowersAvailableAndTotal() {
		Portfolio p = locked(account("60", "40"));

		Portfolio saved = service.reduceCapital(p, new BigDecimal("20"), "withdraw");

		assertThat(saved.getAvailableBalance()).isEqualByComparingTo("40");
		assertThat(saved.getTotalBalance()).isEqualByComparingTo("80");
		assertThat(saved.getInvested())
				.as("an open position is never closed by a withdrawal")
				.isEqualByComparingTo("40");
	}

	@Test
	void reduceCapital_writesANegativeAmountSoTheLedgerSums() {
		Portfolio p = locked(account("60", "40"));

		service.reduceCapital(p, new BigDecimal("20"), null);

		PaperCapitalEvent e = capturedEvent();
		assertThat(e.getEventType()).isEqualTo(PaperCapitalEventType.REDUCE);
		assertThat(e.getAmount()).isEqualByComparingTo("-20");
		assertThat(e.getPreviousBalance()).isEqualByComparingTo("100");
		assertThat(e.getNewBalance()).isEqualByComparingTo("80");
	}

	@Test
	void reduceCapital_allowsWithdrawingTheWholeFreeBalance() {
		Portfolio p = locked(account("60", "40"));

		Portfolio saved = service.reduceCapital(p, new BigDecimal("60"), "all of it");

		assertThat(saved.getAvailableBalance()).isEqualByComparingTo("0");
		assertThat(saved.getTotalBalance()).isEqualByComparingTo("40");
	}

	/**
	 * The load-bearing rule: capital committed to an open position is not
	 * withdrawable, so equity must never be used as the bound.
	 */
	@Test
	void reduceCapital_rejectsAnAmountThatWouldStrandAnOpenPosition() {
		Portfolio p = locked(account("60", "40"));

		assertThatThrownBy(() -> service.reduceCapital(p, new BigDecimal("80"), null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("committed to open positions");
		assertThat(p.getAvailableBalance()).isEqualByComparingTo("60");
		verify(events, never()).save(any());
	}

	@Test
	void reduceCapital_rejectsAnAmountBeyondFreeCashOnAFlatAccount() {
		Portfolio p = locked(account("60", "0"));

		assertThatThrownBy(() -> service.reduceCapital(p, new BigDecimal("60.01"), null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("only 60 is available");
	}

	@Test
	void reduceCapital_rejectsZeroAndNegative() {
		Portfolio p = locked(account("60", "40"));
		assertThatThrownBy(() -> service.reduceCapital(p, BigDecimal.ZERO, null))
				.isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> service.reduceCapital(p, new BigDecimal("-1"), null))
				.isInstanceOf(BadRequestException.class);
	}

	// ── RESET ─────────────────────────────────────────────────────

	@Test
	void reset_auditsTheReturnToTheSeedBalance() {
		Portfolio p = locked(account("600", "0"));
		p.setTotalTrades(4);

		service.reset(p);

		PaperCapitalEvent e = capturedEvent();
		assertThat(e.getEventType()).isEqualTo(PaperCapitalEventType.RESET);
		assertThat(e.getPreviousBalance()).isEqualByComparingTo("600");
		assertThat(e.getNewBalance()).isEqualByComparingTo("100");
	}

	@Test
	void reset_preservesTheLedgerRatherThanClearingIt() {
		Portfolio p = locked(account("600", "0"));

		service.reset(p);

		// The capital history is an audit trail: a reset must add to it, never
		// erase it, otherwise the balance stops being explainable.
		verify(events, never()).deleteAll();
		verify(events, never()).delete(any(PaperCapitalEvent.class));
		verify(events, never()).deleteAllInBatch();
	}

	// ── Isolation ─────────────────────────────────────────────────

	@Test
	void capitalManagement_refusesALiveAccount() {
		Portfolio live = account("60", "40");
		live.setAccountType(AccountType.LIVE);
		locked(live);

		assertThatThrownBy(() -> service.addCapital(live, new BigDecimal("10"), null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("only available on paper accounts");
		assertThatThrownBy(() -> service.reduceCapital(live, new BigDecimal("10"), null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("only available on paper accounts");
		assertThat(live.getAvailableBalance()).isEqualByComparingTo("60");
		verify(events, never()).save(any());
	}

	@Test
	void capitalManagement_rejectsAMissingAccount() {
		Portfolio p = account("60", "40");
		when(portfolios.findByIdForUpdate(p.getId())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.addCapital(p, new BigDecimal("10"), null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("not found");
	}

	@Test
	void capitalManagement_rejectsANullAccount() {
		assertThatThrownBy(() -> service.addCapital(null, new BigDecimal("10"), null))
				.isInstanceOf(BadRequestException.class);
	}

	// ── Ledger ────────────────────────────────────────────────────

	@Test
	void capitalHistory_isBoundedAndNewestFirst() {
		Portfolio p = account("60", "40");
		when(events.findByPortfolioOrderByCreatedAtDesc(any(), any()))
				.thenReturn(List.of());

		service.capitalHistory(p, 10_000);

		var captor = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
		verify(events).findByPortfolioOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(p),
				captor.capture());
		assertThat(captor.getValue().getPageSize())
				.as("an unbounded ledger response would grow without limit")
				.isEqualTo(200);
	}

	@Test
	void capitalHistory_defaultsToFiftyEntries() {
		Portfolio p = account("60", "40");
		when(events.findByPortfolioOrderByCreatedAtDesc(any(), any()))
				.thenReturn(List.of());

		service.capitalHistory(p, 0);

		var captor = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
		verify(events).findByPortfolioOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(p),
				captor.capture());
		assertThat(captor.getValue().getPageSize()).isEqualTo(50);
	}
}

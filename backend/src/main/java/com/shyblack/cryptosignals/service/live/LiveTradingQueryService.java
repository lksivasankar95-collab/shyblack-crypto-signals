package com.shyblack.cryptosignals.service.live;

import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.repository.LiveOrderRepository;
import com.shyblack.cryptosignals.repository.LiveTradingAccountRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LiveTradingQueryService {

	private final LiveTradingAccountRepository accountRepository;
	private final LiveOrderRepository orderRepository;

	@Transactional(readOnly = true)
	public Optional<LiveTradingAccount> findAccount(User user) {
		return accountRepository.findFirstByUser(user);
	}

	@Transactional(readOnly = true)
	public List<LiveOrder> allOrders(User user) {
		return orderRepository.findByAccount_UserOrderByCreatedAtDesc(user);
	}

	@Transactional(readOnly = true)
	public List<LiveOrder> openOrders(User user) {
		Optional<LiveTradingAccount> account = accountRepository.findFirstByUser(user);
		if (account.isEmpty()) return List.of();
		return orderRepository.findByAccountAndStatusInOrderByCreatedAtDesc(
				account.get(),
				List.of(LiveOrderStatus.CREATED, LiveOrderStatus.SUBMITTING,
						LiveOrderStatus.SUBMITTED, LiveOrderStatus.ACKNOWLEDGED,
						LiveOrderStatus.PARTIALLY_FILLED, LiveOrderStatus.CANCEL_REQUESTED,
						LiveOrderStatus.UNKNOWN, LiveOrderStatus.RECONCILING));
	}

	@Transactional(readOnly = true)
	public List<LiveOrder> history(User user) {
		Optional<LiveTradingAccount> account = accountRepository.findFirstByUser(user);
		if (account.isEmpty()) return List.of();
		return orderRepository.findByAccountAndStatusInOrderByCreatedAtDesc(
				account.get(),
				List.of(LiveOrderStatus.FILLED, LiveOrderStatus.CANCELLED,
						LiveOrderStatus.REJECTED, LiveOrderStatus.EXPIRED,
						LiveOrderStatus.FAILED));
	}

	@Transactional(readOnly = true)
	public Optional<LiveOrder> findOwnedOrder(User user, UUID orderId) {
		return orderRepository.findById(orderId)
				.filter(o -> o.getAccount().getUser().getId().equals(user.getId()));
	}

	@Transactional(readOnly = true)
	public Aggregate performance(User user) {
		List<LiveOrder> closed = history(user);
		BigDecimal totalFees = BigDecimal.ZERO;
		int fills = 0;
		int rejections = 0;
		BigDecimal totalQuote = BigDecimal.ZERO;
		for (LiveOrder o : closed) {
			totalFees = totalFees.add(o.getFees() == null ? BigDecimal.ZERO : o.getFees());
			if (o.getStatus() == LiveOrderStatus.FILLED
					&& o.getPurpose() == LiveOrderPurpose.ENTRY) fills++;
			if (o.getStatus() == LiveOrderStatus.REJECTED) rejections++;
			totalQuote = totalQuote.add(o.getCumulativeQuoteQty() == null
					? BigDecimal.ZERO : o.getCumulativeQuoteQty());
		}
		return new Aggregate(closed.size(), fills, rejections, totalFees, totalQuote);
	}

	public record Aggregate(int totalOrders, int filledEntries, int rejections,
			BigDecimal totalFees, BigDecimal totalNotional) {}
}

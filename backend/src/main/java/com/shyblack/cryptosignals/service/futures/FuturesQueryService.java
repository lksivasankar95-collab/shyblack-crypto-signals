package com.shyblack.cryptosignals.service.futures;

import com.shyblack.cryptosignals.entity.FuturesOrder;
import com.shyblack.cryptosignals.entity.FuturesPosition;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionStatus;
import com.shyblack.cryptosignals.repository.FuturesOrderRepository;
import com.shyblack.cryptosignals.repository.FuturesPositionRepository;
import com.shyblack.cryptosignals.repository.FuturesTradingAccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FuturesQueryService {

	private final FuturesTradingAccountRepository accountRepository;
	private final FuturesOrderRepository orderRepository;
	private final FuturesPositionRepository positionRepository;

	@Transactional(readOnly = true)
	public Optional<FuturesTradingAccount> findAccount(User user) {
		return accountRepository.findFirstByUser(user);
	}

	@Transactional(readOnly = true)
	public List<FuturesOrder> openOrders(User user) {
		return accountRepository.findFirstByUser(user).map(a ->
				orderRepository.findByAccountAndStatusInOrderByCreatedAtDesc(a, List.of(
						FuturesOrderStatus.CREATED, FuturesOrderStatus.SUBMITTING,
						FuturesOrderStatus.SUBMITTED, FuturesOrderStatus.ACKNOWLEDGED,
						FuturesOrderStatus.PARTIALLY_FILLED, FuturesOrderStatus.CANCEL_REQUESTED,
						FuturesOrderStatus.UNKNOWN, FuturesOrderStatus.RECONCILING))
		).orElse(List.of());
	}

	@Transactional(readOnly = true)
	public List<FuturesOrder> history(User user) {
		return accountRepository.findFirstByUser(user).map(a ->
				orderRepository.findByAccountAndStatusInOrderByCreatedAtDesc(a, List.of(
						FuturesOrderStatus.FILLED, FuturesOrderStatus.CANCELLED,
						FuturesOrderStatus.REJECTED, FuturesOrderStatus.EXPIRED,
						FuturesOrderStatus.FAILED))
		).orElse(List.of());
	}

	@Transactional(readOnly = true)
	public List<FuturesPosition> openPositions(User user) {
		return accountRepository.findFirstByUser(user).map(a ->
				positionRepository.findByAccountAndStatusOrderByCreatedAtDesc(a, FuturesPositionStatus.OPEN)
		).orElse(List.of());
	}

	@Transactional(readOnly = true)
	public List<FuturesPosition> closedPositions(User user) {
		return accountRepository.findFirstByUser(user).map(a ->
				positionRepository.findByAccountAndStatusOrderByCreatedAtDesc(a, FuturesPositionStatus.CLOSED)
		).orElse(List.of());
	}

	@Transactional(readOnly = true)
	public Optional<FuturesOrder> findOwnedOrder(User user, UUID id) {
		return orderRepository.findById(id)
				.filter(o -> o.getAccount().getUser().getId().equals(user.getId()));
	}
}

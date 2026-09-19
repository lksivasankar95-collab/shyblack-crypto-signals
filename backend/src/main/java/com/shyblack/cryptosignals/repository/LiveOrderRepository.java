package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveOrderRepository extends JpaRepository<LiveOrder, UUID> {

	Optional<LiveOrder> findByAccountAndClientOrderId(LiveTradingAccount account, String clientOrderId);

	List<LiveOrder> findByAccountOrderByCreatedAtDesc(LiveTradingAccount account);

	List<LiveOrder> findByAccount_UserOrderByCreatedAtDesc(User user);

	List<LiveOrder> findByAccountAndStatusInOrderByCreatedAtDesc(
			LiveTradingAccount account, List<LiveOrderStatus> statuses);

	List<LiveOrder> findByAccountAndSymbolAndPurposeAndStatusIn(
			LiveTradingAccount account, String symbol,
			com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose purpose,
			List<LiveOrderStatus> statuses);

	List<LiveOrder> findByStatusIn(List<LiveOrderStatus> statuses);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from LiveOrder o where o.id = :id")
	Optional<LiveOrder> findByIdForUpdate(@Param("id") UUID id);
}

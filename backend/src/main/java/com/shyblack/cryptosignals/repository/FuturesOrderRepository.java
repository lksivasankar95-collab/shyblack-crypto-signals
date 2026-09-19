package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.FuturesOrder;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FuturesOrderRepository extends JpaRepository<FuturesOrder, UUID> {

	Optional<FuturesOrder> findByAccountAndClientOrderId(FuturesTradingAccount account, String clientOrderId);

	List<FuturesOrder> findByAccount_UserOrderByCreatedAtDesc(User user);

	List<FuturesOrder> findByAccountAndStatusInOrderByCreatedAtDesc(
			FuturesTradingAccount account, List<FuturesOrderStatus> statuses);

	List<FuturesOrder> findByAccountAndSymbolAndPositionSideAndPurposeAndStatusIn(
			FuturesTradingAccount account, String symbol, PositionSide positionSide,
			FuturesOrderPurpose purpose, List<FuturesOrderStatus> statuses);

	List<FuturesOrder> findByStatusIn(List<FuturesOrderStatus> statuses);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from FuturesOrder o where o.id = :id")
	Optional<FuturesOrder> findByIdForUpdate(@Param("id") UUID id);
}

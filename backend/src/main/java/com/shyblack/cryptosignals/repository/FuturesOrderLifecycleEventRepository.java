package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.FuturesOrder;
import com.shyblack.cryptosignals.entity.FuturesOrderLifecycleEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FuturesOrderLifecycleEventRepository extends JpaRepository<FuturesOrderLifecycleEvent, UUID> {

	List<FuturesOrderLifecycleEvent> findByOrderOrderByCreatedAtAsc(FuturesOrder order);
}

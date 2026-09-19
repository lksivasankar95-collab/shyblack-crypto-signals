package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.LiveOrderLifecycleEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveOrderLifecycleEventRepository extends JpaRepository<LiveOrderLifecycleEvent, UUID> {

	List<LiveOrderLifecycleEvent> findByOrderOrderByCreatedAtAsc(LiveOrder order);
}

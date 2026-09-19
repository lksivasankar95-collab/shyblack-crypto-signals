package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.PositionLifecycleEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionLifecycleEventRepository extends JpaRepository<PositionLifecycleEvent, UUID> {

	List<PositionLifecycleEvent> findByPositionOrderByCreatedAtAsc(Position position);
}

package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.SignalNfmContext;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalNfmContextRepository extends JpaRepository<SignalNfmContext, UUID> {

	Optional<SignalNfmContext> findBySignalId(UUID signalId);

	List<SignalNfmContext> findBySignalIdIn(Collection<UUID> signalIds);
}

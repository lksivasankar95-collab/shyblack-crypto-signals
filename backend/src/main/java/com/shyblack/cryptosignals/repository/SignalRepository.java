package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalRepository extends JpaRepository<Signal, UUID> {

	List<Signal> findByStatus(SignalStatus status);
}

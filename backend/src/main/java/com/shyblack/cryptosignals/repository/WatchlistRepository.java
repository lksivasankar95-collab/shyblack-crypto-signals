package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.Watchlist;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistRepository extends JpaRepository<Watchlist, UUID> {

	List<Watchlist> findByUser(User user);

	Optional<Watchlist> findByUserAndSymbol(User user, String symbol);
}

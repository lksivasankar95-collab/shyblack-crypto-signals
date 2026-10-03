package com.shyblack.cryptosignals.service.paper;

import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.repository.PositionRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves the stop-loss / take-profit of an OPEN PAPER position.
 *
 * <p>This deliberately does NOT implement its own exit logic. The paper engine
 * ({@code PaperTradingEngineService}) re-reads {@link Position#getStopLoss()} and
 * {@code getTakeProfit1..3()} on every tick, so persisting a validated level is
 * all that is needed for the existing engine to honour it — one execution
 * engine, one source of truth.</p>
 *
 * <p>Validation is side-aware and anchored on the entry price, because a level
 * on the wrong side of entry is unreachable: the engine would never trigger it
 * and the position would silently run unprotected.</p>
 */
@Service
@RequiredArgsConstructor
public class PaperPositionRiskService {

	private final PositionRepository positionRepository;

	/**
	 * Applies new protective levels to an owned, open, paper position.
	 *
	 * @param stopLoss    new stop, or null to leave the current stop alone
	 * @param takeProfit  new target, or null to leave the current target alone
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Position updateRisk(User user, java.util.UUID positionId,
			BigDecimal stopLoss, BigDecimal takeProfit) {

		Position owned = findOwned(user, positionId);

		if (owned.getStatus() != PositionStatus.OPEN) {
			throw new BadRequestException("Position is not open");
		}
		Portfolio portfolio = owned.getPortfolio();
		if (portfolio == null || portfolio.getAccountType() != AccountType.PAPER) {
			// Defence in depth: live exposure is never editable here.
			throw new BadRequestException(
					"Risk levels can only be edited on paper positions");
		}

		Position locked = positionRepository.findByIdForUpdate(positionId)
				.orElseThrow(() -> new BadRequestException("Position not found: " + positionId));

		BigDecimal entry = requireEntry(locked);
		PositionSide side = locked.getSide();
		boolean longSide = side == PositionSide.LONG;

		BigDecimal nextStop = stopLoss == null ? locked.getStopLoss() : stopLoss;
		BigDecimal nextTarget = takeProfit == null ? locked.getTakeProfit1() : takeProfit;

		validateStop(nextStop, entry, longSide);
		validateTarget(nextTarget, entry, longSide);

		locked.setStopLoss(nextStop);
		locked.setTakeProfit1(nextTarget);
		return positionRepository.save(locked);
	}

	private Position findOwned(User user, java.util.UUID positionId) {
		if (user == null || positionId == null) {
			throw new BadRequestException("Position not found");
		}
		List<Position> candidates = positionRepository
				.findByPortfolio_UserOrderByCreatedAtDesc(user);
		return candidates.stream()
				.filter(p -> positionId.equals(p.getId()))
				.findFirst()
				.orElseThrow(() -> new BadRequestException("Position not found: " + positionId));
	}

	private static BigDecimal requireEntry(Position position) {
		BigDecimal entry = position.getEntryPrice();
		if (entry == null || entry.signum() <= 0) {
			throw new BadRequestException(
					"Position has no usable entry price; protective levels cannot be validated");
		}
		return entry;
	}

	private static void validateStop(BigDecimal stop, BigDecimal entry, boolean longSide) {
		if (stop == null) return;
		if (stop.signum() <= 0) {
			throw new BadRequestException("stopLoss must be greater than zero");
		}
		if (longSide && stop.compareTo(entry) >= 0) {
			throw new BadRequestException(
					"For a LONG position stopLoss must be below the entry price " + entry);
		}
		if (!longSide && stop.compareTo(entry) <= 0) {
			throw new BadRequestException(
					"For a SHORT position stopLoss must be above the entry price " + entry);
		}
	}

	private static void validateTarget(BigDecimal target, BigDecimal entry, boolean longSide) {
		if (target == null) return;
		if (target.signum() <= 0) {
			throw new BadRequestException("takeProfit must be greater than zero");
		}
		if (longSide && target.compareTo(entry) <= 0) {
			throw new BadRequestException(
					"For a LONG position takeProfit must be above the entry price " + entry);
		}
		if (!longSide && target.compareTo(entry) >= 0) {
			throw new BadRequestException(
					"For a SHORT position takeProfit must be below the entry price " + entry);
		}
	}
}

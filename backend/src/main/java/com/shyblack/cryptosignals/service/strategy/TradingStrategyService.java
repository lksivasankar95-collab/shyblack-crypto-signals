package com.shyblack.cryptosignals.service.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.dto.strategy.*;
import com.shyblack.cryptosignals.entity.TradingStrategy;
import com.shyblack.cryptosignals.entity.UserStrategySelection;
import com.shyblack.cryptosignals.entity.enums.StrategyStatus;
import com.shyblack.cryptosignals.entity.enums.StrategyType;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.NotFoundException;
import com.shyblack.cryptosignals.repository.TradingStrategyRepository;
import com.shyblack.cryptosignals.repository.UserStrategySelectionRepository;
import com.shyblack.cryptosignals.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TradingStrategyService {

    private final TradingStrategyRepository strategyRepository;
    private final UserStrategySelectionRepository selectionRepository;
    private final StrategyResolver resolver;
    private final ObjectMapper objectMapper;

    public List<TradingStrategyResponse> listForMode(UserPrincipal principal, TradingMode mode) {
        List<TradingStrategy> result = new ArrayList<>();
        result.addAll(strategyRepository.findByTradingModeAndStrategyType(mode, StrategyType.SYSTEM));
        result.addAll(strategyRepository.findByTradingModeAndOwnerIdAndStrategyType(mode, principal.id(), StrategyType.USER));
        return result.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TradingStrategyResponse create(UserPrincipal principal, CreateStrategyRequest req) {
        if (req.tradingMode() == TradingMode.OPTIONS) {
            throw new BadRequestException("OPTIONS mode is not supported for strategy creation");
        }
        TradingStrategy s = new TradingStrategy();
        s.setName(req.name());
        s.setDescription(req.description());
        s.setTradingMode(req.tradingMode());
        s.setStrategyType(StrategyType.USER);
        s.setOwnerId(principal.id());
        s.setVersion(1);
        s.setStatus(StrategyStatus.ACTIVE);
        s.setDeletable(true);
        s.setEditable(true);
        StrategyConfigDto config = req.config() != null ? req.config()
                : (req.tradingMode() == TradingMode.FUTURES
                        ? StrategyConfigDto.futuresDefaults()
                        : StrategyConfigDto.spotDefaults());
        s.setConfigJson(serialize(config));
        return toResponse(strategyRepository.save(s));
    }

    public TradingStrategyResponse getById(UserPrincipal principal, UUID id) {
        return toResponse(findOwned(principal, id));
    }

    @Transactional
    public TradingStrategyResponse update(UserPrincipal principal, UUID id, UpdateStrategyRequest req) {
        TradingStrategy s = findOwned(principal, id);
        if (!s.isEditable()) throw new BadRequestException("System strategies cannot be edited");
        if (req.name() != null) s.setName(req.name());
        if (req.description() != null) s.setDescription(req.description());
        if (req.status() != null) s.setStatus(req.status());
        if (req.config() != null) {
            s.setConfigJson(serialize(req.config()));
            s.setVersion(s.getVersion() + 1); // bump version on config change
        }
        return toResponse(strategyRepository.save(s));
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID id) {
        TradingStrategy s = findOwned(principal, id);
        if (!s.isDeletable()) throw new BadRequestException("System strategies cannot be deleted");
        strategyRepository.delete(s);
    }

    @Transactional
    public ActiveStrategyResponse setActive(UserPrincipal principal, SetActiveStrategyRequest req) {
        TradingStrategy strategy = strategyRepository.findById(req.strategyId())
                .orElseThrow(() -> new NotFoundException("Strategy not found"));
        // Must be system or owned by this user
        if (strategy.getStrategyType() == StrategyType.USER
                && !principal.id().equals(strategy.getOwnerId())) {
            throw new NotFoundException("Strategy not found");
        }
        if (strategy.getTradingMode() != req.tradingMode()) {
            throw new BadRequestException("Strategy mode does not match requested mode");
        }
        Optional<UserStrategySelection> existing =
                selectionRepository.findByUserIdAndTradingMode(principal.id(), req.tradingMode());
        UserStrategySelection selection = existing.orElseGet(UserStrategySelection::new);
        selection.setUserId(principal.id());
        selection.setTradingMode(req.tradingMode());
        selection.setStrategy(strategy);
        selectionRepository.save(selection);
        return new ActiveStrategyResponse(req.tradingMode(), strategy.getId(),
                strategy.getName(), strategy.getVersion(), true);
    }

    public ActiveStrategyResponse getActive(UserPrincipal principal, TradingMode mode) {
        Optional<UserStrategySelection> sel =
                selectionRepository.findByUserIdAndTradingMode(principal.id(), mode);
        if (sel.isPresent()) {
            TradingStrategy s = sel.get().getStrategy();
            return new ActiveStrategyResponse(mode, s.getId(), s.getName(), s.getVersion(),
                    s.getStatus() == StrategyStatus.ACTIVE);
        }
        // Fall back to system default
        return resolver.resolveActive(mode)
                .map(s -> new ActiveStrategyResponse(mode, s.getId(), s.getName(), s.getVersion(), true))
                .orElseGet(() -> new ActiveStrategyResponse(mode, null, null, 0, false));
    }

    private TradingStrategy findOwned(UserPrincipal principal, UUID id) {
        TradingStrategy s = strategyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Strategy not found"));
        // Allow access to SYSTEM strategies for read; for write ops, caller checks isEditable/isDeletable
        if (s.getStrategyType() == StrategyType.USER && !principal.id().equals(s.getOwnerId())) {
            throw new NotFoundException("Strategy not found");
        }
        return s;
    }

    private TradingStrategyResponse toResponse(TradingStrategy s) {
        StrategyConfigDto config = null;
        if (s.getConfigJson() != null) {
            try { config = objectMapper.readValue(s.getConfigJson(), StrategyConfigDto.class); }
            catch (Exception ignored) {}
        }
        return new TradingStrategyResponse(s.getId(), s.getName(), s.getDescription(),
                s.getTradingMode(), s.getStrategyType(), s.getVersion(), s.getStatus(),
                s.isDeletable(), s.isEditable(), config, s.getCreatedAt(), s.getUpdatedAt());
    }

    private String serialize(StrategyConfigDto config) {
        try { return objectMapper.writeValueAsString(config); }
        catch (Exception ex) { throw new RuntimeException("Config serialization failed", ex); }
    }
}

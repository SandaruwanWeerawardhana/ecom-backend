package org.psint.beyosclothing.modules.pos.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.entity.PosCartEntity;
import org.psint.beyosclothing.modules.pos.entity.PosTerminalEntity;
import org.psint.beyosclothing.modules.pos.repository.PosCartRepository;
import org.psint.beyosclothing.modules.pos.repository.PosTerminalRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PosCartCacheWarmer {

    private final PosCartRepository cartRepository;
    private final PosTerminalRepository terminalRepository;
    private final PosCartServiceImpl cartService;

    @PostConstruct
    public void warmUpActiveCartsCache() {
        try {
            log.info("Starting POS cart cache warm-up on application startup...");

            List<PosCartEntity> activeCarts = cartRepository.findAllActiveCartsForWarmup();

            if (activeCarts.isEmpty()) {
                log.info("No active carts found to warm up cache");
                return;
            }

            int warmedCount = 0;
            for (PosCartEntity cart : activeCarts) {
                try {
                    PosTerminalEntity terminal = terminalRepository.findById(cart.getTerminalId()).orElse(null);
                    if (terminal == null) {
                        log.warn("Terminal not found for cart {}, skipping cache warm-up", cart.getUuid());
                        continue;
                    }

                    // Use the service's mapping to convert to response and cache it
                    cartService.warmupCacheForCart(terminal.getUuid(), cart);
                    warmedCount++;

                } catch (Exception e) {
                    log.warn("Failed to warm up cache for cart {}: {}", cart.getUuid(), e.getMessage());
                }
            }

            log.info("POS cart cache warm-up completed. Warmed {} out of {} active carts",
                    warmedCount, activeCarts.size());

        } catch (Exception e) {
            log.error("Failed to warm up POS cart cache on startup: {}", e.getMessage(), e);
        }
    }
}

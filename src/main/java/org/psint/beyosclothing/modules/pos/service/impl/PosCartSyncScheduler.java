package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.dto.response.PosCartResponse;
import org.psint.beyosclothing.modules.pos.entity.PosCartEntity;
import org.psint.beyosclothing.modules.pos.repository.PosCartRepository;
import org.psint.beyosclothing.modules.pos.repository.PosCartItemRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class PosCartSyncScheduler {

    private final PosCartCacheService cacheService;
    private final PosCartRepository cartRepository;
    private final PosCartItemRepository itemRepository;

    private static final int OPS_THRESHOLD = 5;
    private static final Duration SYNC_INTERVAL = Duration.ofSeconds(60);

    @Scheduled(fixedDelay = 60000)
    public void syncDirtyCarts() {
        Set<String> metaKeys = cacheService.getActiveCartMetaKeys();
        for (String metaKey : metaKeys) {
            try {
                if (!cacheService.metaKeyExists(metaKey)) {
                    cacheService.removeActiveKey(metaKey);
                    continue;
                }
                String terminalUuid = metaKey.replace("pos:cart:terminal:", "");
                int ops = cacheService.getOpsCount(terminalUuid);
                Instant lastSynced = cacheService.getLastSyncedAt(terminalUuid).orElse(Instant.EPOCH);
                boolean timeExceeded = Instant.now().minus(SYNC_INTERVAL).isAfter(lastSynced);
                if (ops >= OPS_THRESHOLD || timeExceeded) {
                    persistCacheToDb(terminalUuid);
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate sync for metaKey {}: {}", metaKey, e.getMessage());
            }
        }
    }

    @Transactional
    protected void persistCacheToDb(String terminalUuid) {
        try {
            PosCartResponse resp = cacheService.loadCartFromCache(terminalUuid).orElse(null);
            if (resp == null) return;
            PosCartEntity cart = cartRepository.findByUuidAndIsActiveTrue(resp.getUuid()).orElse(null);
            if (cart == null) return;
            // Update cart totals and items
            cart.setSubtotal(java.math.BigDecimal.valueOf(resp.getSubtotal()));
            cart.setTaxAmount(java.math.BigDecimal.valueOf(resp.getTaxAmount()));
            cart.setTaxPercentage(java.math.BigDecimal.valueOf(resp.getTaxPercentage()));
            cart.setDiscountAmount(java.math.BigDecimal.valueOf(resp.getDiscountAmount()));
            cart.setTotal(java.math.BigDecimal.valueOf(resp.getTotal()));
            cartRepository.save(cart);
            // Note: items are authoritative in DB via itemRepository operations done during writes.
            cacheService.markSynced(terminalUuid);
            log.debug("Persisted cached cart to DB for terminal {}", terminalUuid);
        } catch (Exception e) {
            log.warn("Failed to persist cached cart {} to DB: {}", terminalUuid, e.getMessage());
        }
    }
}

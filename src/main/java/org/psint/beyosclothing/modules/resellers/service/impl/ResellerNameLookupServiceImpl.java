package org.psint.beyosclothing.modules.resellers.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.entity.Reseller;
import org.psint.beyosclothing.modules.resellers.repository.ResellerRepository;
import org.psint.beyosclothing.modules.resellers.service.ResellerNameLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves reseller display names with direct database queries against the reseller datasource.
 * Runs in its own transaction (REQUIRES_NEW on the reseller transaction manager) so callers bound
 * to other datasources, such as the admin order service, can invoke it safely.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(value = "resellerTransactionManager", readOnly = true, propagation = Propagation.REQUIRES_NEW)
public class ResellerNameLookupServiceImpl implements ResellerNameLookupService {

    private final ResellerRepository resellerRepository;

    @Override
    public String getResellerName(Long resellerId) {
        if (resellerId == null) {
            return null;
        }
        return resellerRepository.findById(resellerId)
                .map(Reseller::getFullName)
                .orElseGet(() -> {
                    log.warn("No reseller found for resellerId={}", resellerId);
                    return null;
                });
    }

    @Override
    public Map<Long, String> getResellerNames(Collection<Long> resellerIds) {
        if (resellerIds == null || resellerIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctIds = resellerIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (Reseller reseller : resellerRepository.findAllById(distinctIds)) {
            names.put(reseller.getId(), reseller.getFullName());
        }
        return names;
    }
}

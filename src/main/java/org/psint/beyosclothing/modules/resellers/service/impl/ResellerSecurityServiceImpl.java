package org.psint.beyosclothing.modules.resellers.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.entity.Reseller;
import org.psint.beyosclothing.modules.resellers.entity.ResellerStatus;
import org.psint.beyosclothing.modules.resellers.service.ResellerSecurityService;
import org.psint.beyosclothing.modules.resellers.service.ResellerService;
import org.springframework.stereotype.Service;

/**
 * Implementation of Reseller Security Service
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResellerSecurityServiceImpl implements ResellerSecurityService {

    private final ResellerService resellerService;

    @Override
    public Reseller validateResellerAccess(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        Reseller reseller = resellerService.getResellerByUserId(userId);

        if (reseller == null) {
            throw new IllegalArgumentException("Reseller not found for user ID: " + userId);
        }

        if (!reseller.getIsActive()) {
            throw new IllegalStateException("Reseller account is inactive");
        }

        return reseller;
    }

    @Override
    public void checkApprovedStatus(Long userId) {
        Reseller reseller = validateResellerAccess(userId);

        if (reseller.getStatus() != ResellerStatus.APPROVED) {
            throw new IllegalStateException(
                "This action requires an approved reseller account. Current status: " + reseller.getStatus()
            );
        }
    }

    @Override
    public void checkResourceOwnership(Long userId, Long resourceResellerId) {
        Reseller reseller = validateResellerAccess(userId);

        if (!reseller.getId().equals(resourceResellerId)) {
            log.warn("Access denied: User {} attempted to access resource owned by reseller {}",
                     userId, resourceResellerId);
            throw new IllegalArgumentException("You don't have permission to access this resource");
        }
    }
}


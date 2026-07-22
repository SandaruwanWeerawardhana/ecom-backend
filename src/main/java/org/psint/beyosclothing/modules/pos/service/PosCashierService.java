package org.psint.beyosclothing.modules.pos.service;

import org.psint.beyosclothing.modules.pos.entity.PosCashierEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface PosCashierService {
    PosCashierEntity createCashier(String name, Long userId, String pinCode);

    PosCashierEntity updateCashier(String uuid, String name, Long userId, String pinCode, Boolean isActive);

    void deactivateCashier(String uuid);

    void updateCashierPin(String uuid, String pinCode);

    PosCashierEntity save(PosCashierEntity entity);

    Optional<PosCashierEntity> findByUuid(String uuid);

    Page<PosCashierEntity> findAll(Pageable pageable);
}

package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.pos.entity.PosCashierEntity;
import org.psint.beyosclothing.modules.pos.repository.PosCashierRepository;
import org.psint.beyosclothing.modules.pos.service.PosCashierService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PosCashierServiceImpl implements PosCashierService {

    private final PosCashierRepository cashierRepository;

    @Override
    @Transactional
    public PosCashierEntity createCashier(String name, Long userId, String pinCode) {
        PosCashierEntity entity = PosCashierEntity.builder()
                .name(name)
                .userId(userId)
                .isActive(true)
                .build();

        if (pinCode != null && !pinCode.isBlank()) {
            entity.setPinCode(pinCode);
        }

        return cashierRepository.save(entity);
    }

    @Override
    @Transactional
    public PosCashierEntity updateCashier(String uuid, String name, Long userId, String pinCode, Boolean isActive) {
        PosCashierEntity entity = cashierRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Cashier", "uuid", uuid));

        if (name != null) {
            entity.setName(name);
        }
        if (userId != null) {
            entity.setUserId(userId);
        }
        if (isActive != null) {
            entity.setIsActive(isActive);
        }
        if (pinCode != null && !pinCode.isBlank()) {
            entity.setPinCode(pinCode);
        }

        return cashierRepository.save(entity);
    }

    @Override
    @Transactional
    public void deactivateCashier(String uuid) {
        PosCashierEntity entity = cashierRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Cashier", "uuid", uuid));
        entity.setIsActive(false);
        cashierRepository.save(entity);
    }

    @Override
    @Transactional
    public void updateCashierPin(String uuid, String pinCode) {
        PosCashierEntity entity = cashierRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Cashier", "uuid", uuid));
        entity.setPinCode(pinCode);
        cashierRepository.save(entity);
    }

    @Override
    @Transactional
    public PosCashierEntity save(PosCashierEntity entity) {
        return cashierRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PosCashierEntity> findByUuid(String uuid) {
        return cashierRepository.findByUuid(uuid);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PosCashierEntity> findAll(Pageable pageable) {
        return cashierRepository.findAll(pageable);
    }
}

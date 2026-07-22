package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.entity.PosTerminalEntity;
import org.psint.beyosclothing.modules.pos.repository.PosTerminalRepository;
import org.psint.beyosclothing.modules.pos.service.PosTerminalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosTerminalServiceImpl implements PosTerminalService {

    private final PosTerminalRepository terminalRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Optional<PosTerminalEntity> findByCode(String code) {
        return terminalRepository.findByCode(code);
    }

    @Override
    @Transactional
    public PosTerminalEntity save(PosTerminalEntity entity) {
        if (entity == null) throw new IllegalArgumentException("entity is null");
        String rawCode = entity.getCode();
        if (rawCode == null) {
            throw new IllegalArgumentException("code must be provided");
        }

        // Only encode when creating a new terminal or when the code is still raw.
        // Existing persisted terminals already store an encoded value.
        if (entity.getId() == null) {
            entity.setCode(passwordEncoder.encode(rawCode));
        }

        return terminalRepository.save(entity);
    }

    @Override
    public Optional<PosTerminalEntity> findByUuid(String uuid) {
        return terminalRepository.findByUuid(uuid);
    }

    @Override
    public Page<PosTerminalEntity> findByIsActiveTrue(Pageable pageable) {

        List<PosTerminalEntity> content = terminalRepository.findByIsActiveTrue(pageable);
        long total = terminalRepository.countByIsActiveTrue();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Optional<PosTerminalEntity> validateByTerminal(String uuid, String code) {
        // Load terminal by UUID
        if (uuid == null || uuid.isBlank()) {
            return Optional.empty();
        }

        Optional<PosTerminalEntity> opt = terminalRepository.findByUuid(uuid);
        if (opt.isEmpty()) {
            return Optional.empty();
        }

        PosTerminalEntity entity = opt.get();

        if (entity.getIsActive() == null || !entity.getIsActive()) {
            return Optional.empty();
        }

        if (code == null) {
            return Optional.empty();
        }

        String stored = entity.getCode();
        if (stored == null) {
            return Optional.empty();
        }

        boolean matches = false;
        try {
            matches = passwordEncoder != null && passwordEncoder.matches(code, stored);
        } catch (Exception ex) {
            log.warn("Error validating POS terminal code for UUID {}: {}", uuid, ex.getMessage());
        }

        if (!matches && !code.equals(stored)) {
                return Optional.empty();
            }


        return Optional.of(entity);
    }

    @Override
    public List<PosTerminalEntity> getAllisActive() {

            return terminalRepository.findAll()
                    .stream()
                    .filter(t -> Boolean.TRUE.equals(t.getIsActive())).toList();
    }

    @Override
    @Transactional
    public Optional<PosTerminalEntity> updateTerminal(String uuid, String name, String code, String location, Boolean isActive) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("uuid must be provided");
        }

        Optional<PosTerminalEntity> opt = terminalRepository.findByUuid(uuid);
        if (opt.isEmpty()) {
            return Optional.empty();
        }

        PosTerminalEntity entity = opt.get();

        if (name != null) {
            entity.setName(name);
        }
        if (code != null) {
            entity.setCode(passwordEncoder.encode(code));
        }
        if (location != null) {
            entity.setLocation(location);
        }
        if (isActive != null) {
            entity.setIsActive(isActive);
        }

        return Optional.of(terminalRepository.save(entity));
    }
}

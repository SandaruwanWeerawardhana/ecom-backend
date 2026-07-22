package org.psint.beyosclothing.modules.pos.service;

import org.psint.beyosclothing.modules.pos.entity.PosTerminalEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface PosTerminalService {
    Optional<PosTerminalEntity> findByCode(String code);

    PosTerminalEntity save(PosTerminalEntity entity);

    Optional<PosTerminalEntity> findByUuid(String uuid);

    Page<PosTerminalEntity> findByIsActiveTrue(Pageable pageable);

    Optional<PosTerminalEntity> validateByTerminal(String uuid, String code);

    List<PosTerminalEntity> getAllisActive();

    Optional<PosTerminalEntity> updateTerminal(String uuid, String name, String code, String location, Boolean isActive);
}

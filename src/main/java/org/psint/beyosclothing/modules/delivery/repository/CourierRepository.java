package org.psint.beyosclothing.modules.delivery.repository;

import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourierRepository extends JpaRepository<Courier, Long> {

    Optional<Courier> findByUuid(String uuid);

    Optional<Courier> findByCode(String code);

    Optional<Courier> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    List<Courier> findAllByIsActiveTrue();

    Optional<Courier> findByIsActiveTrue();

    Optional<Courier> findByUuidAndIsActiveTrue(String uuid);

    boolean existsByCodeAndUuidNot(String code, String uuid);

    boolean existsByNameAndUuidNot(String name, String uuid);
}

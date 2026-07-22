package org.psint.beyosclothing.modules.admin.repository;

import org.psint.beyosclothing.modules.admin.entity.AdminEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Admin Repository
 * Database: beyos_admin_db
 */
@Repository
public interface AdminRepository extends JpaRepository<AdminEntity, Long> {

    Optional<AdminEntity> findByUserId(Long userId);

    Optional<AdminEntity> findByUuid(String uuid);

    Optional<AdminEntity> findByEmail(String email);

    boolean existsByUserId(Long userId);

    boolean existsByEmail(String email);

    @Query("SELECT a FROM AdminEntity a WHERE a.userId = :userId AND a.isActive = true")
    Optional<AdminEntity> findActiveAdminWithRole(@Param("userId") Long userId);

    @Query("SELECT a FROM AdminEntity a WHERE a.id = :id AND a.isActive = true")
    Optional<AdminEntity> findActiveAdminById(@Param("id") Long id);

    Page<AdminEntity> findByUserIdIn(List<Long> userIds, Pageable pageable);

    List<AdminEntity> findByUserIdIn(List<Long> userIds);
}

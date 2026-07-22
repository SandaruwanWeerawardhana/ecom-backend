package org.psint.beyosclothing.modules.admin.repository;

import org.psint.beyosclothing.modules.admin.entity.AdminActivityLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin Activity Log Repository
 * Database: beyos_admin_db
 */
@Repository
public interface AdminActivityLogRepository extends JpaRepository<AdminActivityLogEntity, Long> {

    @Query("SELECT a FROM AdminActivityLogEntity a WHERE a.admin.id = :adminId ORDER BY a.createdAt DESC")
    List<AdminActivityLogEntity> findByAdminId(@Param("adminId") Long adminId);

    @Query("SELECT a FROM AdminActivityLogEntity a WHERE a.module = :module ORDER BY a.createdAt DESC")
    List<AdminActivityLogEntity> findByModule(@Param("module") String module);

    @Query("SELECT a FROM AdminActivityLogEntity a WHERE a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    List<AdminActivityLogEntity> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                                   @Param("endDate") LocalDateTime endDate);
}


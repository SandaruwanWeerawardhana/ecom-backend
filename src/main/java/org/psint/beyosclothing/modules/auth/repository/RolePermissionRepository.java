package org.psint.beyosclothing.modules.auth.repository;

import org.psint.beyosclothing.modules.auth.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Role Permission Repository
 * Database: beyos_auth_db
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    @Query("SELECT rp FROM RolePermission rp JOIN FETCH rp.permission WHERE rp.userRole.id = :roleId AND rp.isActive = true")
    List<RolePermission> findActivePermissionsByRoleId(@Param("roleId") Long roleId);

    @Query("SELECT rp FROM RolePermission rp WHERE rp.userRole.id = :roleId AND rp.permission.id = :permissionId")
    RolePermission findByRoleIdAndPermissionId(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Query("SELECT rp FROM RolePermission rp JOIN FETCH rp.permission WHERE rp.userRole.id = :roleId")
    List<RolePermission> findByRoleId(@Param("roleId") Long roleId);

    @Query("SELECT COUNT(rp) > 0 FROM RolePermission rp WHERE rp.userRole.id = :roleId AND rp.permission.id = :permissionId")
    boolean existsByRoleAndPermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.userRole.id = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);
}

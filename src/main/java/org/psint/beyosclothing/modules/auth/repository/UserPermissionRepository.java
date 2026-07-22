package org.psint.beyosclothing.modules.auth.repository;

import org.psint.beyosclothing.modules.auth.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * User Permission Repository
 * Database: beyos_auth_db
 */
@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    Optional<UserPermission> findByPermissionName(String permissionName);

    boolean existsByPermissionName(String permissionName);

    Optional<UserPermission> findByPermissionCode(String permissionCode);

    boolean existsByPermissionCode(String permissionCode);

    @Query("SELECT p FROM UserPermission p WHERE p.parent IS NULL AND p.isActive = true ORDER BY p.displayOrder")
    List<UserPermission> findAllParentPermissions();

    @Query("SELECT p FROM UserPermission p WHERE p.parent.id = :parentId AND p.isActive = true ORDER BY p.displayOrder")
    List<UserPermission> findSubPermissionsByParentId(@Param("parentId") Long parentId);

    @Query("SELECT p FROM UserPermission p WHERE p.module = :module AND p.isActive = true ORDER BY p.displayOrder")
    List<UserPermission> findByModule(@Param("module") String module);
}

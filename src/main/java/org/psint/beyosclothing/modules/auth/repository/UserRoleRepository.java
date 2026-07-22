package org.psint.beyosclothing.modules.auth.repository;

import org.psint.beyosclothing.modules.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * User Role Repository
 * Database: beyos_auth_db
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    Optional<UserRole> findByRoleName(String roleName);

    Optional<UserRole> findByToken(String token);

    boolean existsByRoleName(String roleName);

    Optional<UserRole> findByRoleCode(String roleCode);

    boolean existsByRoleCode(String roleCode);

    @Query("SELECT ur FROM UserRole ur WHERE ur.userType = :userType AND ur.isActive = true")
    List<UserRole> findByUserType(@Param("userType") String userType);

    @Query("SELECT ur FROM UserRole ur WHERE ur.userType = 'ADMIN' AND ur.isActive = true")
    List<UserRole> findAllAdminRoles();
}

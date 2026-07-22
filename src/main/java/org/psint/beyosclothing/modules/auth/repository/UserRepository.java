package org.psint.beyosclothing.modules.auth.repository;

import org.psint.beyosclothing.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * User Repository
 * Database: beyos_auth_db
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndIsActiveTrue(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email); // ✅ NEW: Check if email exists

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.email = :email AND u.isActive = true")
    boolean existsByActiveEmail(@Param("email") String email); // Check if active email exists

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.isActive = true")
    Optional<User> findByEmail(@Param("email") String email); // Find active user by email

    List<User> findAllByEmailIgnoreCaseAndUserTypeIgnoreCaseAndIsActiveTrue(String email, String userType);

    @Query("SELECT u FROM User u WHERE u.id = :userId AND u.isActive = true")
    Optional<User> findActiveUserById(@Param("userId") Long userId);

    List<User> findAllByUserTypeIgnoreCaseAndIsActiveTrue(String userType);

    List<User> findByUserRoleId(Long userRoleId);
}

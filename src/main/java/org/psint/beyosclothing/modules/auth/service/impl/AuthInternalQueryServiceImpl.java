package org.psint.beyosclothing.modules.auth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.service.AuthQueryService;
import org.psint.beyosclothing.modules.admin.dto.response.RoleInfoDTO;
import org.psint.beyosclothing.modules.admin.dto.response.UserInfoDTO;
import org.psint.beyosclothing.modules.auth.entity.User;
import org.psint.beyosclothing.modules.auth.entity.UserRole;
import org.psint.beyosclothing.modules.auth.repository.RolePermissionRepository;
import org.psint.beyosclothing.modules.auth.repository.UserRepository;
import org.psint.beyosclothing.modules.auth.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Auth Internal Query Service Implementation
 * ✅ MICROSERVICE-READY: Implements common interface, no cross-module dependencies
 * This service provides read-only access to Auth data for other modules
 * In microservice architecture, this would be exposed as REST endpoints
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthInternalQueryServiceImpl implements AuthQueryService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Transactional(value = "authTransactionManager", readOnly = true)
    public boolean emailExists(String email) {
        try {
            return userRepository.existsByActiveEmail(email);
        } catch (Exception e) {
            log.error("Error checking if email exists: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    @Transactional(value = "authTransactionManager", readOnly = true)
    public Optional<UserInfoDTO> getUserByEmail(String email) {
        try {
            return userRepository.findByEmail(email)
                    .map(this::mapToUserInfoDTO);
        } catch (Exception e) {
            log.error("Error fetching user by email: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    @Transactional(value = "authTransactionManager", readOnly = true)
    public Optional<UserInfoDTO> getUserById(Long userId) {
        try {
            return userRepository.findById(userId)
                    .map(this::mapToUserInfoDTO);
        } catch (Exception e) {
            log.error("Error fetching user by ID: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    @Transactional(value = "authTransactionManager", readOnly = true)
    public Optional<RoleInfoDTO> getRoleByCode(String roleCode) {
        try {
            return userRoleRepository.findByRoleCode(roleCode)
                    .map(this::mapToRoleInfoDTO);
        } catch (Exception e) {
            log.error("Error fetching role by code: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    @Transactional(value = "authTransactionManager", readOnly = true)
    public Optional<RoleInfoDTO> getRoleById(Long roleId) {
        try {
            return userRoleRepository.findById(roleId)
                    .map(this::mapToRoleInfoDTO);
        } catch (Exception e) {
            log.error("Error fetching role by ID: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    @Transactional(value = "authTransactionManager", readOnly = true)
    public boolean hasPermission(Long userId, String permissionCode) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getUserRoleId() == null) {
                return false;
            }

            return rolePermissionRepository.findActivePermissionsByRoleId(user.getUserRoleId())
                    .stream()
                    .anyMatch(rp -> rp.getPermission().getPermissionCode().equals(permissionCode));
        } catch (Exception e) {
            log.error("Error checking permission: {}", e.getMessage(), e);
            return false;
        }
    }

    private UserInfoDTO mapToUserInfoDTO(User user) {
        return UserInfoDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .userType(user.getUserType())
                .userRoleId(user.getUserRoleId())
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .build();
    }

    private RoleInfoDTO mapToRoleInfoDTO(UserRole role) {
        return RoleInfoDTO.builder()
                .roleId(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .userType(role.getUserType())
                .build();
    }
}

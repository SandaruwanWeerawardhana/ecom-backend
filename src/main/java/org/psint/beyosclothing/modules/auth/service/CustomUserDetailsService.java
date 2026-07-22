package org.psint.beyosclothing.modules.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.auth.entity.RolePermission;
import org.psint.beyosclothing.modules.auth.entity.User;
import org.psint.beyosclothing.modules.auth.repository.RolePermissionRepository;
import org.psint.beyosclothing.modules.auth.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Custom UserDetailsService Implementation
 * Loads user from Auth database for authentication
 * ✅ UPDATED: Loads permissions for admin users
 */
@Service("customUserDetailsService")
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Transactional(value = "authTransactionManager", readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading user by email: {}", email);

        // ✅ Load by email instead of username
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        // Check if user is active
        if (!user.getIsActive()) {
            throw new UsernameNotFoundException("User account is disabled: " + email);
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(), // ✅ Use email for Spring Security username
                user.getPassword(),
                user.getIsActive() && !user.getAccountLocked(),
                true, // accountNonExpired
                true, // credentialsNonExpired
                !user.getAccountLocked(), // accountNonLocked
                getAuthorities(user)
        );
    }

    /**
     * Get authorities for user
     * For ADMIN users with userRoleId, load all permissions
     * For CUSTOMER/RESELLER, just use userType
     */
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // Add user type as authority with ROLE_ prefix (required for hasRole() to work)
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getUserType()));
        log.debug("Added user type authority: ROLE_{}", user.getUserType());

        // If user is ADMIN with assigned role, load permissions
        if ("ADMIN".equals(user.getUserType()) && user.getUserRoleId() != null) {
            log.debug("Loading permissions for ADMIN user with roleId: {}", user.getUserRoleId());
            List<RolePermission> rolePermissions = rolePermissionRepository.findActivePermissionsByRoleId(user.getUserRoleId());
            log.debug("Found {} permissions for roleId: {}", rolePermissions.size(), user.getUserRoleId());

            for (RolePermission rp : rolePermissions) {
                String permissionCode = rp.getPermission().getPermissionCode();
                authorities.add(new SimpleGrantedAuthority(permissionCode));
                log.debug("Added permission authority: {}", permissionCode);
            }
        }

        log.debug("Total authorities for user {}: {}", user.getEmail(), authorities.size());
        return authorities;
    }
}

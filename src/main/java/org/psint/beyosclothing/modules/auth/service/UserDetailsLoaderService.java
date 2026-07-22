package org.psint.beyosclothing.modules.auth.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * User Details Service Interface
 * Loads user-specific data for authentication
 */
public interface UserDetailsLoaderService {

    /**
     * Load user by username (email)
     * @param username the username (email)
     * @return UserDetails for Spring Security
     * @throws UsernameNotFoundException if user not found
     */
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
}


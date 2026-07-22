package org.psint.beyosclothing.modules.auth.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.core.security.jwt.JwtService;
import org.psint.beyosclothing.core.util.CookieUtil;
import org.psint.beyosclothing.modules.auth.dto.SessionData;
import org.psint.beyosclothing.modules.auth.dto.request.email.EmailVerificationRequest;
import org.psint.beyosclothing.modules.auth.dto.request.password.DevPasswordResetRequest;
import org.psint.beyosclothing.modules.auth.dto.request.password.PasswordResetConfirmRequest;
import org.psint.beyosclothing.modules.auth.dto.request.password.PasswordResetRequest;
import org.psint.beyosclothing.modules.auth.dto.request.token.RefreshTokenRequest;
import org.psint.beyosclothing.modules.auth.dto.request.user.LoginRequest;
import org.psint.beyosclothing.modules.auth.dto.request.user.RegistrationRequest;
import org.psint.beyosclothing.modules.auth.dto.response.user.AuthenticationResponse;
import org.psint.beyosclothing.modules.auth.dto.response.user.CurrentUserResponse;
import org.psint.beyosclothing.modules.auth.dto.response.user.LoginResponse;
import org.psint.beyosclothing.modules.auth.entity.*;
import org.psint.beyosclothing.modules.auth.events.EmailVerificationEvent;
import org.psint.beyosclothing.modules.auth.events.PasswordResetEvent;
import org.psint.beyosclothing.modules.auth.events.SessionEvent;
import org.psint.beyosclothing.modules.auth.events.UserCreatedEvent;
import org.psint.beyosclothing.modules.auth.repository.*;
import org.psint.beyosclothing.modules.auth.service.AuthService;
import org.psint.beyosclothing.modules.auth.service.EventPublisherService;
import org.psint.beyosclothing.modules.auth.service.RedisService;
import org.psint.beyosclothing.modules.auth.service.UsernameGeneratorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Auth Service Implementation
 * Implements all authentication business logic with session management
 * ✅ INDUSTRY STANDARD: Implementation in impl package, interface in service package
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final RedisService redisService;
    private final EventPublisherService eventPublisher;
    private final UsernameGeneratorService usernameGenerator;
    private final CookieUtil cookieUtil;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Value("${app.rabbitmq.exchange.customer:beyos.exchange.customer}")
    private String customerExchange;

    @Value("${app.rabbitmq.exchange.admin:admin.exchange}")
    private String adminExchange;

    @Value("${app.rabbitmq.exchange.pos:pos.exchange}")
    private String posExchange;

    @Value("${app.rabbitmq.exchange.reseller:reseller.exchange}")
    private String resellerExchange;

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int MAX_PASSWORD_RESET_REQUESTS = 3;
    private static final int MAX_EMAIL_VERIFICATION_REQUESTS = 5;

    @Override
    @Transactional("authTransactionManager")
    public AuthenticationResponse register(RegistrationRequest request, HttpServletRequest httpRequest) {
        log.info("Registration attempt for email: {}", request.getEmail());

        // Check if email already exists and is active
        if (userRepository.existsByActiveEmail(request.getEmail())) {
            throw new BadRequestException("Email already exists");
        }

        // Rate limiting check
        String rateLimitKey = "register:" + getClientIP(httpRequest);
        if (redisService.isRateLimited(rateLimitKey, 5, Duration.ofHours(1))) {
            throw new BadRequestException("Too many registration attempts. Please try again later.");
        }

        // Get user role
        UserRole role = userRoleRepository.findByRoleName(request.getRole())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRole()));

        // ✅ Auto-generate meaningful username
        String generatedUsername = usernameGenerator.generateUsername(request.getRole());
        log.info("Generated username: {} for role: {}", generatedUsername, request.getRole());

        // Create user
        User user = User.builder()
                .username(generatedUsername)
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .userType(role.getUserType())
                .userRoleId(role.getId())
                .emailVerified(false)
                .accountLocked(false)
                .loginAttempts(0)
                .build();

        user = userRepository.save(user);
        log.info("User created successfully - ID: {}, Username: {}, Email: {}",
                user.getId(), user.getUsername(), user.getEmail());

        // Generate email verification token
        String verificationToken = UUID.randomUUID().toString();
        EmailVerificationToken emailToken = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(passwordEncoder.encode(verificationToken))
                .expiresAt(LocalDateTime.now().plusHours(24))
                .isUsed(false)
                .build();
        emailVerificationTokenRepository.save(emailToken);

        // Publish user created event
        UserCreatedEvent userCreatedEvent = UserCreatedEvent.builder()
                .userId(user.getId())
                .username(generatedUsername)
                .email(user.getEmail()) // ✅ Add email field
                .role(role.getRoleName())
                .firstName(request.getFirstName())
                .lastName(request.getLastName()) // ✅ Add lastName field
                .phone(request.getPhone())
                .createdAt(LocalDateTime.now())
                .userType(role.getUserType())
                .userRoleId(role.getId())
                .build();
        eventPublisher.publishUserCreatedEvent(userCreatedEvent);

        // Publish email verification event
        EmailVerificationEvent emailEvent = EmailVerificationEvent.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .verificationToken(verificationToken)
                .createdAt(LocalDateTime.now())
                .priority("HIGH")
                .build();
        eventPublisher.publishEmailVerificationEvent(emailEvent);

        // Generate tokens
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        // Store in Redis (use email as key)
        redisService.saveJwtSession(user.getEmail(), accessToken, accessTokenExpiration);
        redisService.saveRefreshToken(user.getEmail(), refreshToken, refreshTokenExpiration);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .userInfo(AuthenticationResponse.UserInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(role.getRoleName())
                        .emailVerified(user.getEmailVerified())
                        .build())
                .build();
    }

    @Override
    @Transactional("authTransactionManager")
    public AuthenticationResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        log.info("Login attempt for email: {}", request.getEmail());

        String clientIP = getClientIP(httpRequest);
        String rateLimitKey = "login:" + clientIP +":" + request.getEmail();

        // Rate limiting check
        if (redisService.isRateLimited(rateLimitKey, MAX_LOGIN_ATTEMPTS, Duration.ofMinutes(15))) {
            throw new BadRequestException("Too many login attempts. Please try again later.");
        }

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check if account is locked
        if (user.getAccountLocked()) {
            recordLoginHistory(user, httpRequest, false, "Account is locked");
            throw new BadRequestException("Account is locked. Please contact support.");
        }

        // Check if user is active in auth DB
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            recordLoginHistory(user, httpRequest, false, "Account is deactivated");
            throw new BadRequestException("Account is deactivated. Please contact support.");
        }

        // Check if admin/reseller record is active in their respective DB
        String userRoleName = user.getUserType();
        if (user.getUserRoleId() != null) {
            UserRole userRole = userRoleRepository.findById(user.getUserRoleId()).orElse(null);
            if (userRole != null) {
                userRoleName = userRole.getUserType();
            }
        }

        if ("ADMIN".equals(userRoleName)) {
            Map<String, Object> adminDetails = fetchAdminDetailsByUserId(user.getId());
            if (adminDetails == null || !Boolean.TRUE.equals(adminDetails.get("found"))) {
                recordLoginHistory(user, httpRequest, false, "Admin profile not found");
                throw new BadRequestException("Admin profile not found. Please contact support.");
            }
            if (!Boolean.TRUE.equals(adminDetails.get("isActive"))) {
                recordLoginHistory(user, httpRequest, false, "Admin account is deactivated");
                throw new BadRequestException("Admin account is deactivated. Please contact support.");
            }
        } else if ("RESELLER".equals(userRoleName)) {
            Map<String, Object> resellerDetails = fetchResellerDetailsByUserId(user.getId());
            if (resellerDetails == null || !Boolean.TRUE.equals(resellerDetails.get("found"))) {
                recordLoginHistory(user, httpRequest, false, "Reseller profile not found");
                throw new BadRequestException("Reseller profile not found. Please contact support.");
            }
            if (!Boolean.TRUE.equals(resellerDetails.get("isActive"))) {
                recordLoginHistory(user, httpRequest, false, "Reseller account is deactivated");
                throw new BadRequestException("Reseller account is deactivated. Please contact support.");
            }
        }

        try {
            // Authenticate with email
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            // Reset login attempts
            user.setLoginAttempts(0);
            userRepository.save(user);

            // ==================== SESSION MANAGEMENT ====================

            // Step 1: Invalidate old session (single session enforcement)
            String oldSessionId = redisService.getUserActiveSessionId(user.getEmail());
            if (oldSessionId != null) {
                redisService.deleteSession(oldSessionId);
                log.info("Invalidated old session for user: {}", user.getEmail());

                // Publish SESSION_REPLACED event
                publishSessionEvent("SESSION_REPLACED", null, user, httpRequest, oldSessionId);
            }

            // Step 2: Generate new sessionId
            String sessionId = UUID.randomUUID().toString();

            // Step 3: Generate tokens with sessionId embedded
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
            String accessToken = jwtService.generateTokenWithSession(userDetails, sessionId);
            String refreshToken = jwtService.generateRefreshTokenWithSession(userDetails, sessionId);

            // Step 4: Get role name for session data
            String roleName = user.getUserType();
            if (user.getUserRoleId() != null) {
                UserRole userRole = userRoleRepository.findById(user.getUserRoleId()).orElse(null);
                if (userRole != null) {
                    roleName = userRole.getUserType();
                }
            }

            // Step 5: Create session data
            SessionData sessionData = SessionData.builder()
                    .sessionId(sessionId)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .role(roleName)
                    .createdAt(LocalDateTime.now())
                    .lastAccessed(LocalDateTime.now())
                    .userAgent(httpRequest.getHeader("User-Agent"))
                    .ipAddress(clientIP)
                    .build();

            // Step 6: Save session to Redis
            redisService.saveSession(sessionId, sessionData, refreshTokenExpiration);
            redisService.saveUserActiveSessionId(user.getEmail(), sessionId, refreshTokenExpiration);

            // Step 7: Maintain backward compatibility - save to old Redis keys
            redisService.saveJwtSession(user.getEmail(), accessToken, accessTokenExpiration);
            redisService.saveRefreshToken(user.getEmail(), refreshToken, refreshTokenExpiration);

            // Record successful login
            recordLoginHistory(user, httpRequest, true, null);

            // Reset rate limit on successful login
            redisService.resetRateLimit(rateLimitKey);

            // Publish SESSION_CREATED event
            publishSessionEvent("SESSION_CREATED", sessionId, user, httpRequest, null);

            log.info("Login successful for user: {}, sessionId: {}", user.getId(), sessionId);

            // Return both old format (for backward compatibility) and new format
            return AuthenticationResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(accessTokenExpiration / 1000)
                    .userInfo(AuthenticationResponse.UserInfo.builder()
                            .userId(user.getId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .role(roleName)
                            .emailVerified(user.getEmailVerified())
                            .build())
                    .build();
        } catch (BadCredentialsException e) {
            // Increment failed login attempts
            user.setLoginAttempts(user.getLoginAttempts() + 1);

            if (user.getLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
                user.setAccountLocked(true);
                recordLoginHistory(user, httpRequest, false, "Account locked due to multiple failed attempts");
                userRepository.save(user);
                throw new BadRequestException("Account locked due to multiple failed login attempts");
            }

            userRepository.save(user);
            recordLoginHistory(user, httpRequest, false, "Invalid credentials");
            throw new BadRequestException("Invalid credentials");
        }
    }

    /**
     * New cookie-based login for browser sessions
     */
    @Transactional("authTransactionManager")
    public LoginResponse loginWithCookies(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // Reuse existing login logic
        AuthenticationResponse authResponse = login(request, httpRequest);

        // Set HTTP-only cookies
        int accessMaxAge = (int) (accessTokenExpiration / 1000);
        int refreshMaxAge = (int) (refreshTokenExpiration / 1000);

        cookieUtil.setAccessTokenCookie(httpResponse, authResponse.getAccessToken(), accessMaxAge);
        cookieUtil.setRefreshTokenCookie(httpResponse, authResponse.getRefreshToken(), refreshMaxAge);

        // Determine default portal based on role
        String defaultPortal = determineDefaultPortal(authResponse.getUserInfo().getRole());

        return LoginResponse.builder()
                .success(true)
                .defaultPortal(defaultPortal)
                .build();
    }

     /**
      * Get current authenticated user
      * ✅ Fetches firstName, lastName, phoneNumber from admin/reseller/customer DB via RabbitMQ
      * Only handles ADMIN, RESELLER, and CUSTOMER roles
      */
    public CurrentUserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check if user is active
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException("Account is deactivated. Please contact support.");
        }

        String roleName = user.getUserType();

        if (user.getUserRoleId() != null) {
            UserRole userRole = userRoleRepository.findById(user.getUserRoleId()).orElse(null);
            if (userRole != null) {
                roleName = userRole.getUserType();
            }
        }

        String uuid = null;
        String firstName = null;
        String lastName = null;
        String phoneNumber = null;
        Long tableId = null;  // admin/reseller/customer table ID from the respective tables
        boolean isPosCashier = false;
        String posCashierUuid = null;

        log.info("User role : {}", roleName);

        if ("ADMIN".equals(roleName)) {
            // Fetch admin details (uuid, firstName, lastName, phoneNumber) from admin module via RabbitMQ
            Map<String, Object> adminDetails = fetchAdminDetailsByUserId(user.getId());
            if (adminDetails != null && Boolean.TRUE.equals(adminDetails.get("found"))) {
                // Verify userId matches the returned record
                Long returnedUserId = adminDetails.get("userId") instanceof Number
                    ? ((Number) adminDetails.get("userId")).longValue()
                    : null;

                if (returnedUserId != null && returnedUserId.equals(user.getId())) {
                    Long adminId = adminDetails.get("adminId") instanceof Number
                        ? ((Number) adminDetails.get("adminId")).longValue()
                        : null;

                    uuid = (String) adminDetails.get("uuid");
                    firstName = (String) adminDetails.get("firstName");
                    lastName = (String) adminDetails.get("lastName");
                    // Backward-compatible: admin consumer historically returns "phone"
                    Object phoneObj = adminDetails.containsKey("phoneNumber") ? adminDetails.get("phoneNumber") : adminDetails.get("phone");
                    phoneNumber = phoneObj != null ? String.valueOf(phoneObj) : null;
                    tableId = adminId;  // Set admin table ID

                    log.info("✅ Admin details matched: userId={}, adminId={}, uuid={}",
                        user.getId(), adminId, uuid);

                    // Check if this admin is also a POS cashier (using admin.id as tableId)
                    if (adminId != null) {
                        Map<String, Object> cashierInfo = fetchPosCashierInfoByAdminId(adminId);
                        if (cashierInfo != null && Boolean.TRUE.equals(cashierInfo.get("found"))) {
                            isPosCashier = true;
                            posCashierUuid = (String) cashierInfo.get("cashierUuid");
                            log.info("Admin is also a POS cashier: adminId={}, cashierUuid={}", adminId, posCashierUuid);
                        }
                    }
                } else {
                    log.warn("❌ Admin userId mismatch: expected={}, returned={}",
                        user.getId(), returnedUserId);
                }
            }
        } else if ("RESELLER".equals(roleName)) {
            // Fetch reseller details (uuid, firstName, lastName, phoneNumber) from reseller module via RabbitMQ
            Map<String, Object> resellerDetails = fetchResellerDetailsByUserId(user.getId());
            if (resellerDetails != null && Boolean.TRUE.equals(resellerDetails.get("found"))) {
                // Verify userId matches the returned record
                Long returnedUserId = resellerDetails.get("userId") instanceof Number
                    ? ((Number) resellerDetails.get("userId")).longValue()
                    : null;

                if (returnedUserId != null && returnedUserId.equals(user.getId())) {
                    Long resellerId = resellerDetails.get("resellerId") instanceof Number
                        ? ((Number) resellerDetails.get("resellerId")).longValue()
                        : null;

                    uuid = (String) resellerDetails.get("uuid");
                    firstName = (String) resellerDetails.get("firstName");
                    lastName = (String) resellerDetails.get("lastName");
                    // Backward-compatible: reseller consumer historically returns "phone"
                    Object phoneObj = resellerDetails.containsKey("phoneNumber") ? resellerDetails.get("phoneNumber") : resellerDetails.get("phone");
                    phoneNumber = phoneObj != null ? String.valueOf(phoneObj) : null;
                    tableId = resellerId;  // Set reseller table ID

                    log.info("✅ Reseller details matched: userId={}, resellerId={}, uuid={}",
                        user.getId(), resellerId, uuid);
                } else {
                    log.warn("❌ Reseller userId mismatch: expected={}, returned={}",
                        user.getId(), returnedUserId);
                }
            }
        } else if ("CUSTOMER".equals(roleName)) {
            // Fetch customer details (customerUuid, firstName, lastName, phoneNumber) from customer module via RabbitMQ
            Map<String, Object> customerDetails = fetchCustomerDetailsByUserId(user.getId());
            if (customerDetails != null && Boolean.TRUE.equals(customerDetails.get("found"))) {
                Long customerId = customerDetails.get("customerId") instanceof Number
                        ? ((Number) customerDetails.get("customerId")).longValue()
                        : null;

                uuid = (String) customerDetails.get("customerUuid");
                firstName = (String) customerDetails.get("firstName");
                lastName = (String) customerDetails.get("lastName");
                // Backward-compatible: customer consumer historically returns "phone"
                Object phoneObj = customerDetails.containsKey("phoneNumber") ? customerDetails.get("phoneNumber") : customerDetails.get("phone");
                phoneNumber = phoneObj != null ? String.valueOf(phoneObj) : null;
                tableId = customerId;  // Set customer table ID

                log.info("Customer details matched: userId={}, customerId={}, uuid={}",
                        user.getId(), customerId, uuid);
            } else {
                log.warn("Customer details not found for userId: {}", user.getId());
            }
        }

        return CurrentUserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .role(roleName)
                .tableId(tableId)  // Return admin/reseller table ID instead of userRoleId
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber(phoneNumber)
                .emailVerified(user.getEmailVerified())
                .uuid(uuid)
                .isPosCashier(isPosCashier)
                .posCashierUuid(posCashierUuid)
                .build();
    }

    @Override
    @Transactional("authTransactionManager")

    public void logout(String username, String token) {
        log.info("Logout request for username: {}", username);

        // Extract sessionId from token if present
        String sessionId = null;
        try {
            sessionId = jwtService.extractSessionId(token);
        } catch (Exception e) {
            log.warn("Could not extract sessionId from token during logout");
        }

        // Delete session from Redis
        if (sessionId != null) {
            SessionData sessionData = redisService.getSession(sessionId);
            redisService.deleteSession(sessionId);
            redisService.deleteUserActiveSessionId(username);

            // Publish SESSION_LOGGED_OUT event
            if (sessionData != null) {
                SessionEvent event = SessionEvent.builder()
                        .eventType("SESSION_LOGGED_OUT")
                        .sessionId(sessionId)
                        .userId(sessionData.getUserId())
                        .email(sessionData.getEmail())
                        .role(sessionData.getRole())
                        .timestamp(LocalDateTime.now())
                        .build();
                eventPublisher.publishSessionEvent(event);
            }
        }

        // Blacklist the current token
        redisService.blacklistToken(token, accessTokenExpiration);

        // Delete old Redis session and refresh token
        redisService.deleteJwtSession(username);
        redisService.deleteRefreshToken(username);

        log.info("User logged out successfully: {}", username);
    }

    /**
     * Fetch customer details by userId using RabbitMQ request-reply pattern
     * Retrieves: customerId, customerUuid, firstName, lastName, phoneNumber
     * Message routing: customer.by.userid.request
     */
    private Map<String, Object> fetchCustomerDetailsByUserId(Long userId) {
        try {
            String requestId = UUID.randomUUID().toString();

            // Create request using Map
            java.util.Map<String, Object> request = new java.util.HashMap<>();
            request.put("userId", userId);
            request.put("requestId", requestId);

            log.info("📤 Sending customer lookup request via RabbitMQ - userId: {}, requestId: {}, exchange: {}, routingKey: customer.by.userid.request",
                    userId, requestId, customerExchange);

            // Send request and wait for response (5 seconds timeout)
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = (java.util.Map<String, Object>) rabbitTemplate
                    .convertSendAndReceive(
                            customerExchange,
                            "customer.by.userid.request",
                            request
                    );

            if (response == null) {
                log.error("❌ RabbitMQ RPC returned null - userId: {}, requestId: {}. Check if customer-by-userid-request queue has a listener and binding is correct.",
                        userId, requestId);
                return null;
            }

            log.info("📥 Received customer lookup response - requestId: {}, found: {}",
                    requestId, response.get("found"));

            if (Boolean.TRUE.equals(response.get("found"))) {
                log.info("✅ Customer found via RabbitMQ - userId: {}, customerId: {}, uuid: {}, firstName: {}, lastName: {}",
                        userId, response.get("customerId"), response.get("customerUuid"),
                        response.get("firstName"), response.get("lastName"));
                return response;
            } else {
                log.warn("⚠️ Customer not found for userId: {} (response found=false)", userId);
                return null;
            }

        } catch (Exception e) {
            log.error("❌ Error fetching customer details via RabbitMQ for userId: {}. Error: {}",
                    userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetch customer UUID by userId using RabbitMQ request-reply pattern
     */
    private String fetchCustomerUuidByUserId(Long userId) {
        Map<String, Object> response = fetchCustomerDetailsByUserId(userId);
        if (response != null && Boolean.TRUE.equals(response.get("found"))) {
            return (String) response.get("customerUuid");
        }
        return null;
    }

    /**
     * Fetch admin details by userId using RabbitMQ request-reply pattern
     * Retrieves: uuid, firstName, lastName, phoneNumber from admin module DB
     * Message routing: admin.details.lookup.request
     */
    private Map<String, Object> fetchAdminDetailsByUserId(Long userId) {
        try {
            String requestId = UUID.randomUUID().toString();

            java.util.Map<String, Object> request = new java.util.HashMap<>();
            request.put("userId", userId);
            request.put("requestId", requestId);

            log.debug("Sending admin details lookup request for userId: {}, requestId: {}", userId, requestId);

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = (java.util.Map<String, Object>) rabbitTemplate
                    .convertSendAndReceive(
                            adminExchange,
                            "admin.details.lookup.request",
                            request
                    );

            if (response != null && Boolean.TRUE.equals(response.get("found"))) {
                log.debug("Admin found for userId: {} - UUID: {}, firstName: {}, lastName: {}",
                        userId, response.get("uuid"), response.get("firstName"), response.get("lastName"));
                return response;
            } else {
                log.warn("Admin details not found for userId: {}", userId);
                return null;
            }

        } catch (Exception e) {
            log.error("Error fetching admin details for userId: {}", userId, e);
            return null;
        }
    }

    /**
     * Fetch POS cashier info by userId using RabbitMQ request-reply pattern
     */
    private java.util.Map<String, Object> fetchPosCashierInfoByUserId(Long userId) {
        try {
            String requestId = UUID.randomUUID().toString();

            java.util.Map<String, Object> request = new java.util.HashMap<>();
            request.put("userId", userId);
            request.put("requestId", requestId);

            log.debug("Sending POS cashier lookup request for userId: {}, requestId: {}", userId, requestId);

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = (java.util.Map<String, Object>) rabbitTemplate
                    .convertSendAndReceive(
                            posExchange,
                            "pos.cashier.lookup.request",
                            request
                    );

            if (response != null) {
                log.debug("POS cashier lookup response for userId={}: isPosCashier={}",
                        userId, response.get("isPosCashier"));
            } else {
                log.debug("POS cashier lookup returned null for userId: {}", userId);
            }

            return response;

        } catch (Exception e) {
            log.error("Error fetching POS cashier info for userId: {}", userId, e);
            return null;
        }
    }

    /**
     * Fetch POS cashier info by admin ID (tableId) using RabbitMQ request-reply pattern
     * Looks up active cashier in pos_cashiers table where user_id matches adminId
     * Returns: found (boolean), cashierUuid (string)
     * Message routing: pos.cashier.by.admin.lookup.request
     */
    private java.util.Map<String, Object> fetchPosCashierInfoByAdminId(Long adminId) {
        try {
            String requestId = UUID.randomUUID().toString();

            java.util.Map<String, Object> request = new java.util.HashMap<>();
            request.put("adminId", adminId);
            request.put("requestId", requestId);

            log.debug("Sending POS cashier lookup request for adminId: {}, requestId: {}", adminId, requestId);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) rabbitTemplate
                    .convertSendAndReceive(
                            posExchange,
                            "pos.cashier.by.admin.lookup.request",
                            request
                    );

            if (response != null && Boolean.TRUE.equals(response.get("found"))) {
                log.debug("POS cashier found for adminId={}: cashierUuid={}",
                        adminId, response.get("cashierUuid"));
                return response;
            } else {
                log.debug("Active POS cashier not found for adminId: {}", adminId);
                return null;
            }

        } catch (Exception e) {
            log.error("Error fetching POS cashier info for adminId: {}", adminId, e);
            return null;
        }
    }

    /**
     * Fetch reseller details by userId using RabbitMQ request-reply pattern
     * Retrieves: uuid, firstName, lastName, phoneNumber from reseller module DB
     * Message routing: reseller.details.lookup.request
     */
    private Map<String, Object> fetchResellerDetailsByUserId(Long userId) {
        try {
            String requestId = UUID.randomUUID().toString();

            java.util.Map<String, Object> request = new java.util.HashMap<>();
            request.put("userId", userId);
            request.put("requestId", requestId);

            log.debug("Sending reseller details lookup request for userId: {}, requestId: {}", userId, requestId);

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = (java.util.Map<String, Object>) rabbitTemplate
                    .convertSendAndReceive(
                            resellerExchange,
                            "reseller.details.lookup.request",
                            request
                    );

            if (response != null && Boolean.TRUE.equals(response.get("found"))) {
                log.debug("Reseller found for userId: {} - UUID: {}, firstName: {}, lastName: {}",
                        userId, response.get("uuid"), response.get("firstName"), response.get("lastName"));
                return response;
            } else {
                log.warn("Reseller details not found for userId: {}", userId);
                return null;
            }

        } catch (Exception e) {
            log.error("Error fetching reseller details for userId: {}", userId, e);
            return null;
        }
    }

    @Override
    @Transactional("authTransactionManager")
    public void logoutWithCookies(String username, String token, HttpServletResponse httpResponse) {
        logout(username, token);
        cookieUtil.clearAllAuthCookies(httpResponse);
    }

    @Override
    @Transactional("authTransactionManager")
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        String username = jwtService.extractUsername(refreshToken);

        if (username == null) {
            throw new BadRequestException("Invalid refresh token");
        }

        // Verify refresh token from Redis
        String storedRefreshToken = redisService.getRefreshToken(username);
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new BadRequestException("Invalid or expired refresh token");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        if (!jwtService.isTokenValid(refreshToken, userDetails)) {
            throw new BadRequestException("Invalid refresh token");
        }

        // Generate a new access token for the same Redis-backed session.
        String sessionId = jwtService.extractSessionId(refreshToken);
        String newAccessToken = sessionId != null
                ? jwtService.generateTokenWithSession(userDetails, sessionId)
                : jwtService.generateToken(userDetails);
        redisService.saveJwtSession(username, newAccessToken, accessTokenExpiration);

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check if user is active
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException("Account is deactivated. Please contact support.");
        }

        // Check if admin/reseller record is active
        String roleName = user.getUserType();
        if (user.getUserRoleId() != null) {
            UserRole userRole = userRoleRepository.findById(user.getUserRoleId()).orElse(null);
            if (userRole != null) {
                roleName = userRole.getUserType();
            }
        }

        if ("ADMIN".equals(roleName)) {
            Map<String, Object> adminDetails = fetchAdminDetailsByUserId(user.getId());
            if (adminDetails != null && Boolean.TRUE.equals(adminDetails.get("found"))
                    && !Boolean.TRUE.equals(adminDetails.get("isActive"))) {
                throw new BadRequestException("Admin account is Deleted");
            }
        } else if ("RESELLER".equals(roleName)) {
            Map<String, Object> resellerDetails = fetchResellerDetailsByUserId(user.getId());
            if (resellerDetails != null && Boolean.TRUE.equals(resellerDetails.get("found"))
                    && !Boolean.TRUE.equals(resellerDetails.get("isActive"))) {
                throw new BadRequestException("Reseller account is Deleted.");
            }
        }

        log.info("Token refreshed for user: {}", username);

        return AuthenticationResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .userInfo(AuthenticationResponse.UserInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(roleName)
                        .emailVerified(user.getEmailVerified())
                        .build())
                .build();
    }

    @Override
    @Transactional("authTransactionManager")
    public void requestPasswordReset(PasswordResetRequest request, HttpServletRequest httpRequest) {
        log.info("Password reset requested for email: {}", request.getEmail());

        // Email throttling
        if (!redisService.canSendEmail(request.getEmail(), MAX_PASSWORD_RESET_REQUESTS, Duration.ofHours(1))) {
            throw new BadRequestException("Too many password reset requests. Please try again later.");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Generate reset token
        String resetToken = UUID.randomUUID().toString();

        PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(passwordEncoder.encode(resetToken))
                .expiresAt(LocalDateTime.now().plusHours(1))
                .isUsed(false)
                .ipAddress(getClientIP(httpRequest))
                .userAgent(httpRequest.getHeader("User-Agent"))
                .build();
        passwordResetTokenRepository.save(passwordResetToken);

        // Publish password reset event
        PasswordResetEvent event = PasswordResetEvent.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .resetToken(resetToken)
                .createdAt(LocalDateTime.now())
                .ipAddress(getClientIP(httpRequest))
                .build();
        eventPublisher.publishPasswordResetEvent(event);

        log.info("Password reset token generated for user: {}", user.getId());
    }

    @Override
    @Transactional("authTransactionManager")
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        log.info("Password reset confirmation attempt");

        PasswordResetToken resetToken = passwordResetTokenRepository.findValidToken(
                        request.getToken(), LocalDateTime.now())
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setLoginAttempts(0);
        user.setAccountLocked(false);
        userRepository.save(user);

        // Mark token as used
        resetToken.setIsUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Invalidate all sessions
        redisService.deleteJwtSession(user.getEmail());
        redisService.deleteRefreshToken(user.getEmail());

        log.info("Password reset successful for user: {}", user.getId());
    }

    @Override
    @Transactional("authTransactionManager")
    public void verifyEmail(EmailVerificationRequest request) {
        log.info("Email verification attempt");

        EmailVerificationToken verificationToken = emailVerificationTokenRepository
                .findValidToken(request.getToken(), LocalDateTime.now())
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification token"));

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setIsUsed(true);
        verificationToken.setUsedAt(LocalDateTime.now());
        emailVerificationTokenRepository.save(verificationToken);

        log.info("Email verified successfully for user: {}", user.getId());
    }

    @Override
    @Transactional("authTransactionManager")
    public void resendVerificationEmail(String email) {
        log.info("Resend verification email request for: {}", email);

        if (!redisService.canSendEmail(email, MAX_EMAIL_VERIFICATION_REQUESTS, Duration.ofHours(1))) {
            throw new BadRequestException("Too many verification email requests. Please try again later.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getEmailVerified()) {
            throw new BadRequestException("Email already verified");
        }

        // Generate new verification token
        String verificationToken = UUID.randomUUID().toString();
        EmailVerificationToken emailToken = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(passwordEncoder.encode(verificationToken))
                .expiresAt(LocalDateTime.now().plusHours(24))
                .isUsed(false)
                .build();
        emailVerificationTokenRepository.save(emailToken);

        // Publish email verification event
        EmailVerificationEvent event = EmailVerificationEvent.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .verificationToken(verificationToken)
                .createdAt(LocalDateTime.now())
                .priority("NORMAL")
                .build();
        eventPublisher.publishEmailVerificationEvent(event);

        log.info("Verification email resent for user: {}", user.getId());
    }

    /**
     * Development-only: Reset password without any validations
     * WARNING: This should NEVER be enabled in production
     * NO token validation, NO rate limiting, NO security checks
     */
    @Override
    @Transactional("authTransactionManager")
    public void devResetPassword(DevPasswordResetRequest request) {
        log.warn("⚠️ DEV MODE: Password reset without validation for email: {}", request.getEmail());

        // Find user by email - throw exception if not found
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        // Update password directly - NO VALIDATIONS
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setDateUpdated(LocalDateTime.now());
        userRepository.save(user);

        log.warn("⚠️ DEV MODE: Password reset successful for user ID: {}", user.getId());
    }

    private void recordLoginHistory(User user, HttpServletRequest request, boolean success, String failureReason) {
        LoginHistory loginHistory = LoginHistory.builder()
                .user(user)
                .ipAddress(getClientIP(request))
                .deviceInfo(extractDeviceInfo(request))
                .userAgent(request.getHeader("User-Agent"))
                .loginTime(LocalDateTime.now())
                .isLoginSuccess(success)
                .failureReason(failureReason)
                .build();
        loginHistoryRepository.save(loginHistory);
    }

    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractDeviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) return "Unknown";

        if (userAgent.contains("Mobile")) return "Mobile";
        if (userAgent.contains("Tablet")) return "Tablet";
        return "Desktop";
    }

    /**
     * Publish session lifecycle events to RabbitMQ
     */
    private void publishSessionEvent(String eventType, String sessionId, User user, HttpServletRequest request, String oldSessionId) {
        String roleName = user.getUserType();
        if (user.getUserRoleId() != null) {
            UserRole userRole = userRoleRepository.findById(user.getUserRoleId()).orElse(null);
            if (userRole != null) {
                roleName = userRole.getRoleName();
            }
        }

        SessionEvent event = SessionEvent.builder()
                .eventType(eventType)
                .sessionId(sessionId)
                .userId(user.getId())
                .email(user.getEmail())
                .role(roleName)
                .timestamp(LocalDateTime.now())
                .ipAddress(getClientIP(request))
                .userAgent(request.getHeader("User-Agent"))
                .oldSessionId(oldSessionId)
                .build();

        eventPublisher.publishSessionEvent(event);
    }

    /**
     * Determine default portal based on user role
     */
    private String determineDefaultPortal(String role) {
        if (role == null) return "CUSTOMER";

        if (role.contains("ADMIN") || role.equals("ADMIN")) {
            return "ADMIN";
        } else if (role.contains("RESELLER") || role.equals("RESELLER")) {
            return "RESELLER";
        } else {
            return "CUSTOMER";
        }
    }
}

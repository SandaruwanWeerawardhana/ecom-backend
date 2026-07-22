package org.psint.beyosclothing.modules.resellers.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.dto.request.ResellerRegistrationRequest;
import org.psint.beyosclothing.modules.resellers.dto.request.ResellerUpdateRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerProfileResponse;
import org.psint.beyosclothing.modules.resellers.entity.Reseller;
import org.psint.beyosclothing.modules.resellers.entity.ResellerStatus;
import org.psint.beyosclothing.modules.resellers.events.*;
import org.psint.beyosclothing.modules.resellers.exception.InvalidCurrentPasswordException;
import org.psint.beyosclothing.modules.resellers.exception.PasswordReuseException;
import org.psint.beyosclothing.modules.resellers.repository.ResellerBankAccountRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerCartItemRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerCartRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerRepository;
import org.psint.beyosclothing.modules.resellers.service.ResellerService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of Reseller Service
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional("resellerTransactionManager")
public class ResellerServiceImpl implements ResellerService {

    private final ResellerRepository resellerRepository;
    private final ResellerCartRepository resellerCartRepository;
    private final ResellerCartItemRepository resellerCartItemRepository;
    private final ResellerBankAccountRepository resellerBankAccountRepository;

    /**
     * General-purpose RabbitTemplate (fire-and-forget publishes).
     */
    private final RabbitTemplate rabbitTemplate;
    
    private final RabbitTemplate authRpcRabbitTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ResellerEventPublisher eventPublisher;

    @Value("${app.rabbitmq.exchange.auth}")
    private String authExchange;

    // RPC routing key for user creation (synchronous with response)
    private static final String USER_CREATE_RPC_ROUTING_KEY = "auth.user.create.rpc";
    private static final String VERIFY_PASSWORD_RPC_ROUTING_KEY = "auth.user.verify.password.rpc";
    private static final String UPDATE_PASSWORD_RPC_ROUTING_KEY = "auth.user.update.password.rpc";
    private static final String UPDATE_ACCOUNT_LOCKED_RPC_ROUTING_KEY = "auth.user.update.account.locked.rpc";

    @Override
    public void changePassword(String userUuId, String currentPassword, String newPassword, String confirmPassword) {
        log.info("Change password requested for resellerUuid={}", userUuId);
        
        try {
            // 1) FIRST: Find user_id by matching userUuid in Reseller table
            Reseller reseller = resellerRepository.findByUuid(userUuId)
                    .orElseThrow(() -> new IllegalArgumentException("Reseller not found with UUID: " + userUuId));
            
            Long userId = reseller.getUserId();
            log.info("Found Reseller with UUID={}, userId={}", userUuId, userId);

            if (currentPassword == null) {
                throw new IllegalArgumentException("Current password cannot be null");
            }

            // 2) NEXT: Verify current password in User table that matches the user_id
            Map<String, Object> verifyRequest = new HashMap<>();
            verifyRequest.put("userId", userId);
            verifyRequest.put("currentPassword", currentPassword);

            Object verifyResponse = authRpcRabbitTemplate.convertSendAndReceive(
                    authExchange,
                    VERIFY_PASSWORD_RPC_ROUTING_KEY,
                    verifyRequest
            );

            boolean verified = false;
            if (verifyResponse instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = (Map<String, Object>) verifyResponse;
                Object successObj = resp.get("success");
                if (successObj instanceof Boolean) {
                    verified = (Boolean) successObj;
                }
            }

            if (!verified) {
                log.warn("Current password verification failed for userId={}", userId);
                throw new InvalidCurrentPasswordException();
            }

            log.info("Current password verified successfully for userId={}", userId);

            // 3) AFTER VERIFICATION: Check newPassword and confirmPassword are equal
            if (newPassword == null || confirmPassword == null || !newPassword.equals(confirmPassword)) {
                throw new IllegalArgumentException("New password and confirm password do not match");
            }

            // Prevent reusing the same password
            if (currentPassword.equals(newPassword)) {
                throw new PasswordReuseException();
            }

            // Update password in auth DB for that user_id
            String encodedNewPassword = passwordEncoder.encode(newPassword);

            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("userId", userId);
            updateRequest.put("newPassword", encodedNewPassword);

            Object updateResponse = authRpcRabbitTemplate.convertSendAndReceive(
                    authExchange,
                    UPDATE_PASSWORD_RPC_ROUTING_KEY,
                    updateRequest
            );

            boolean updateSuccess = false;
            if (updateResponse instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = (Map<String, Object>) updateResponse;
                Object successObj = resp.get("success");
                if (successObj instanceof Boolean) {
                    updateSuccess = (Boolean) successObj;
                }
            }

            if (!updateSuccess) {
                throw new IllegalStateException("Failed to update password in auth service");
            }

            log.info("Password changed successfully for userId={}, resellerUuid={}", userId, userUuId);
        } catch (InvalidCurrentPasswordException e) {
            log.warn("Invalid current password for resellerUuid={}", userUuId);
            throw e;
        } catch (PasswordReuseException e) {
            log.warn("Password reuse detected for resellerUuid={}", userUuId);
            throw e;
        } catch (Exception e) {
            log.error("Error while changing password for resellerUuid={}", userUuId, e);
            throw new IllegalStateException("Failed to change password. Please try again later.", e);
        }
    }

    @Override
    public void deleteReseller(String uuid) {
        Reseller reseller = getResellerByUuid(uuid);

        if (!reseller.getIsActive()) {
            throw new IllegalStateException("Reseller is already deactivated: " + uuid);
        }

        // 1. Deactivate reseller
        reseller.setIsActive(false);
        resellerRepository.save(reseller);

        // 1.1 Deactivate matching Auth user by email
        deactivateAuthUserByEmail(reseller.getEmail());

        // 2. Deactivate active cart and its items
        resellerCartRepository.findByResellerIdAndIsActive(reseller.getId(), true)
                .ifPresent(cart -> {
                    resellerCartItemRepository.softDeleteByCartId(cart.getId());

                    cart.setIsActive(false);
                    resellerCartRepository.save(cart);

                    log.info("Deactivated cart and items for reseller: {}", uuid);
                });

        // 3. Deactivate all active bank accounts
        resellerBankAccountRepository.findByResellerIdAndIsActive(reseller.getId(), true)
                .forEach(account -> {
                    account.setIsActive(false);
                    resellerBankAccountRepository.save(account);
                });

        log.info("Deactivated {} bank account(s) for reseller: {}",
                resellerBankAccountRepository.findByResellerIdAndIsActive(reseller.getId(), false).size(), uuid);

        log.info("Reseller deleted (deactivated): {}", uuid);
    }

    @Override
    public ResellerProfileResponse register(ResellerRegistrationRequest request) {
        log.info("Registering new reseller with email: {}", request.getEmail());

        // Validate email not already in use
        if (resellerRepository.existsByEmailAndIsActiveTrue(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Hash password
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // Create User account via RabbitMQ
        Long userId = createUserAccount(request.getEmail(), hashedPassword, request.getFirstName(), request.getLastName());

        // Create Reseller entity
        Reseller reseller = Reseller.builder()
                .userId(userId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .district(request.getDistrict())
                .province(request.getProvince())
                .postalCode(request.getPostalCode())
                .status(ResellerStatus.PENDING)
                .allowPriceOverride(false)
                .creditBalance(BigDecimal.ZERO)
                .creditLimit(BigDecimal.ZERO)
                .isActive(true)
                .build();

        reseller = resellerRepository.save(reseller);

        // Publish ResellerRegisteredEvent
        publishResellerRegisteredEvent(reseller);

        // Send emails
        sendRegistrationConfirmationEmail(reseller);
        sendAdminNotificationEmail(reseller);

        log.info("Reseller registered successfully with UUID: {}", reseller.getUuid());

        return mapToProfileResponse(reseller);
    }

    @Override
    public ResellerProfileResponse getProfile(Long userId) {
        Reseller reseller = getResellerByUserId(userId);
        return mapToProfileResponse(reseller);
    }

    @Override
    public ResellerProfileResponse updateProfile(Long userId, ResellerUpdateRequest request) {
        Reseller reseller = getResellerByUserId(userId);

        // Update allowed fields only
        reseller.setFirstName(request.getFirstName());
        reseller.setLastName(request.getLastName());
        reseller.setPhone(request.getPhone());
        reseller.setAddressLine1(request.getAddressLine1());
        reseller.setAddressLine2(request.getAddressLine2());
        reseller.setCity(request.getCity());
        reseller.setDistrict(request.getDistrict());
        reseller.setProvince(request.getProvince());
        reseller.setPostalCode(request.getPostalCode());

        reseller = resellerRepository.save(reseller);

        log.info("Reseller profile updated: {}", reseller.getUuid());

        return mapToProfileResponse(reseller);
    }

    @Override
    public String getStatus(Long userId) {
        Reseller reseller = getResellerByUserId(userId);
        return switch (reseller.getStatus()) {
            case PENDING -> "Your application is pending admin approval";
            case APPROVED -> "Your account is approved and active";
            case REJECTED -> "Your application was rejected";
            case SUSPENDED -> "Your account has been suspended";
        };
    }

    @Override
    public String getStatusByEmail(String email) {
        Reseller reseller = resellerRepository.findByEmailAndIsActiveTrue(email)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found with email: " + email));
        return switch (reseller.getStatus()) {
            case PENDING -> "Your application is pending admin approval";
            case APPROVED -> "Your account is approved and active";
            case REJECTED -> "Your application was rejected";
            case SUSPENDED -> "Your account has been suspended";
        };
    }

    @Override
    public ResellerProfileResponse getProfileByEmail(String email) {
        Reseller reseller = resellerRepository.findByEmailAndIsActiveTrue(email)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found with email: " + email));
        return mapToProfileResponse(reseller);
    }

    @Override
    public Reseller getResellerByUserId(Long userId) {
        return resellerRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found for user ID: " + userId));
    }

    @Override
    public Reseller getResellerByUuid(String uuid) {
        return resellerRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found: " + uuid));
    }

    @Override
    public void approveReseller(String resellerUuid, Boolean allowPriceOverride,
                                BigDecimal minMarkupPct, BigDecimal maxMarkupPct,
                                BigDecimal creditLimit, Long adminId) {
        Reseller reseller = getResellerByUuid(resellerUuid);

        if (reseller.getStatus() != ResellerStatus.PENDING) {
            throw new IllegalStateException("Only PENDING resellers can be approved");
        }

        reseller.setStatus(ResellerStatus.APPROVED);
        reseller.setAllowPriceOverride(allowPriceOverride);
        reseller.setMinAllowedMarkupPct(minMarkupPct);
        reseller.setMaxAllowedMarkupPct(maxMarkupPct);
        reseller.setCreditLimit(creditLimit);

        resellerRepository.save(reseller);

        // Update User.accountLocked = false via RPC (synchronous with confirmation)
        updateAccountLockedViaRpc(reseller.getUserId(), false);

        // Publish event
        publishResellerApprovedEvent(reseller);

        // Send approval email
        sendApprovalEmail(reseller);

        log.info("Reseller approved: {} by admin: {}", resellerUuid, adminId);
    }

    @Override
    public void rejectReseller(String resellerUuid, String rejectionReason, Long adminId) {
        Reseller reseller = getResellerByUuid(resellerUuid);

        if (reseller.getStatus() != ResellerStatus.PENDING) {
            throw new IllegalStateException("Only PENDING resellers can be rejected");
        }

        reseller.setStatus(ResellerStatus.REJECTED);
        resellerRepository.save(reseller);

        // Lock user account in auth DB (account_locked = true) via RPC (synchronous with confirmation)
        updateAccountLockedViaRpc(reseller.getUserId(), true);

        // Publish event
        publishResellerRejectedEvent(reseller, rejectionReason);

        // Send rejection email
        sendRejectionEmail(reseller, rejectionReason);

        log.info("Reseller rejected: {} by admin: {}", resellerUuid, adminId);
    }

    @Override
    public void suspendReseller(String resellerUuid, String suspensionReason, Long adminId) {
        Reseller reseller = getResellerByUuid(resellerUuid);

        if (reseller.getStatus() != ResellerStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED resellers can be suspended");
        }

        reseller.setStatus(ResellerStatus.SUSPENDED);
        resellerRepository.save(reseller);

        // Lock user account in auth DB (account_locked = true) via RPC (synchronous with confirmation)
        updateAccountLockedViaRpc(reseller.getUserId(), true);

        // Publish event
        publishResellerSuspendedEvent(reseller, suspensionReason);

        // Send suspension email
        sendSuspensionEmail(reseller, suspensionReason);

        log.info("Reseller suspended: {} by admin: {}", resellerUuid, adminId);
    }

    @Override
    public void reactivateReseller(String resellerUuid, Long adminId) {
        Reseller reseller = getResellerByUuid(resellerUuid);

        reseller.setStatus(ResellerStatus.APPROVED);
        resellerRepository.save(reseller);

        // Unlock user account
        unlockUserAccount(reseller.getUserId());

        // Lock user account in auth
        updateAccountLockedViaRpc(reseller.getUserId(), false);

        // Send reactivation email
        sendReactivationEmail(reseller);

        log.info("Reseller reactivated: {} by admin: {}", resellerUuid, adminId);
    }

    @Override
    public void updateSettings(String resellerUuid, Boolean allowPriceOverride,
                               BigDecimal minMarkupPct, BigDecimal maxMarkupPct,
                               BigDecimal creditLimit, Long adminId) {
        Reseller reseller = getResellerByUuid(resellerUuid);

        reseller.setAllowPriceOverride(allowPriceOverride);
        reseller.setMinAllowedMarkupPct(minMarkupPct);
        reseller.setMaxAllowedMarkupPct(maxMarkupPct);
        reseller.setCreditLimit(creditLimit);

        resellerRepository.save(reseller);

        // Send notification email
        sendSettingsUpdateEmail(reseller);

        log.info("Reseller settings updated: {} by admin: {}", resellerUuid, adminId);
    }

    // Helper methods for RabbitMQ communication
    private Long createUserAccount(String email, String hashedPassword, String firstName, String lastName) {
        log.info("Creating user account via RabbitMQ for email: {}", email);
        
        try {
            // Create user creation request
            Map<String, Object> userRequest = new HashMap<>();
            userRequest.put("email", email);
            userRequest.put("password", hashedPassword);
            userRequest.put("firstName", firstName);
            userRequest.put("lastName", lastName);
            userRequest.put("userType", "RESELLER");
            userRequest.put("emailVerified", false);
            userRequest.put("accountLocked", true); // Locked until approved
            userRequest.put("timestamp", LocalDateTime.now().toString());

            // Send RPC request to auth module (dedicated template has a fixed reply timeout)
            Object response = authRpcRabbitTemplate.convertSendAndReceive(
                authExchange, 
                USER_CREATE_RPC_ROUTING_KEY,
                userRequest
            );

            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;
                Boolean success = (Boolean) responseMap.get("success");
                
                if (Boolean.TRUE.equals(success)) {
                    Number userId = (Number) responseMap.get("userId");
                    if (userId != null) {
                        log.info("User account created successfully with ID: {}", userId.longValue());
                        return userId.longValue();
                    }
                } else {
                    String error = (String) responseMap.get("error");
                    throw new IllegalStateException("Failed to create user account: " + error);
                }
            }
            
            throw new IllegalStateException("Failed to create user account: Invalid response from auth module");
            
        } catch (Exception e) {
            log.error("Error creating user account via RabbitMQ", e);
            throw new IllegalStateException("Failed to create user account. Please try again later.", e);
        }
    }

    private void unlockUserAccount(Long userId) {
        log.info("Unlocking user account: {}", userId);
        
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("userId", userId);
            request.put("accountLocked", false);
            request.put("timestamp", LocalDateTime.now().toString());
            
            rabbitTemplate.convertAndSend(authExchange, "auth.user.unlock", request);
        } catch (Exception e) {
            log.error("Error unlocking user account", e);
        }
    }

    private void lockUserAccount(Long userId) {
        log.info("Locking user account: {}", userId);
        
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("userId", userId);
            request.put("accountLocked", true);
            request.put("timestamp", LocalDateTime.now().toString());
            
            rabbitTemplate.convertAndSend(authExchange, "auth.user.lock", request);
        } catch (Exception e) {
            log.error("Error locking user account", e);
        }
    }

    /**
     * Synchronous RPC call to auth service to update the user's account_locked column.
     * This ensures the database update is confirmed before proceeding.
     * Throws IllegalStateException if the update fails.
     */
    private void updateAccountLockedViaRpc(Long userId, boolean accountLocked) {
        log.info("Updating account_locked={} for userId={} via RPC", accountLocked, userId);

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("userId", userId);
            request.put("accountLocked", accountLocked);
            request.put("timestamp", LocalDateTime.now().toString());

            // Send RPC request and wait for response
            Object response = authRpcRabbitTemplate.convertSendAndReceive(
                    authExchange,
                    UPDATE_ACCOUNT_LOCKED_RPC_ROUTING_KEY,
                    request
            );

            if (response == null) {
                // Typically indicates: no queue binding for routing key, or no RPC consumer running, or timeout.
                throw new IllegalStateException(
                        "No RPC response from auth service for routingKey=" + UPDATE_ACCOUNT_LOCKED_RPC_ROUTING_KEY +
                                ", exchange=" + authExchange + ". Ensure the auth RPC queue is declared and a consumer is running."
                );
            }

            boolean success = false;
            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = (Map<String, Object>) response;
                Object successObj = resp.get("success");
                if (successObj instanceof Boolean) {
                    success = (Boolean) successObj;
                }
            }

            if (!success) {
                String error = null;
                if (response instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resp = (Map<String, Object>) response;
                    Object errObj = resp.get("error");
                    if (errObj instanceof String) {
                        error = (String) errObj;
                    }
                }
                throw new IllegalStateException("Auth service failed to update account_locked: " + error);
            }

            log.info("Account locked status successfully updated for userId={}", userId);

        } catch (Exception e) {
            log.error("Error updating account_locked via RPC for userId={}", userId, e);
            throw new IllegalStateException("Failed to update account lock status in auth service", e);
        }
    }

    private void deactivateAuthUserByEmail(String email) {
        log.info("Deactivating auth user for reseller email: {}", email);

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("email", email);
            request.put("userType", "RESELLER");
            request.put("timestamp", LocalDateTime.now().toString());

            rabbitTemplate.convertAndSend(authExchange,
                    "auth.user.deactivate",
                    request
            );

        } catch (Exception e) {
            log.error("Error deactivating auth user for reseller email: {}", email, e);
        }
    }

    // Helper methods for events
    private void publishResellerRegisteredEvent(Reseller reseller) {
        log.info("Publishing ResellerRegisteredEvent for: {}", reseller.getUuid());
        
        ResellerRegisteredEvent event = ResellerRegisteredEvent.builder()
                .resellerUuid(reseller.getUuid())
                .userId(reseller.getUserId())
                .email(reseller.getEmail())
                .firstName(reseller.getFirstName())
                .lastName(reseller.getLastName())
                .phone(reseller.getPhone())
                .registrationDate(reseller.getDateCreated())
                .build();
                
        eventPublisher.publishResellerRegistered(event);
    }

    private void publishResellerApprovedEvent(Reseller reseller) {
        log.info("Publishing ResellerApprovedEvent for: {}", reseller.getUuid());
        
        ResellerApprovedEvent event = ResellerApprovedEvent.builder()
                .resellerUuid(reseller.getUuid())
                .userId(reseller.getUserId())
                .email(reseller.getEmail())
                .approvalDate(LocalDateTime.now())
                .allowPriceOverride(reseller.getAllowPriceOverride())
                .minMarkup(reseller.getMinAllowedMarkupPct())
                .maxMarkup(reseller.getMaxAllowedMarkupPct())
                .creditLimit(reseller.getCreditLimit())
                .build();
                
        eventPublisher.publishResellerApproved(event);
    }

    private void publishResellerRejectedEvent(Reseller reseller, String reason) {
        log.info("Publishing ResellerRejectedEvent for: {}", reseller.getUuid());
        
        ResellerRejectedEvent event = ResellerRejectedEvent.builder()
                .resellerUuid(reseller.getUuid())
                .userId(reseller.getUserId())
                .email(reseller.getEmail())
                .rejectionReason(reason)
                .rejectionDate(LocalDateTime.now())
                .build();
                
        eventPublisher.publishResellerRejected(event);
    }

    private void publishResellerSuspendedEvent(Reseller reseller, String reason) {
        log.info("Publishing ResellerSuspendedEvent for: {}", reseller.getUuid());
        
        ResellerSuspendedEvent event = ResellerSuspendedEvent.builder()
                .resellerUuid(reseller.getUuid())
                .userId(reseller.getUserId())
                .suspensionReason(reason)
                .suspensionDate(LocalDateTime.now())
                .build();
                
        eventPublisher.publishResellerSuspended(event);
    }

    // Helper methods for emails
    private void sendRegistrationConfirmationEmail(Reseller reseller) {
        log.info("Sending registration confirmation email to: {}", reseller.getEmail());
        // TODO: Send email
    }

    private void sendAdminNotificationEmail(Reseller reseller) {
        log.info("Sending admin notification email for new reseller: {}", reseller.getUuid());
        // TODO: Send email
    }

    private void sendApprovalEmail(Reseller reseller) {
        log.info("Sending approval email to: {}", reseller.getEmail());
        // TODO: Send email with pricing rules
    }

    private void sendRejectionEmail(Reseller reseller, String reason) {
        log.info("Sending rejection email to: {} with reason={}", reseller.getEmail(), reason);
        // TODO: Send email
    }

    private void sendSuspensionEmail(Reseller reseller, String reason) {
        log.info("Sending suspension email to: {} with reason={}", reseller.getEmail(), reason);
        // TODO: Send email
    }

    private void sendReactivationEmail(Reseller reseller) {
        log.info("Sending reactivation email to: {}", reseller.getEmail());
        // TODO: Send email
    }

    private void sendSettingsUpdateEmail(Reseller reseller) {
        log.info("Sending settings update email to: {}", reseller.getEmail());
        // TODO: Send email
    }

    // Mapper
    private ResellerProfileResponse mapToProfileResponse(Reseller reseller) {
        return ResellerProfileResponse.builder()
                .uuid(reseller.getUuid())
                .firstName(reseller.getFirstName())
                .lastName(reseller.getLastName())
                .fullName(reseller.getFullName())
                .email(reseller.getEmail())
                .imageUrl(reseller.getImageUrl())
                .phone(reseller.getPhone())
                .addressLine1(reseller.getAddressLine1())
                .addressLine2(reseller.getAddressLine2())
                .city(reseller.getCity())
                .district(reseller.getDistrict())
                .province(reseller.getProvince())
                .postalCode(reseller.getPostalCode())
                .status(reseller.getStatus().name())
                .statusMessage(getStatusMessage(reseller.getStatus()))
                .allowPriceOverride(reseller.getAllowPriceOverride())
                .minAllowedMarkupPct(reseller.getMinAllowedMarkupPct())
                .maxAllowedMarkupPct(reseller.getMaxAllowedMarkupPct())
                .creditBalance(reseller.getCreditBalance())
                .creditLimit(reseller.getCreditLimit())
                .availableCredit(reseller.getAvailableCredit())
                .registrationDate(reseller.getDateCreated())
                .isActive(reseller.getIsActive())
                .build();
    }

    private String getStatusMessage(ResellerStatus status) {
        return switch (status) {
            case PENDING -> "Your application is under review";
            case APPROVED -> "Your account is approved and active";
            case REJECTED -> "Your application was not approved";
            case SUSPENDED -> "Your account is temporarily suspended";
        };
    }
}

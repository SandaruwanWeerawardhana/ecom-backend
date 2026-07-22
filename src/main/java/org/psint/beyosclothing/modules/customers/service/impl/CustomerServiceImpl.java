package org.psint.beyosclothing.modules.customers.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.customers.dto.CreateAddressRequest;
import org.psint.beyosclothing.modules.customers.dto.CustomerResponse;
import org.psint.beyosclothing.modules.customers.dto.request.CustomerUpdatePassword;
import org.psint.beyosclothing.modules.customers.dto.request.UpdateCustomerRequestDTO;
import org.psint.beyosclothing.modules.customers.dto.response.UpdateCustomerResponse;
import org.psint.beyosclothing.modules.customers.dto.response.RoleInfoDTO;
import org.psint.beyosclothing.modules.admin.events.UpdateAdminUserDetailsEvent;
import org.psint.beyosclothing.modules.admin.service.AdminEventPublisherService;
import org.psint.beyosclothing.modules.auth.entity.User;
import org.psint.beyosclothing.modules.auth.entity.UserRole;
import org.psint.beyosclothing.modules.auth.repository.UserRepository;
import org.psint.beyosclothing.modules.auth.repository.UserRoleRepository;
import org.psint.beyosclothing.modules.customers.entity.Address;
import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.psint.beyosclothing.modules.customers.repository.AddressRepository;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.psint.beyosclothing.modules.customers.service.CustomerService;
import org.psint.beyosclothing.shared.dto.CustomerDetailsLookupRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Customer Service Implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitTemplate authRpcRabbitTemplate;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AdminEventPublisherService eventPublisher;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.rabbitmq.exchange.auth}")
    private String authExchange;

    private static final String VERIFY_PASSWORD_RPC_ROUTING_KEY = "auth.user.verify.password.rpc";
    private static final String UPDATE_PASSWORD_RPC_ROUTING_KEY = "auth.user.update.password.rpc";

    @Override
    @Transactional(value = "customerTransactionManager", readOnly = true)
    public CustomerResponse getCustomerByUuid(String customerUuid) {
        log.info("Fetching customer for customerUuid: {}", customerUuid);

        Customer customer = customerRepository.findByUuid(customerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for customerUuid: " + customerUuid));

        return mapToCustomerResponse(customer);
    }

    @Override
    @Transactional(value = "customerTransactionManager", readOnly = true)
    public Long getCustomerIdByEmail(String email) {
        log.info("Getting customer ID for email: {}", email);
        throw new UnsupportedOperationException(
            "This method is deprecated. Use CustomerQueryService.getCustomerIdByEmail() instead"
        );
    }

    @Override
    @Transactional("customerTransactionManager")
    public CustomerResponse addAddress(String customerUuid, CreateAddressRequest request) {
        log.info("Adding address for customerUuid: {}", customerUuid);

        Customer customer = customerRepository.findByUuid(customerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        if (Boolean.TRUE.equals(request.getIsDefault()) || customer.getAddresses().isEmpty()) {
            customer.getAddresses().forEach(addr -> addr.setIsDefault(false));
        }

        Address address = Address.builder()
                .customer(customer)
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .district(request.getDistrict())
                .country(request.getCountry())
                .province(request.getProvince())
                .postalCode(request.getPostalCode())
                .isDefault(request.getIsDefault() != null ? request.getIsDefault() : customer.getAddresses().isEmpty())
                .build();

        addressRepository.save(address);
        log.info("Address added successfully for customer: {}", customer.getId());

        return mapToCustomerResponse(customer);
    }

    @Override
    @Transactional("customerTransactionManager")
    public void setDefaultAddress(String customerUuid, Long addressId) {
        log.info("Setting default address {} for customerUuid: {}", addressId, customerUuid);

        Customer customer = customerRepository.findByUuid(customerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getCustomer().getId().equals(customer.getId())) {
            throw new BadRequestException("Address does not belong to this customer");
        }

        customer.getAddresses().forEach(addr -> addr.setIsDefault(false));

        address.setIsDefault(true);
        addressRepository.save(address);

        log.info("Default address updated for customer: {}", customer.getId());
    }

    @Override
    @Transactional("customerTransactionManager")
    public void deleteAddress(String customerUuid, Long addressId) {
        log.info("Deleting address {} for customerUuid: {}", addressId, customerUuid);

        Customer customer = customerRepository.findByUuid(customerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getCustomer().getId().equals(customer.getId())) {
            throw new BadRequestException("Address does not belong to this customer");
        }

        address.setIsActive(false);
        addressRepository.save(address);

        log.info("Address deleted for customer: {}", customer.getId());
    }

    @Override
    @Transactional(value = "customerTransactionManager", readOnly = true)
    public Page<CustomerResponse> getAllCustomers(Pageable pageable) {
        log.info("Fetching paginated customers, page: {} size: {}", pageable.getPageNumber(), pageable.getPageSize());

        Page<Customer> customerPage = customerRepository.findAllByIsActiveTrue(pageable);
        List<Customer> customers = customerPage.getContent();

        // collect userIds from customers
        Set<Long> userIds = customers.stream()
                .map(Customer::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        log.debug("Customer page contains userIds: {}", userIds);

        Map<Long, String> emailMap = new HashMap<>();

        if (!userIds.isEmpty()) {
            try {
                CustomerDetailsLookupRequest req = new CustomerDetailsLookupRequest(new ArrayList<>(userIds));
                log.debug("Sending CustomerDetailsLookupRequest via RabbitMQ for userIds: {}", req.getUserIds());
                Object reply = rabbitTemplate.convertSendAndReceive("beyos.exchange.customer", "customer.details.request", req);
                log.debug("RPC reply (raw): {}", reply);
                if (reply instanceof Map) {
                    Map<?, ?> raw = (Map<?, ?>) reply;

                    // Case A: reply is APIResponse with nested data.content list
                    if (raw.containsKey("data")) {
                        Object dataObj = raw.get("data");
                        if (dataObj instanceof Map) {
                            Map<?, ?> dataMap = (Map<?, ?>) dataObj;
                            Object contentObj = dataMap.get("content");
                            if (contentObj instanceof List) {
                                List<?> contentList = (List<?>) contentObj;
                                for (Object item : contentList) {
                                    if (item instanceof Map) {
                                        Map<?, ?> itemMap = (Map<?, ?>) item;
                                        Object uid = itemMap.get("userId");
                                        Object emailVal = itemMap.get("email");
                                        if (uid != null && emailVal != null) {
                                            try {
                                                Long key = Long.valueOf(uid.toString());
                                                emailMap.put(key, emailVal.toString());
                                            } catch (NumberFormatException nfe) {
                                                log.debug("Skipping non-numeric userId in content item: {}", uid);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Case B: reply might be a direct map of id->email
                    if (emailMap.isEmpty()) {
                        for (Map.Entry<?, ?> e : raw.entrySet()) {
                            Object k = e.getKey();
                            Object v = e.getValue();
                            if (k != null && v != null) {
                                try {
                                    Long key = Long.valueOf(k.toString());
                                    emailMap.put(key, v.toString());
                                } catch (NumberFormatException ex) {
                                    // not numeric key - continue
                                }
                            }
                        }
                    }

                    log.debug("Email map populated from RPC: {}", emailMap);
                } else {
                    log.debug("Customer details RPC returned no map reply: {}", reply);
                }
            } catch (Exception ex) {
                log.warn("Failed to fetch customer emails via RabbitMQ RPC", ex);
            }

            // fallback: if RPC didn't populate the emailMap, query UserRepository directly
            if (emailMap.isEmpty()) {
                try {
                    log.debug("Falling back to UserRepository.findAllById for userIds: {}", userIds);
                    Iterable<User> users = userRepository.findAllById(userIds);
                    for (User u : users) {
                        log.debug("Found user id={} email={}", u.getId(), u.getEmail());
                        if (u.getEmail() != null) {
                            emailMap.put(u.getId(), u.getEmail());
                        }
                    }
                    log.debug("Email map after DB fallback: {}", emailMap);
                } catch (Exception dbEx) {
                    log.warn("Failed to fetch user emails from auth DB as fallback", dbEx);
                }
            }
         }

        // set transient email on customers using emailMap (may be empty)
        List<CustomerResponse> content = new ArrayList<>(customers.size());
        for (Customer c : customers) {
            c.setEmail(emailMap.get(c.getUserId()));
            content.add(mapToCustomerResponse(c));
        }

        return new PageImpl<>(content, pageable, customerPage.getTotalElements());
    }

    @Override
    @Transactional("customerTransactionManager")
    public UpdateCustomerResponse updateCustomer(String customerUuid, UpdateCustomerRequestDTO request, String updatedBy) {
        log.info("Updating customer with UUID: {}", customerUuid);

        try {
            Customer customer = customerRepository.findByUuid(customerUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found for customerUuid: " + customerUuid));

            // Find user in auth DB for email/password/role updates
            User user = userRepository.findById(customer.getUserId()).orElse(null);
            String currentEmail = user != null ? user.getEmail() : null;

            // Update customer details in customer DB
            if (request.getFirstName() != null) {
                customer.setFirstName(request.getFirstName());
            }
            if (request.getLastName() != null) {
                customer.setLastName(request.getLastName());
            }
            if (request.getPhone() != null) {
                customer.setPhoneNumber(request.getPhone());
            }

            customer = customerRepository.save(customer);
            log.info("Customer details updated in customer DB for UUID: {}", customerUuid);

            // Handle email update directly in auth DB
            if (request.getEmail() != null && !request.getEmail().isBlank() && user != null) {
                if (!request.getEmail().equals(currentEmail)) {
                    log.info("Email updated for customer {}, syncing to Auth DB", customerUuid);
                    user.setEmail(request.getEmail());
                    userRepository.save(user);

                    // Also publish event for any downstream consumers
                    UpdateAdminUserDetailsEvent detailsEvent = UpdateAdminUserDetailsEvent.builder()
                            .userId(customer.getUserId())
                            .email(request.getEmail())
                            .updatedBy(updatedBy)
                            .updatedAt(LocalDateTime.now())
                            .build();
                    eventPublisher.publishUpdateAdminUserDetailsEvent(detailsEvent);
                    log.info("Published UpdateAdminUserDetailsEvent for userId: {}", customer.getUserId());
                }
            }


            // Handle role update directly in auth DB
            if (request.getRoleCode() != null && !request.getRoleCode().isBlank() && user != null) {
                UserRole role = userRoleRepository.findByRoleCode(request.getRoleCode())
                        .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRoleCode()));
                user.setUserRoleId(role.getId());
                userRepository.save(user);
                log.info("Role updated successfully in Auth DB for userId: {} to role: {}", customer.getUserId(), request.getRoleCode());
            }

            log.info("Customer updated successfully: {}", customerUuid);
            return mapToUpdateCustomerResponse(customer, user);

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating customer: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update customer: " + e.getMessage());
        }
    }

    @Override
    @Transactional("customerTransactionManager")
    public UpdateCustomerResponse updateCustomerPassword(String customerUuid, CustomerUpdatePassword request) {
        log.info("Updating password for customer with UUID: {}", customerUuid);

        try {
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                throw new BadRequestException("Current password is required");
            }
            if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
                throw new BadRequestException("New password is required");
            }
            if (request.getCurrentPassword().equals(request.getNewPassword())) {
                throw new BadRequestException("New password must be different from current password");
            }

            Customer customer = customerRepository.findByUuid(customerUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found for customerUuid: " + customerUuid));

            // Verify current password via Auth RPC
            Map<String, Object> verifyRequest = new HashMap<>();
            verifyRequest.put("userId", customer.getUserId());
            verifyRequest.put("currentPassword", request.getCurrentPassword());

            Object verifyResponse = authRpcRabbitTemplate.convertSendAndReceive(
                    authExchange,
                    VERIFY_PASSWORD_RPC_ROUTING_KEY,
                    verifyRequest
            );

            boolean isCurrentPasswordValid = false;
            if (verifyResponse instanceof Map<?, ?> responseMap) {
                Object successObj = responseMap.get("success");
                isCurrentPasswordValid = successObj instanceof Boolean && (Boolean) successObj;
            }

            if (!isCurrentPasswordValid) {
                throw new BadRequestException("Current password is incorrect");
            }

            // Update password via Auth RPC
            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("userId", customer.getUserId());
            updateRequest.put("newPassword", passwordEncoder.encode(request.getNewPassword()));

            Object updateResponse = authRpcRabbitTemplate.convertSendAndReceive(
                    authExchange,
                    UPDATE_PASSWORD_RPC_ROUTING_KEY,
                    updateRequest
            );

            boolean updateSuccess = false;
            if (updateResponse instanceof Map<?, ?> responseMap) {
                Object successObj = responseMap.get("success");
                updateSuccess = successObj instanceof Boolean && (Boolean) successObj;
            }

            if (!updateSuccess) {
                throw new RuntimeException("Failed to update password in auth service");
            }

            // Fetch updated user for response mapping
            User user = userRepository.findById(customer.getUserId()).orElse(null);

            log.info("Customer password updated successfully for UUID: {}", customerUuid);
            return mapToUpdateCustomerResponse(customer, user);

        } catch (BadRequestException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating customer password for UUID {}: {}", customerUuid, e.getMessage(), e);
            throw new RuntimeException("Failed to update customer password: " + e.getMessage());
        }
    }

    private UpdateCustomerResponse mapToUpdateCustomerResponse(Customer customer, User user) {
        RoleInfoDTO roleInfo = null;
        if (user != null && user.getUserRoleId() != null) {
            UserRole userRole = userRoleRepository.findById(user.getUserRoleId()).orElse(null);
            if (userRole != null) {
                roleInfo = RoleInfoDTO.builder()
                        .roleId(userRole.getId())
                        .roleCode(userRole.getRoleCode())
                        .roleName(userRole.getRoleName())
                        .description(userRole.getDescription())
                        .userType(userRole.getUserType())
                        .build();
            }
        }

        return UpdateCustomerResponse.builder()
                .id(customer.getId())
                .uuid(customer.getUuid())
                .userId(customer.getUserId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .fullName(customer.getFullName())
                .email(user != null ? user.getEmail() : null)
                .phone(customer.getPhone())
                .role(roleInfo)
                .isActive(customer.getIsActive())
                .createdAt(customer.getDateCreated())
                .updatedAt(customer.getDateUpdated())
                .build();
    }

    private CustomerResponse mapToCustomerResponse(Customer customer) {
        List<CustomerResponse.AddressDTO> addressDTOs = customer.getAddresses().stream()
                .filter(Address::getIsActive)
                .map(addr -> CustomerResponse.AddressDTO.builder()
                        .id(addr.getId())
                        .addressLine1(addr.getAddressLine1())
                        .addressLine2(addr.getAddressLine2())
                        .city(addr.getCity())
                        .district(addr.getDistrict())
                        .country(addr.getCountry())
                        .postalCode(addr.getPostalCode())
                        .isDefault(addr.getIsDefault())
                        .build())
                .toList();

        return CustomerResponse.builder()
                .uuid(customer.getUuid())
                .userId(customer.getUserId())
                .fullName(customer.getFullName())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .isActive(customer.getIsActive())
                .dateCreated(customer.getDateCreated())
                .addresses(addressDTOs)
                .build();
    }
}

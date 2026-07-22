package org.psint.beyosclothing.modules.customers.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.auth.entity.User;
import org.psint.beyosclothing.modules.auth.repository.UserRepository;
import org.psint.beyosclothing.modules.customers.entity.Address;
import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.psint.beyosclothing.modules.pos.dto.response.PosCustomerSimpleResponse;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consumer for handling simple customer list requests from POS module
 * Returns all active customers from customer module (ONLINE source)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerSimpleListConsumer {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.customer-simple-list-request:customer.simple.list.request}")
    @Transactional(value = "customerTransactionManager", readOnly = true)
    public List<PosCustomerSimpleResponse> handleSimpleListRequest(String requestId) {
        try {
            log.debug("Received simple customer list request: {}", requestId);

            // Get all active customers
            List<Customer> customers = customerRepository.findAllByIsActiveTrue(Pageable.unpaged()).getContent();
            Map<Long, String> emailByUserId = getEmailByUserId(customers);

            List<PosCustomerSimpleResponse> response = customers.stream()
                    .map(customer -> mapToSimpleResponse(customer, emailByUserId.get(customer.getUserId())))
                    .collect(Collectors.toList());

            log.info("Returning {} ONLINE customers for simple list request: {}", response.size(), requestId);
            return response;

        } catch (Exception e) {
            log.error("Error processing simple customer list request: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private Map<Long, String> getEmailByUserId(List<Customer> customers) {
        Set<Long> userIds = customers.stream()
                .map(Customer::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, String> emailByUserId = new HashMap<>();
        try {
            Iterable<User> users = userRepository.findAllById(userIds);
            for (User user : users) {
                if (user.getEmail() != null) {
                    emailByUserId.put(user.getId(), user.getEmail());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch ONLINE customer emails for simple list: {}", e.getMessage());
        }

        return emailByUserId;
    }

    private PosCustomerSimpleResponse mapToSimpleResponse(Customer customer, String email) {
        List<PosCustomerSimpleResponse.CustomerAddress> addresses = customer.getAddresses().stream()
                .filter(Address::getIsActive)
                .map(this::mapToAddressResponse)
                .collect(Collectors.toList());

        Address defaultAddress = customer.getAddresses().stream()
                .filter(Address::getIsActive)
                .filter(address -> Boolean.TRUE.equals(address.getIsDefault()))
                .findFirst()
                .orElseGet(() -> customer.getAddresses().stream()
                        .filter(Address::getIsActive)
                        .findFirst()
                        .orElse(null));

        return PosCustomerSimpleResponse.builder()
                .id(customer.getId())
                .uuid(customer.getUuid())
                .fullName(customer.getFullName())
                .phone(customer.getPhone())
                .address(defaultAddress != null ? defaultAddress.getAddressLine1() : null)
                .city(defaultAddress != null ? defaultAddress.getCity() : null)
                .province(defaultAddress != null ? defaultAddress.getProvince() : null)
                .district(defaultAddress != null ? defaultAddress.getDistrict() : null)
                .zipCode(defaultAddress != null ? defaultAddress.getPostalCode() : null)
                .addresses(addresses)
                .source("ONLINE")
                .build();
    }

    private PosCustomerSimpleResponse.CustomerAddress mapToAddressResponse(Address address) {
        return PosCustomerSimpleResponse.CustomerAddress.builder()
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .province(address.getProvince())
                .district(address.getDistrict())
                .country(address.getCountry())
                .postalCode(address.getPostalCode())
                .isDefault(address.getIsDefault())
                .build();
    }
}


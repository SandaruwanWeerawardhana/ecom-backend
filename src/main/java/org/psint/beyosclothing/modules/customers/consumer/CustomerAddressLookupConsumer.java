package org.psint.beyosclothing.modules.customers.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.shared.dto.CustomerAddressLookupRequest;
import org.psint.beyosclothing.shared.dto.CustomerAddressLookupResponse;
import org.psint.beyosclothing.modules.customers.entity.Address;
import org.psint.beyosclothing.modules.customers.repository.AddressRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer for customer address lookup requests from Order module
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerAddressLookupConsumer {

    private final AddressRepository addressRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.customer-address-lookup-request}")
    @Transactional(value = "customerTransactionManager", readOnly = true)
    public CustomerAddressLookupResponse handleAddressLookupRequest(CustomerAddressLookupRequest request) {
        log.info("Received address lookup request - Request ID: {}, Address ID: {}",
                request.getRequestId(), request.getAddressId());

        try {
            Address address = resolveAddress(request);

            if (address == null || !address.getIsActive()) {
                log.warn("Address not found or inactive - Address ID: {}, Customer ID: {}",
                        request.getAddressId(), request.getCustomerId());
                return CustomerAddressLookupResponse.builder()
                        .requestId(request.getRequestId())
                        .found(false)
                        .build();
            }

            // Verify address belongs to customer if customer ID provided
            if (request.getCustomerId() != null && !address.getCustomer().getId().equals(request.getCustomerId())) {
                log.warn("Address does not belong to customer - Address ID: {}, Customer ID: {}",
                        request.getAddressId(), request.getCustomerId());
                return CustomerAddressLookupResponse.builder()
                        .requestId(request.getRequestId())
                        .found(false)
                        .build();
            }

            // ✅ Access customer data inside transaction to avoid LazyInitializationException
            String fullName = address.getCustomer().getFullName();
            String phoneNumber = address.getCustomer().getPhoneNumber();
            String email = address.getCustomer().getEmail();

            log.info("Address found - ID: {}, Customer ID: {}, Full Name: {}", 
                    address.getId(), address.getCustomer().getId(), fullName);

            return CustomerAddressLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .found(true)
                    .addressId(address.getId())
                    .fullName(fullName)
                    .phoneNumber(phoneNumber)
                    .email(email)
                    .addressLine1(address.getAddressLine1())
                    .addressLine2(address.getAddressLine2())
                    .city(address.getCity())
                    .province(address.getDistrict())
                    .postalCode(address.getPostalCode())
                    .country(address.getCountry())
                    .build();

        } catch (Exception e) {
            log.error("Error looking up address - Request ID: {}, Address ID: {}", 
                    request.getRequestId(), request.getAddressId(), e);
            return CustomerAddressLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .found(false)
                    .build();
        }
    }

    /**
     * Resolves the address to return: by explicit id when one is provided, otherwise the
     * customer's default saved address. Returns null when neither yields an address.
     */
    private Address resolveAddress(CustomerAddressLookupRequest request) {
        if (request.getAddressId() != null) {
            return addressRepository.findById(request.getAddressId()).orElse(null);
        }
        if (request.getCustomerId() != null) {
            return addressRepository.findDefaultAddressByCustomerId(request.getCustomerId()).orElse(null);
        }
        return null;
    }
}
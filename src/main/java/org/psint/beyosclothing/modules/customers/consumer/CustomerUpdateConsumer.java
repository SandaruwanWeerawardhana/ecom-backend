package org.psint.beyosclothing.modules.customers.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.customers.entity.Address;
import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.psint.beyosclothing.modules.customers.repository.AddressRepository;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.psint.beyosclothing.modules.pos.dto.request.PosCustomerUpdateRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosCustomerDetailsResponse;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerUpdateConsumer {

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.customer-update-request:customer.update.request}")
    @Transactional("customerTransactionManager")
    public PosCustomerDetailsResponse handleCustomerUpdate(PosCustomerUpdateRequest request) {
        try {
            log.debug("Received ONLINE customer update request for ID: {}", request.getCustomerId());

            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found with ID: " + request.getCustomerId()));

            if (request.getFullName() != null) {
                String[] parts = request.getFullName().trim().split("\\s+", 2);
                customer.setFirstName(parts[0]);
                customer.setLastName(parts.length > 1 ? parts[1] : null);
            }

            if (request.getPhone() != null) {
                customer.setPhoneNumber(request.getPhone().trim());
            }

            customerRepository.save(customer);

            updateOrCreateDefaultAddress(customer, request);

            log.info("ONLINE customer updated successfully for ID: {}", request.getCustomerId());
            return mapToDetailsResponse(customer);

        } catch (Exception e) {
            log.error("Failed to update ONLINE customer ID {}: {}", request.getCustomerId(), e.getMessage(), e);
            return null;
        }
    }

    // O(n) over active addresses — typically a small set per customer
    private void updateOrCreateDefaultAddress(Customer customer, PosCustomerUpdateRequest request) {
        if (request.getAddress() == null) {
            return;
        }

        Optional<Address> defaultAddress = addressRepository.findDefaultAddressByCustomerId(customer.getId());

        if (defaultAddress.isPresent()) {
            Address addr = defaultAddress.get();
            addr.setAddressLine1(request.getAddress());
            if (request.getCity() != null)     addr.setCity(request.getCity());
            if (request.getProvince() != null) addr.setProvince(request.getProvince());
            if (request.getDistrict() != null) addr.setDistrict(request.getDistrict());
            if (request.getZipCode() != null)  addr.setPostalCode(request.getZipCode());
            addressRepository.save(addr);
        } else {
            Address newAddress = Address.builder()
                    .customer(customer)
                    .addressLine1(request.getAddress())
                    .city(request.getCity() != null ? request.getCity() : "")
                    .province(request.getProvince() != null ? request.getProvince() : "")
                    .district(request.getDistrict())
                    .postalCode(request.getZipCode())
                    .country("LK")
                    .isDefault(true)
                    .build();
            addressRepository.save(newAddress);
        }
    }

    private PosCustomerDetailsResponse mapToDetailsResponse(Customer customer) {
        List<Address> activeAddresses = addressRepository.findActiveAddressesByCustomerId(customer.getId());

        List<PosCustomerDetailsResponse.CustomerAddress> addressResponses = activeAddresses.stream()
                .map(addr -> PosCustomerDetailsResponse.CustomerAddress.builder()
                        .id(addr.getId())
                        .addressLine1(addr.getAddressLine1())
                        .city(addr.getCity())
                        .district(addr.getDistrict())
                        .country(addr.getCountry())
                        .postalCode(addr.getPostalCode())
                        .isDefault(addr.getIsDefault())
                        .build())
                .collect(Collectors.toList());

        return PosCustomerDetailsResponse.builder()
                .id(customer.getId())
                .uuid(customer.getUuid())
                .userId(customer.getUserId())
                .fullName(customer.getFullName())
                .phone(customer.getPhone())
                .isActive(customer.getIsActive())
                .loyaltyPoints(0)
                .addresses(addressResponses)
                .build();
    }
}

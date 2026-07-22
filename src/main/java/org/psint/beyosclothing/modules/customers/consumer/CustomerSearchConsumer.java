package org.psint.beyosclothing.modules.customers.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.psint.beyosclothing.modules.pos.dto.request.CustomerSearchRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosCustomerSearchResponse;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerSearchConsumer {

    private final CustomerRepository customerRepository;

    @RabbitListener(queues = "customer.search.request")
    @Transactional(value = "customerTransactionManager", readOnly = true)
    public List<PosCustomerSearchResponse> handleCustomerSearch(CustomerSearchRequest request) {
        try {
            log.debug("Received customer search request - query: {}, limit: {}", request.getQuery(), request.getLimit());

            if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
                log.warn("Empty search query received");
                return Collections.emptyList();
            }

            int limit = request.getLimit() != null && request.getLimit() > 0 ? request.getLimit() : 10;
            PageRequest pageRequest = PageRequest.of(0, Math.min(limit, 50));

            List<Customer> customers = customerRepository.searchActiveCustomers(request.getQuery(), pageRequest);

            List<PosCustomerSearchResponse> response = customers.stream()
                    .map(this::mapToSearchResponse)
                    .toList();

            log.info("Customer search completed - query: {}, results: {}", request.getQuery(), response.size());
            return response;

        } catch (Exception e) {
            log.error("Failed to process customer search request: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private PosCustomerSearchResponse mapToSearchResponse(Customer customer) {
        return PosCustomerSearchResponse.builder()
                .id(customer.getId())
                .userId(customer.getUserId())
                .fullName(customer.getFullName())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .isActive(customer.getIsActive())
                .loyaltyPoints(0)
                .build();
    }
}
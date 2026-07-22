package org.psint.beyosclothing.modules.customers.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.common.constants.ResponseCode;
import org.psint.beyosclothing.modules.customers.dto.external.CustomerDetailsLookupRequest;
import org.psint.beyosclothing.modules.customers.dto.external.CustomerDetailsLookupResponse;
import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerDetailsConsumer {

    private final CustomerRepository customerRepository;
    private final RabbitTemplate rabbitTemplate; // use RabbitMQ to fetch auth emails

    @Value("${app.rabbitmq.exchange.auth:beyos.exchange.auth}")
    private String authExchange;

    @Value("${app.rabbitmq.routing-keys.auth.customer-lookup:customer.lookup.request}")
    private String authCustomerLookupRoutingKey;

    private static final int DEFAULT_MAX_CUSTOMERS = 1000;
    private static final int DEFAULT_PAGE = 0;

    @RabbitListener(queues = "${app.rabbitmq.queue.customer-details-request:customer.details.request.queue}")
    @Transactional(value = "customerTransactionManager", readOnly = true)
    public APIResponse<PageResponse<CustomerDetailsLookupResponse>> handleCustomerDetailsRequest(CustomerDetailsLookupRequest request) {
        String reqId = request != null ? request.getRequestId() : "<unknown>";
        Boolean activeOnly = request != null ? request.getActiveOnly() : null;

        log.info("Received customer details request - requestId={}, activeOnly={}", reqId, activeOnly);

        try {
            int page = request != null && request.getPage() != null ? request.getPage() : DEFAULT_PAGE;
            int size = request != null && request.getSize() != null ? Math.min(request.getSize(), DEFAULT_MAX_CUSTOMERS) : DEFAULT_MAX_CUSTOMERS;
            Pageable pageable = PageRequest.of(page, size);

            Page<Customer> customerPage;

            if (request != null && request.getCustomerIds() != null && !request.getCustomerIds().isEmpty()) {
                List<Customer> customers = customerRepository.findAllById(request.getCustomerIds());
                customerPage = new org.springframework.data.domain.PageImpl<>(customers, pageable, customers.size());
            } else if (request != null && request.getCustomerUuids() != null && !request.getCustomerUuids().isEmpty()) {
                List<String> uuids = request.getCustomerUuids();
                List<Customer> customers = customerRepository.findAllByUuidIn(uuids);
                customerPage = new org.springframework.data.domain.PageImpl<>(customers, pageable, customers.size());
            } else if (Boolean.TRUE.equals(activeOnly)) {
                customerPage = customerRepository.findAllByIsActiveTrue(pageable);
            } else {
                customerPage = customerRepository.findAll(pageable);
            }

            List<Customer> customers = customerPage.getContent();

            if (customers.isEmpty()) {
                log.info("Customer details request {}: no customers found", reqId);
                return APIResponse.error(ResponseCode.NOT_FOUND, "No customers found");
            }

            boolean includeAddresses = request != null && Boolean.TRUE.equals(request.getIncludeAddresses());

            // Collect userIds and fetch emails from Auth service over RabbitMQ
            Set<Long> userIds = customers.stream()
                    .map(Customer::getUserId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());

            Map<Long, String> emailMap = new HashMap<>();

            if (!userIds.isEmpty()) {
                try {
                    Map<String, Object> lookupReq = new HashMap<>();
                    lookupReq.put("userIds", new ArrayList<>(userIds));

                    log.debug("Requesting user emails from Auth service for userIds={}", userIds);
                    Object reply = rabbitTemplate.convertSendAndReceive(authExchange, authCustomerLookupRoutingKey, lookupReq);
                    log.debug("Auth lookup reply (raw): {}", reply);

                    if (reply instanceof Map) {
                        Map<?, ?> raw = (Map<?, ?>) reply;

                        // If reply is APIResponse-like with data.content list
                        if (raw.containsKey("data")) {
                            Object dataObj = raw.get("data");
                            if (dataObj instanceof Map) {
                                Object contentObj = ((Map<?, ?>) dataObj).get("content");
                                if (contentObj instanceof List) {
                                    for (Object item : (List<?>) contentObj) {
                                        if (item instanceof Map) {
                                            Object uid = ((Map<?, ?>) item).get("userId");
                                            Object emailVal = ((Map<?, ?>) item).get("email");
                                            if (uid != null && emailVal != null) {
                                                try {
                                                    Long key = Long.valueOf(uid.toString());
                                                    emailMap.put(key, emailVal.toString());
                                                } catch (NumberFormatException ignore) {
                                                    // skip
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Fallback: if not filled, maybe reply is simple id->email map
                        if (emailMap.isEmpty()) {
                            for (Map.Entry<?, ?> e : raw.entrySet()) {
                                Object k = e.getKey();
                                Object v = e.getValue();
                                if (k != null && v != null) {
                                    try {
                                        Long key = Long.valueOf(k.toString());
                                        emailMap.put(key, v.toString());
                                    } catch (NumberFormatException ignore) {
                                        // not numeric key
                                    }
                                }
                            }
                        }
                    } else {
                        log.debug("Auth lookup returned unexpected reply type: {}", (reply == null ? "null" : reply.getClass()));
                    }

                    log.debug("Email map from Auth service: {}", emailMap);
                } catch (Exception e) {
                    log.warn("Failed to fetch user emails from Auth service via RabbitMQ", e);
                }
            }

            List<CustomerDetailsLookupResponse> respList = new ArrayList<>();
            for (Customer c : customers) {
                List<CustomerDetailsLookupResponse.Address> addresses = Collections.emptyList();
                if (includeAddresses && c.getAddresses() != null && !c.getAddresses().isEmpty()) {
                    addresses = c.getAddresses().stream()
                            .map(a -> CustomerDetailsLookupResponse.Address.builder()
                                    .id(a.getId())
                                    .addressLine1(a.getAddressLine1())
                                    .addressLine2(a.getAddressLine2())
                                    .city(a.getCity())
                                    .district(a.getDistrict())
                                    .country(a.getCountry())
                                    .postalCode(a.getPostalCode())
                                    .isDefault(a.getIsDefault())
                                    .build())
                            .collect(Collectors.toList());
                }

                String email = emailMap.getOrDefault(c.getUserId(), c.getEmail());

                CustomerDetailsLookupResponse resp = CustomerDetailsLookupResponse.builder()
                        .id(c.getId())
                        .uuid(c.getUuid())
                        .userId(c.getUserId())
                        .fullName(c.getFullName())
                        .phone(c.getPhone())
                        .email(email)
                        .isActive(c.getIsActive())
                        .dateCreated(c.getDateCreated())
                        .addresses(addresses)
                        .build();

                respList.add(resp);
            }

            PageResponse<CustomerDetailsLookupResponse> pageResponse = PageResponse.<CustomerDetailsLookupResponse>builder()
                    .content(respList)
                    .pageNumber(customerPage.getNumber())
                    .pageSize(customerPage.getSize())
                    .totalElements(customerPage.getTotalElements())
                    .totalPages(customerPage.getTotalPages())
                    .last(customerPage.isLast())
                    .first(customerPage.isFirst())
                    .empty(customerPage.isEmpty())
                    .build();

            log.info("Customer details request {}: returning {} customers", reqId, respList.size());
            return APIResponse.success("Customer details retrieved", pageResponse);

        } catch (Exception e) {
            log.error("Error processing customer details request {}", reqId, e);
            return APIResponse.error(ResponseCode.INTERNAL_ERROR, "Failed to retrieve customer details");
        }
    }
}

package org.psint.beyosclothing.modules.orders.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.orders.dto.response.AdminOrderDetailResponse;
import org.psint.beyosclothing.modules.orders.dto.response.AdminOrderListResponse;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderItemEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderShippingAddressEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderStatusHistoryEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderItemRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderShippingAddressRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderStatusHistoryRepository;
import org.psint.beyosclothing.modules.orders.repository.projection.AdminOrderListView;
import org.psint.beyosclothing.modules.pos.repository.PosCartRepository;
import org.psint.beyosclothing.modules.pos.repository.PosCustomerRepository;
import org.psint.beyosclothing.modules.pos.repository.projection.PosCartOrderView;
import org.psint.beyosclothing.modules.sms.service.OrderSmsNotificationService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceImplTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderItemRepository orderItemRepository;

    @Mock
    OrderShippingAddressRepository shippingAddressRepository;

    @Mock
    OrderStatusHistoryRepository statusHistoryRepository;

    @Mock
    PosCartRepository posCartRepository;

    @Mock
    PosCustomerRepository posCustomerRepository;

    @Mock
    RabbitTemplate rabbitTemplate;

    @Mock
    OrderSmsNotificationService smsNotificationService;

    @InjectMocks
    AdminOrderServiceImpl service;

    @Captor
    ArgumentCaptor<OrderStatusHistoryEntity> historyCaptor;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles setup
    }

    @Test
    void getAllOrders_ordersOnly_paginatesDirectly() {
        // status=PENDING excludes POS carts, so the page is fetched straight from the orders table
        AdminOrderListView orderView = orderView(1L, "order-1", "BYS-001",
                LocalDateTime.of(2026, 5, 19, 10, 0), 10L);

        when(orderRepository.countOrdersWithFilters(
                eq(OrderEntity.OrderStatus.PENDING),
                eq(OrderEntity.OrderSource.ONLINE),
                eq("CUSTOMER"),
                eq(LocalDate.of(2026, 5, 1).atStartOfDay()),
                eq(LocalDate.of(2026, 5, 31).atTime(23, 59, 59, 999_999_999)),
                eq("phone")
        )).thenReturn(1L);
        when(orderRepository.findOrderViewsWithFilters(
                eq(OrderEntity.OrderStatus.PENDING),
                eq(OrderEntity.OrderSource.ONLINE),
                eq("CUSTOMER"),
                eq(LocalDate.of(2026, 5, 1).atStartOfDay()),
                eq(LocalDate.of(2026, 5, 31).atTime(23, 59, 59, 999_999_999)),
                eq("phone"),
                eq(PageRequest.of(0, 20))
        )).thenReturn(List.of(orderView));
        when(shippingAddressRepository.findByOrderIdIn(List.of(1L))).thenReturn(
                List.of(OrderShippingAddressEntity.builder().orderId(1L).fullName("John Doe").build())
        );

        PageResponse<AdminOrderListResponse> response = service.getAllOrders(
                "phone",
                OrderEntity.OrderStatus.PENDING,
                OrderEntity.OrderSource.ONLINE,
                "customer",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                0,
                20
        );

        assertEquals(1, response.getContent().size());
        assertEquals("order-1", response.getContent().getFirst().getOrderUuid());
        assertEquals("John Doe", response.getContent().getFirst().getCustomerName());
        assertEquals("CUSTOMER", response.getContent().getFirst().getOrderFrom());
        assertEquals(1, response.getTotalElements());
        assertFalse(response.isEmpty());
        verifyNoInteractions(posCartRepository);
    }

    @Test
    void getAllOrders_mergesOrdersAndPosCartsNewestFirst() {
        AdminOrderListView orderView = orderView(1L, "order-1", "BYS-001",
                LocalDateTime.of(2026, 5, 19, 10, 0), 10L);
        PosCartOrderView cartView = cartView("cart-1", LocalDateTime.of(2026, 5, 20, 9, 0));

        when(orderRepository.countOrdersWithFilters(any(), any(), any(), any(), any(), any()))
                .thenReturn(1L);
        when(posCartRepository.countCompletedCartOrders(any(), any(), any())).thenReturn(1L);
        when(orderRepository.findOrderViewsWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(orderView));
        when(posCartRepository.findCompletedCartOrders(any(), any(), any(), any()))
                .thenReturn(List.of(cartView));
        when(shippingAddressRepository.findByOrderIdIn(List.of(1L))).thenReturn(
                List.of(OrderShippingAddressEntity.builder().orderId(1L).fullName("John Doe").build())
        );

        PageResponse<AdminOrderListResponse> response = service.getAllOrders(
                null, null, null, null, null, null, 0, 20);

        assertEquals(2, response.getContent().size());
        assertEquals("cart-1", response.getContent().getFirst().getOrderUuid());
        assertEquals("Walk-in", response.getContent().getFirst().getCustomerName());
        assertEquals("order-1", response.getContent().getLast().getOrderUuid());
        assertEquals(2, response.getTotalElements());
    }

    @Test
    void getAllOrders_pageBeyondTotal_skipsRowFetch() {
        when(orderRepository.countOrdersWithFilters(any(), any(), any(), any(), any(), any()))
                .thenReturn(3L);
        when(posCartRepository.countCompletedCartOrders(any(), any(), any())).thenReturn(2L);

        PageResponse<AdminOrderListResponse> response = service.getAllOrders(
                null, null, null, null, null, null, 5, 20);

        assertEquals(0, response.getContent().size());
        assertEquals(5, response.getTotalElements());
        verify(orderRepository, never())
                .findOrderViewsWithFilters(any(), any(), any(), any(), any(), any(), any());
        verify(posCartRepository, never()).findCompletedCartOrders(any(), any(), any(), any());
    }

    private static AdminOrderListView orderView(Long id, String uuid, String orderNumber,
                                                LocalDateTime createdAt, Long customerId) {
        return new AdminOrderListView() {
            @Override public Long getId() { return id; }
            @Override public String getUuid() { return uuid; }
            @Override public String getOrderNumber() { return orderNumber; }
            @Override public LocalDateTime getCreatedAt() { return createdAt; }
            @Override public BigDecimal getSubtotal() { return new BigDecimal("90.00"); }
            @Override public BigDecimal getTotal() { return new BigDecimal("100.00"); }
            @Override public OrderEntity.OrderSource getSource() { return OrderEntity.OrderSource.ONLINE; }
            @Override public Long getCustomerId() { return customerId; }
            @Override public Long getResellerId() { return null; }
            @Override public OrderEntity.OrderStatus getStatus() { return OrderEntity.OrderStatus.PENDING; }
            @Override public OrderEntity.PaymentStatus getPaymentStatus() { return OrderEntity.PaymentStatus.UNPAID; }
        };
    }

    /** Walk-in cart: no customer id, so the list shows the "Walk-in" placeholder name. */
    private static PosCartOrderView cartView(String uuid, LocalDateTime createdAt) {
        return new PosCartOrderView() {
            @Override public String getUuid() { return uuid; }
            @Override public LocalDateTime getCreatedAt() { return createdAt; }
            @Override public BigDecimal getSubtotal() { return new BigDecimal("45.00"); }
            @Override public BigDecimal getTotal() { return new BigDecimal("50.00"); }
            @Override public Long getCustomerId() { return null; }
        };
    }

    @Test
    void getPendingOrders() {
        OrderEntity order = OrderEntity.builder()
                .id(2L)
                .uuid("order-2")
                .orderNumber("BYS-002")
                .customerId(11L)
                .status(OrderEntity.OrderStatus.PENDING)
                .source(OrderEntity.OrderSource.POS)
                .total(new BigDecimal("250.00"))
                .createdAt(LocalDateTime.of(2026, 5, 19, 11, 0))
                .paymentStatus(OrderEntity.PaymentStatus.PAID)
                .build();

        Page<OrderEntity> page = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);
        when(orderRepository.findAllWithFilters(
                eq(OrderEntity.OrderStatus.PENDING),
                eq(OrderEntity.OrderSource.POS),
                eq("RESELLER"),
                eq(null),
                eq(null),
                eq(null),
                any()
        )).thenReturn(page);
        when(shippingAddressRepository.findByOrderId(2L)).thenReturn(
                Optional.of(OrderShippingAddressEntity.builder().fullName("Jane Doe").build())
        );

        PageResponse<AdminOrderListResponse> response = service.getPendingOrders(
                OrderEntity.OrderSource.POS,
                "reseller",
                0,
                10
        );

        assertEquals(1, response.getContent().size());
        assertEquals("order-2", response.getContent().getFirst().getOrderUuid());
        assertEquals("CUSTOMER", response.getContent().getFirst().getOrderFrom());
        assertEquals("Jane Doe", response.getContent().getFirst().getCustomerName());
    }

    @Test
    void getOrderDetail() {
        OrderEntity order = OrderEntity.builder()
                .id(3L)
                .uuid("order-3")
                .orderNumber("BYS-003")
                .customerId(12L)
                .status(OrderEntity.OrderStatus.PAID)
                .source(OrderEntity.OrderSource.ONLINE)
                .paymentReference("TXN-1")
                .subtotal(new BigDecimal("80.00"))
                .shippingCost(new BigDecimal("10.00"))
                .discountTotal(BigDecimal.ZERO)
                .total(new BigDecimal("90.00"))
                .paymentMethod("COD")
                .paymentStatus(OrderEntity.PaymentStatus.PAID)
                .createdAt(LocalDateTime.of(2026, 5, 19, 12, 0))
                .build();

        OrderItemEntity item = OrderItemEntity.builder()
                .id(1L)
                .orderId(3L)
                .productTitle("T-Shirt")
                .variantTitle("Size: L")
                .unitPrice(new BigDecimal("40.00"))
                .quantity(2)
                .totalPrice(new BigDecimal("80.00"))
                .isRefunded(false)
                .build();

        OrderShippingAddressEntity address = OrderShippingAddressEntity.builder()
                .orderId(3L)
                .fullName("Customer One")
                .phone("0771234567")
                .email("c1@example.com")
                .addressLine1("Line 1")
                .city("Colombo")
                .province("Western")
                .postalCode("10000")
                .country("Sri Lanka")
                .build();

        when(orderRepository.findByUuid("order-3")).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(3L)).thenReturn(List.of(item));
        when(shippingAddressRepository.findByOrderId(3L)).thenReturn(Optional.of(address));
        when(statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(3L)).thenReturn(Collections.emptyList());
        when(rabbitTemplate.convertSendAndReceive(anyString(), anyString(), Optional.ofNullable(any()))).thenReturn(null);

        AdminOrderDetailResponse response = service.getOrderDetail("order-3");

        assertEquals("order-3", response.getOrderUuid());
        assertEquals("BYS-003", response.getOrderNumber());
        assertEquals("CUSTOMER", response.getOrderFrom());
        assertEquals("PAID", response.getOrderStatus());
        assertEquals(1, response.getItems().size());
        assertEquals("Customer One", response.getCustomer().getFullName());
        assertEquals("TXN-1", response.getTransactionId());
        assertEquals("COD", response.getPayment().getPaymentMethod());
        assertNull(response.getTracking());
    }

    @Test
    void getPendingOrderDetail() {
        OrderEntity order = OrderEntity.builder()
                .id(4L)
                .uuid("order-4")
                .orderNumber("BYS-004")
                .customerId(13L)
                .status(OrderEntity.OrderStatus.PENDING)
                .source(OrderEntity.OrderSource.ONLINE)
                .createdAt(LocalDateTime.of(2026, 5, 19, 13, 0))
                .paymentStatus(OrderEntity.PaymentStatus.UNPAID)
                .build();

        when(orderRepository.findByUuid("order-4")).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(4L)).thenReturn(Collections.emptyList());
        when(shippingAddressRepository.findByOrderId(4L)).thenReturn(Optional.empty());
        when(statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(4L)).thenReturn(Collections.emptyList());
        when(rabbitTemplate.convertSendAndReceive(anyString(), anyString(), Optional.ofNullable(any()))).thenReturn(null);

        AdminOrderDetailResponse response = service.getPendingOrderDetail("order-4");

        assertEquals("order-4", response.getOrderUuid());
        assertEquals("PENDING", response.getOrderStatus());
        assertEquals("CUSTOMER", response.getOrderFrom());
        assertNull(response.getCustomer());
        assertEquals(0, response.getItems().size());
    }

    @Test
    void rejectOrder() {
        OrderEntity order = OrderEntity.builder()
                .id(5L)
                .uuid("order-5")
                .orderNumber("BYS-005")
                .customerId(14L)
                .status(OrderEntity.OrderStatus.PENDING)
                .notes("Original note")
                .source(OrderEntity.OrderSource.ONLINE)
                .createdAt(LocalDateTime.of(2026, 5, 19, 14, 0))
                .paymentStatus(OrderEntity.PaymentStatus.UNPAID)
                .build();

        when(orderRepository.findByUuid("order-5")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(Collections.emptyList());
        when(shippingAddressRepository.findByOrderId(5L)).thenReturn(Optional.empty());
        when(statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(5L)).thenReturn(Collections.emptyList());
        when(rabbitTemplate.convertSendAndReceive(anyString(), anyString(), Optional.ofNullable(any()))).thenReturn(null);

        AdminOrderDetailResponse response = service.rejectOrder("order-5", "Out of stock", "Customer informed");

        assertEquals("order-5", response.getOrderUuid());
        assertEquals("REJECTED", response.getOrderStatus());

        verify(orderRepository).save(any(OrderEntity.class));
        verify(statusHistoryRepository).save(historyCaptor.capture());

        OrderStatusHistoryEntity history = historyCaptor.getValue();
        assertEquals("PENDING", history.getOldStatus());
        assertEquals("REJECTED", history.getNewStatus());
        assertEquals("Out of stock", history.getNotes());
        assertEquals("Original note\nCustomer informed", order.getNotes());
    }

    @Test
    void rejectOrder_alreadyRejected_throws() {
        OrderEntity order = OrderEntity.builder()
                .id(6L)
                .uuid("order-6")
                .orderNumber("BYS-006")
                .status(OrderEntity.OrderStatus.REJECTED)
                .build();

        when(orderRepository.findByUuid("order-6")).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class,
                () -> service.rejectOrder("order-6", "reason", null));
    }

    @Test
    void rejectOrder_notFound_throws() {
        when(orderRepository.findByUuid("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.rejectOrder("missing", "r", null));
    }
}
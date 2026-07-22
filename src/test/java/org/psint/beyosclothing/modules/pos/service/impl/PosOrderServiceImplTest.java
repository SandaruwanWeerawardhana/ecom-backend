package org.psint.beyosclothing.modules.pos.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.modules.orders.dto.external.InventoryUpdateRequest;
import org.psint.beyosclothing.modules.orders.dto.external.InventoryUpdateResponse;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.orders.repository.projection.PosOrderListView;
import org.psint.beyosclothing.modules.orders.service.OrderCrossModuleLookupService;
import org.psint.beyosclothing.modules.pos.dto.request.PosDeliveryOrderRequest;
import org.psint.beyosclothing.modules.pos.dto.request.PosPlaceOrderRequest;
import org.psint.beyosclothing.modules.pos.dto.request.PosReceiptRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosOrderResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosReceiptDataResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosReceiptResponse;
import org.psint.beyosclothing.modules.pos.entity.PosCartEntity;
import org.psint.beyosclothing.modules.pos.entity.PosCartItemEntity;
import org.psint.beyosclothing.modules.pos.entity.PosCustomerEntity;
import org.psint.beyosclothing.modules.pos.entity.PosProductCacheEntity;
import org.psint.beyosclothing.modules.pos.entity.PosReceiptEntity;
import org.psint.beyosclothing.modules.pos.entity.PosTerminalEntity;
import org.psint.beyosclothing.modules.pos.repository.PosCartItemRepository;
import org.psint.beyosclothing.modules.pos.repository.PosCartRepository;
import org.psint.beyosclothing.modules.pos.repository.PosCustomerRepository;
import org.psint.beyosclothing.modules.pos.repository.PosProductCacheRepository;
import org.psint.beyosclothing.modules.pos.repository.PosReceiptRepository;
import org.psint.beyosclothing.modules.pos.repository.PosTerminalRepository;
import org.psint.beyosclothing.modules.pos.service.PosCustomerLookupService;
import org.psint.beyosclothing.modules.sms.service.OrderSmsNotificationService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosOrderServiceImplTest {

    @Mock
    private PosCartCacheService cartCacheService;

    @Mock
    private PosCartRepository cartRepository;

    @Mock
    private PosTerminalRepository terminalRepository;

    @Mock
    private PosCartItemRepository cartItemRepository;

    @Mock
    private PosProductCacheRepository productCacheRepository;

    @Mock
    private PosCustomerRepository posCustomerRepository;

    @Mock
    private PosReceiptRepository receiptRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderCrossModuleLookupService crossModuleLookupService;

    @Mock
    private PosCustomerLookupService posCustomerLookupService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private OrderSmsNotificationService smsNotificationService;

    @InjectMocks
    private PosOrderServiceImpl service;

    @Captor
    private ArgumentCaptor<List<InventoryUpdateRequest.InventoryItem>> inventoryItemsCaptor;

    @Captor
    private ArgumentCaptor<PosReceiptEntity> receiptCaptor;

    @Test
    void placeOrderCompletesCartAndSyncsProductCache() {
        PosCartEntity cart = completedCartBuilder()
                .isActive(true)
                .isDraft(false)
                .customerType("WALK_IN")
                .build();
        PosCartItemEntity item = cartItem(101L, 7L, null, 2, "20.00", "40.00", 5);
        PosProductCacheEntity cache = productCache(7L, "SKU-7", "Crew Neck T-Shirt", 10);
        PosTerminalEntity terminal = PosTerminalEntity.builder()
                .id(2L)
                .uuid("terminal-uuid")
                .code("T-01")
                .build();

        when(cartRepository.findByUuidAndIsActiveTrue("cart-uuid")).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndIsActiveTrue(1L)).thenReturn(List.of(item));
        when(crossModuleLookupService.decreaseInventoryStock(anyList()))
                .thenReturn(InventoryUpdateResponse.builder().success(true).build());
        when(productCacheRepository.findByProductId(7L)).thenReturn(Optional.of(cache));
        when(terminalRepository.findById(2L)).thenReturn(Optional.of(terminal));
        when(cartRepository.save(cart)).thenReturn(cart);
        when(productCacheRepository.save(cache)).thenReturn(cache);

        PosOrderResponse response = service.placeOrder(
                PosPlaceOrderRequest.builder().cartUuid("cart-uuid").build(),
                "terminal-header",
                "cashier-header");

        assertEquals("COMPLETE", response.getStatus());
        assertEquals("cart-uuid", response.getCartUuid());
        assertEquals(new BigDecimal("40.00"), response.getCartTotal());
        assertFalse(response.getCartIsActive());
        assertEquals(8, cache.getStockAvailable());

        verify(cartRepository).save(cart);
        verify(cartCacheService).removeCacheForTerminal("terminal-uuid");
        verify(productCacheRepository).save(cache);
        verify(crossModuleLookupService).decreaseInventoryStock(inventoryItemsCaptor.capture());
        assertEquals(1, inventoryItemsCaptor.getValue().size());
        assertEquals(7L, inventoryItemsCaptor.getValue().getFirst().getProductId());
        assertEquals(2, inventoryItemsCaptor.getValue().getFirst().getQuantity());
    }

    @Test
    void placeOrderRejectsEmptyCart() {
        PosCartEntity cart = completedCartBuilder()
                .isActive(true)
                .build();

        when(cartRepository.findByUuidAndIsActiveTrue("cart-uuid")).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndIsActiveTrue(1L)).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> service.placeOrder(
                PosPlaceOrderRequest.builder().cartUuid("cart-uuid").build(),
                "terminal-header",
                "cashier-header"));
    }

    @Test
    void placeDeliveryOrderReturnsExistingOrderWithoutDuplicatingSideEffects() {
        PosCartEntity cart = completedCartBuilder()
                .isActive(true)
                .customerType("POS")
                .build();
        PosCartItemEntity item = cartItem(101L, 7L, null, 2, "20.00", "40.00", 5);
        OrderEntity existingOrder = posOrder(90L, "existing-order-uuid", "BYS-POS-090", 1L);
        PosTerminalEntity terminal = PosTerminalEntity.builder()
                .id(2L)
                .uuid("terminal-uuid")
                .code("T-01")
                .build();

        when(cartItemRepository.findByUuid("item-101")).thenReturn(Optional.of(item));
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));
        when(orderRepository.findFirstBySourceAndCartIdOrderByCreatedAtDesc(OrderEntity.OrderSource.POS, 1L))
                .thenReturn(Optional.of(existingOrder));
        when(cartRepository.save(cart)).thenReturn(cart);
        when(terminalRepository.findById(2L)).thenReturn(Optional.of(terminal));

        PosOrderResponse response = service.placeDeliveryOrder(PosDeliveryOrderRequest.builder()
                .cartItemUuids(List.of("item-101"))
                .source("POS")
                .deliveryAddressId(0L)
                .courierId(0L)
                .paymentMethodId(0L)
                .customerUuid("customer-uuid")
                .build(), null, null);

        assertEquals("existing-order-uuid", response.getOrderUuid());
        assertEquals("BYS-POS-090", response.getOrderNumber());
        assertEquals("PENDING", response.getStatus());
        assertFalse(cart.getIsActive());

        verify(cartRepository).save(cart);
        verify(cartCacheService).removeCacheForTerminal("terminal-uuid");
        verify(crossModuleLookupService, never()).decreaseInventoryStock(anyList());
        verify(rabbitTemplate, never()).convertSendAndReceive(nullable(String.class), nullable(String.class), any(Object.class));
    }

    @Test
    void placeDeliveryOrderRetryReturnsExistingOnlineOrderMatchedByStableUuid() {
        PosCartEntity cart = completedCartBuilder()
                .isActive(true)
                .customerType("ONLINE")
                .build();
        PosCartItemEntity item = cartItem(101L, 7L, null, 2, "20.00", "40.00", 5);
        String stableOrderUuid = UUID.nameUUIDFromBytes(
                "POS_DELIVERY_ORDER:cart-uuid".getBytes(StandardCharsets.UTF_8)).toString();
        OrderEntity existingOrder = order(92L, stableOrderUuid, "BYS-ONL-092", 1L, OrderEntity.OrderSource.ONLINE);
        PosTerminalEntity terminal = PosTerminalEntity.builder()
                .id(2L)
                .uuid("terminal-uuid")
                .code("T-01")
                .build();

        when(cartItemRepository.findByUuid("item-101")).thenReturn(Optional.of(item));
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));
        when(orderRepository.findByUuid(stableOrderUuid)).thenReturn(Optional.of(existingOrder));
        when(cartRepository.save(cart)).thenReturn(cart);
        when(terminalRepository.findById(2L)).thenReturn(Optional.of(terminal));

        PosOrderResponse response = service.placeDeliveryOrder(PosDeliveryOrderRequest.builder()
                .cartItemUuids(List.of("item-101"))
                .source("ONLINE")
                .paymentMethodId(1L)
                .build(), "online-customer-uuid", null);

        assertEquals(stableOrderUuid, response.getOrderUuid());
        assertEquals("BYS-ONL-092", response.getOrderNumber());
        assertFalse(cart.getIsActive());

        verify(crossModuleLookupService, never()).decreaseInventoryStock(anyList());
        verify(rabbitTemplate, never()).convertSendAndReceive(nullable(String.class), nullable(String.class), any(Object.class));
    }

    @Test
    void placeDeliveryOrderRecoversWhenOrderModuleReplyIsMissingButOrderExists() {
        PosCartEntity cart = completedCartBuilder()
                .isActive(true)
                .customerType("POS")
                .build();
        PosCartItemEntity item = cartItem(101L, 7L, null, 2, "20.00", "40.00", 5);
        PosProductCacheEntity cache = productCache(7L, "SKU-7", "Crew Neck T-Shirt", 10);
        OrderEntity completedOrder = posOrder(91L, "completed-order-uuid", "BYS-POS-091", 1L);
        PosTerminalEntity terminal = PosTerminalEntity.builder()
                .id(2L)
                .uuid("terminal-uuid")
                .code("T-01")
                .build();

        when(cartItemRepository.findByUuid("item-101")).thenReturn(Optional.of(item));
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));
        when(orderRepository.findFirstBySourceAndCartIdOrderByCreatedAtDesc(OrderEntity.OrderSource.POS, 1L))
                .thenReturn(Optional.empty(), Optional.of(completedOrder));
        when(posCustomerRepository.findByUuid("customer-uuid")).thenReturn(Optional.of(posCustomer()));
        when(productCacheRepository.findByProductId(7L)).thenReturn(Optional.of(cache));
        when(crossModuleLookupService.decreaseInventoryStock(anyList()))
                .thenReturn(InventoryUpdateResponse.builder().success(true).build());
        when(productCacheRepository.save(cache)).thenReturn(cache);
        when(rabbitTemplate.convertSendAndReceive(nullable(String.class), eq("order.create.request"), any(Object.class))).thenReturn(null);
        when(cartRepository.save(cart)).thenReturn(cart);
        when(terminalRepository.findById(2L)).thenReturn(Optional.of(terminal));

        PosOrderResponse response = service.placeDeliveryOrder(PosDeliveryOrderRequest.builder()
                .cartItemUuids(List.of("item-101"))
                .source("POS")
                .deliveryAddressId(0L)
                .courierId(0L)
                .paymentMethodId(0L)
                .customerUuid("customer-uuid")
                .customerNotes("")
                .usePromoCode(false)
                .build(), null, null);

        assertEquals("completed-order-uuid", response.getOrderUuid());
        assertEquals("BYS-POS-091", response.getOrderNumber());
        assertEquals("PENDING", response.getStatus());
        assertFalse(cart.getIsActive());
        assertEquals(8, cache.getStockAvailable());

        verify(rabbitTemplate).convertSendAndReceive(nullable(String.class), eq("order.create.request"), any(Object.class));
        verify(crossModuleLookupService).decreaseInventoryStock(anyList());
        verify(cartRepository).save(cart);
        verify(cartCacheService).removeCacheForTerminal("terminal-uuid");
    }
    @Test
    void listOrdersMapsWalkInCartsAndDeliveryOrderProjections() {
        Pageable pageable = PageRequest.of(0, 10);
        LocalDate dateFrom = LocalDate.of(2026, 7, 1);
        LocalDate dateTo = LocalDate.of(2026, 7, 15);
        PosCartEntity walkInCart = completedCartBuilder()
                .id(1L)
                .uuid("walk-in-cart")
                .customerType("WALK_IN")
                .build();
        PosCartEntity deliveryCart = completedCartBuilder()
                .id(2L)
                .uuid("delivery-cart")
                .customerType("POS")
                .build();
        PosOrderListView deliveryOrder = deliveryOrderProjection(2L);

        when(cartRepository.findCompletedCarts(
                eq(dateFrom.atStartOfDay()),
                eq(dateTo.plusDays(1).atStartOfDay()),
                eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(walkInCart, deliveryCart), pageable, 2));
        when(orderRepository.findPosOrderViewsByCartIds(List.of(1L, 2L), OrderEntity.OrderSource.POS))
                .thenReturn(List.of(deliveryOrder));

        PageResponse<PosOrderResponse> response = service.listOrders(
                Optional.of(dateFrom),
                Optional.of(dateTo),
                pageable);

        assertEquals(2, response.getContent().size());
        assertEquals(2, response.getTotalElements());
        assertEquals("walk-in-cart", response.getContent().get(0).getOrderUuid());
        assertEquals("COMPLETE", response.getContent().get(0).getStatus());
        assertEquals("WALK_IN", response.getContent().get(0).getCartCustomerType());
        assertEquals("order-uuid", response.getContent().get(1).getOrderUuid());
        assertEquals("BYS-POS-001", response.getContent().get(1).getOrderNumber());
        assertEquals("PENDING", response.getContent().get(1).getStatus());
        assertEquals("UNPAID", response.getContent().get(1).getPaymentStatus());
        assertEquals("BYS-POS-001", response.getContent().get(1).getReceiptNumber());
    }

    @Test
    void listOrdersWithoutDateFiltersQueriesWithWideDefaultBounds() {
        Pageable pageable = PageRequest.of(0, 10);
        PosCartEntity cart = completedCartBuilder()
                .id(1L)
                .uuid("walk-in-cart")
                .customerType("WALK_IN")
                .build();

        when(cartRepository.findCompletedCarts(any(LocalDateTime.class), any(LocalDateTime.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(cart), pageable, 1));
        when(orderRepository.findPosOrderViewsByCartIds(List.of(1L), OrderEntity.OrderSource.POS))
                .thenReturn(List.of());

        PageResponse<PosOrderResponse> response = service.listOrders(Optional.empty(), Optional.empty(), pageable);

        assertEquals(1, response.getContent().size());
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(cartRepository).findCompletedCarts(startCaptor.capture(), endCaptor.capture(), eq(pageable));
        assertEquals(LocalDate.EPOCH.atStartOfDay(), startCaptor.getValue());
        assertTrue(endCaptor.getValue().isAfter(LocalDateTime.now()));
    }

    @Test
    void getReceiptForOrderMapsOrderAndCartItems() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 16, 9, 30);
        OrderEntity order = OrderEntity.builder()
                .id(20L)
                .uuid("order-uuid")
                .cartId(1L)
                .orderNumber("BYS-1001")
                .subtotal(new BigDecimal("70.00"))
                .total(new BigDecimal("70.00"))
                .createdAt(createdAt)
                .build();
        PosCartItemEntity firstItem = cartItem(101L, 7L, null, 2, "20.00", "40.00", 5);
        PosCartItemEntity secondItem = cartItem(102L, 8L, null, 1, "30.00", "30.00", 5);

        when(orderRepository.findByUuid("order-uuid")).thenReturn(Optional.of(order));
        when(cartItemRepository.findByCartIdAndIsActiveTrue(1L)).thenReturn(List.of(firstItem, secondItem));
        when(productCacheRepository.findByProductId(7L))
                .thenReturn(Optional.of(productCache(7L, "SKU-7", "Crew Neck T-Shirt", 10)));
        when(productCacheRepository.findByProductId(8L)).thenReturn(Optional.empty());

        PosReceiptDataResponse response = service.getReceiptForOrder("order-uuid");

        assertEquals("BYS-1001", response.getBillNo());
        assertEquals(createdAt, response.getDate());
        assertEquals(new BigDecimal("70.00"), response.getSubtotal());
        assertEquals(new BigDecimal("70.00"), response.getGrandTotal());
        assertEquals(2, response.getItems().size());
        assertEquals(1, response.getItems().get(0).getLineNo());
        assertEquals("Crew Neck T-Shirt", response.getItems().get(0).getProduct());
        assertEquals("SKU-7", response.getItems().get(0).getSku());
        assertEquals("Product #8", response.getItems().get(1).getProduct());
    }

    @Test
    void createReceiptTrimsOrderUuidAndSavesReceipt() {
        LocalDateTime printedAt = LocalDateTime.of(2026, 7, 16, 11, 15);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 16, 11, 16);
        PosCartEntity cart = completedCartBuilder().build();

        when(cartRepository.findByUuid("cart-uuid")).thenReturn(Optional.of(cart));
        when(receiptRepository.save(any(PosReceiptEntity.class))).thenAnswer(invocation -> {
            PosReceiptEntity receipt = invocation.getArgument(0);
            receipt.setId(55L);
            receipt.setUuid("receipt-uuid");
            receipt.setCreatedAt(createdAt);
            return receipt;
        });

        PosReceiptResponse response = service.createReceipt(PosReceiptRequest.builder()
                .orderUuid("  cart-uuid  ")
                .receiptNumber("RCP-0001")
                .printedAt(printedAt)
                .build());

        verify(receiptRepository).save(receiptCaptor.capture());
        PosReceiptEntity savedReceipt = receiptCaptor.getValue();
        assertEquals(1L, savedReceipt.getOrderId());
        assertEquals("RCP-0001", savedReceipt.getReceiptNumber());
        assertEquals(1, savedReceipt.getPrintCount());
        assertEquals(printedAt, savedReceipt.getPrintedAt());
        assertEquals("receipt-uuid", response.getUuid());
        assertEquals("cart-uuid", response.getOrderUuid());
        assertEquals(createdAt, response.getCreatedAt());
    }

    private PosCartEntity.PosCartEntityBuilder completedCartBuilder() {
        return PosCartEntity.builder()
                .id(1L)
                .uuid("cart-uuid")
                .terminalId(2L)
                .cashierId(3L)
                .customerId(4L)
                .customerType("POS")
                .subtotal(new BigDecimal("40.00"))
                .taxAmount(BigDecimal.ZERO)
                .taxPercentage(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .total(new BigDecimal("40.00"))
                .isActive(false)
                .isDraft(false)
                .createdAt(LocalDateTime.of(2026, 7, 16, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 7, 16, 10, 5));
    }

    private PosCartItemEntity cartItem(Long id, Long productId, Long variantId, int quantity,
                                       String unitPrice, String totalPrice, int stockAvailable) {
        return PosCartItemEntity.builder()
                .id(id)
                .uuid("item-" + id)
                .cartId(1L)
                .productId(productId)
                .variantId(variantId)
                .quantity(quantity)
                .unitPrice(new BigDecimal(unitPrice))
                .totalPrice(new BigDecimal(totalPrice))
                .stockAvailable(stockAvailable)
                .isActive(true)
                .build();
    }

    private PosProductCacheEntity productCache(Long productId, String sku, String title, int stockAvailable) {
        return PosProductCacheEntity.builder()
                .productId(productId)
                .uuid("product-" + productId)
                .sku(sku)
                .title(title)
                .stockAvailable(stockAvailable)
                .build();
    }

    private PosCustomerEntity posCustomer() {
        return PosCustomerEntity.builder()
                .id(4L)
                .uuid("customer-uuid")
                .fullName("Walk In Customer")
                .phone("0771234567")
                .address("No 1 Main Street")
                .city("Colombo")
                .province("Western")
                .zipCode("10000")
                .isActive(true)
                .build();
    }

    private OrderEntity posOrder(Long id, String uuid, String orderNumber, Long cartId) {
        return order(id, uuid, orderNumber, cartId, OrderEntity.OrderSource.POS);
    }

    private OrderEntity order(Long id, String uuid, String orderNumber, Long cartId, OrderEntity.OrderSource source) {
        return OrderEntity.builder()
                .id(id)
                .uuid(uuid)
                .orderNumber(orderNumber)
                .source(source)
                .cartId(cartId)
                .status(OrderEntity.OrderStatus.PENDING)
                .paymentStatus(OrderEntity.PaymentStatus.UNPAID)
                .subtotal(new BigDecimal("40.00"))
                .discountTotal(BigDecimal.ZERO)
                .shippingCost(BigDecimal.ZERO)
                .total(new BigDecimal("40.00"))
                .createdAt(LocalDateTime.of(2026, 7, 16, 10, 30))
                .build();
    }
    private PosOrderListView deliveryOrderProjection(Long cartId) {
        PosOrderListView projection = org.mockito.Mockito.mock(PosOrderListView.class);
        when(projection.getUuid()).thenReturn("order-uuid");
        when(projection.getOrderNumber()).thenReturn("BYS-POS-001");
        when(projection.getStatus()).thenReturn(OrderEntity.OrderStatus.PENDING);
        when(projection.getPaymentStatus()).thenReturn(OrderEntity.PaymentStatus.UNPAID);
        when(projection.getSubtotal()).thenReturn(new BigDecimal("40.00"));
        when(projection.getDiscountTotal()).thenReturn(BigDecimal.ZERO);
        when(projection.getShippingCost()).thenReturn(new BigDecimal("10.00"));
        when(projection.getTotal()).thenReturn(new BigDecimal("50.00"));
        when(projection.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 7, 16, 10, 30));
        when(projection.getCartId()).thenReturn(cartId);
        return projection;
    }
}


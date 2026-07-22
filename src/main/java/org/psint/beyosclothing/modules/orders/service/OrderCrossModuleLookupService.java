package org.psint.beyosclothing.modules.orders.service;

import org.psint.beyosclothing.modules.orders.dto.external.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cross-Module Lookup Service for Order Module
 * Handles RabbitMQ-based communication with other modules
 */
public interface OrderCrossModuleLookupService {

    /**
     * Get cart items for order placement
     * @param customerUuid Customer UUID
     * @param guestSessionToken Guest session token (if guest)
     * @param selectedItemUuids Selected cart item UUIDs (null for all)
     * @return Cart items response
     */
    CartItemsForOrderResponse getCartItemsForOrder(String customerUuid, String guestSessionToken, List<String> selectedItemUuids);

    /**
     * Lookup customer details by UUID
     * @param customerUuid Customer UUID
     * @return Customer details
     */
    CustomerLookupResponse lookupCustomer(String customerUuid);

    /**
     * Lookup customer address by ID
     * @param customerId Customer ID
     * @param addressId Address ID
     * @return Address details
     */
    CustomerAddressResponse lookupCustomerAddress(Long customerId, Long addressId);

    /**
     * Lookup payment method details
     * @param paymentMethodId Payment method ID
     * @return Payment method details
     */
    PaymentMethodLookupResponse lookupPaymentMethod(Long paymentMethodId);

    /**
     * Calculate shipping cost
     * @param courierUuid Courier UUID
     * @param totalWeight Total weight in kg
     * @param paymentMethodId Payment method ID
     * @param customerType Customer type (CUSTOMER/RESELLER)
     * @return Shipping calculation response
     */
    ShippingCostResponse calculateShippingCost(String courierUuid, BigDecimal totalWeight, Long paymentMethodId, String customerType);

    /**
     * Create shipment record in delivery module
     * @param request Shipment creation request
     * @return Shipment creation response
     */
    ShipmentCreationResponse createShipment(ShipmentCreationRequest request);

    /**
     * Create payment request in payment module
     * @param request Payment request creation
     * @return Payment request response
     */
    PaymentRequestCreationResponse createPaymentRequest(PaymentRequestCreationRequest request);

    /**
     * Decrease inventory stock
     * @param items List of items to decrease stock
     * @return Inventory update response
     */
    InventoryUpdateResponse decreaseInventoryStock(List<InventoryUpdateRequest.InventoryItem> items);

    /**
     * Clear cart items after order placement
     * @param cartId Cart ID
     * @param itemUuids Item UUIDs to remove
     * @return Cart clear response
     */
    CartClearResponse clearCartItems(Long cartId, List<String> itemUuids);
}


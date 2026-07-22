package org.psint.beyosclothing.modules.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.entity.Shipment;
import org.psint.beyosclothing.modules.delivery.repository.ShipmentRepository;
import org.psint.beyosclothing.modules.delivery.repository.ShipmentTrackingEventRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service to process courier API responses and update shipment/order status
 * Maps Koombiyo courier statuses to internal ShipmentStatus and OrderStatus enums
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CourierResponseProcessingService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingEventRepository trackingEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange.order:beyos.exchange.order}")
    private String orderExchange;

    @Value("${app.rabbitmq.exchange.reseller:beyos.exchange.reseller}")
    private String resellerExchange;

    /**
     * Process courier API response and update shipment/order status
     * Koombiyo API response format:
     * {
     *   "cust_orders": [{
     *     "waybill_id": "459654131",
     *     "orderstatus": "Delivered",
     *     "deliverystatus": "6",
     *     "deliveryaddress": "...",
     *     "recever": "...",
     *     ...
     *   }],
     *   "rowcount": 1
     * }
     */
    @Transactional("deliveryTransactionManager")
    public void processKoombiyoTrackingResponse(Map<String, Object> rawResponse) {
        try {
            log.info("🔄 Processing Koombiyo tracking response");

            // Extract the cust_orders array
            List<Map<String, Object>> custOrders = (List<Map<String, Object>>) rawResponse.get("cust_orders");

            if (custOrders == null || custOrders.isEmpty()) {
                log.warn("No order data in Koombiyo response");
                return;
            }

            // Process each order in the response
            for (Map<String, Object> orderData : custOrders) {
                processKoombiyoOrder(orderData);
            }

        } catch (Exception e) {
            log.error("❌ Error processing Koombiyo tracking response: {}", e.getMessage(), e);
        }
    }

    /**
     * Process a single order from Koombiyo response
     */
    private void processKoombiyoOrder(Map<String, Object> orderData) {
        try {
            String waybillId = (String) orderData.get("waybill_id");
            String courierOrderStatus = (String) orderData.get("orderstatus");
            String courierDeliveryStatus = (String) orderData.get("deliverystatus");

            log.info("📦 Processing waybill: {}, status: {}, deliveryStatus: {}",
                    waybillId, courierOrderStatus, courierDeliveryStatus);

            // Find shipment by waybill ID
            Optional<Shipment> shipmentOpt = shipmentRepository.findByWayBillId(waybillId);

            if (shipmentOpt.isEmpty()) {
                log.warn("⚠️ Shipment not found for waybill: {}", waybillId);
                return;
            }

            Shipment shipment = shipmentOpt.get();

            // Map courier status to internal status
            Shipment.ShipmentStatus mappedShipmentStatus = mapCourierStatusToShipmentStatus(courierOrderStatus);
            String mappedOrderStatus = mapCourierStatusToOrderStatus(courierOrderStatus);

            log.info("📊 Courier Status: {} -> Shipment Status: {}, Order Status: {}",
                    courierOrderStatus, mappedShipmentStatus, mappedOrderStatus);

            // Check if this status already exists for this shipment
            if (shipment.getStatus() == mappedShipmentStatus) {
                log.debug("Shipment already has status: {}, skipping", mappedShipmentStatus);
                return;
            }

            // Update shipment status
            Shipment.ShipmentStatus oldStatus = shipment.getStatus();
            shipment.setStatus(mappedShipmentStatus);

            if (mappedShipmentStatus == Shipment.ShipmentStatus.DELIVERED) {
                shipment.setDeliveredAt(LocalDateTime.now());
                // Mark shipment as inactive when delivered
                shipment.setIsActive(false);
                log.info("✅ Shipment marked as inactive (delivered)");
            }

            shipmentRepository.save(shipment);
            log.info("✅ Shipment {} status updated: {} -> {}",
                    shipment.getUuid(), oldStatus, mappedShipmentStatus);

            // Record tracking event
            recordTrackingEvent(shipment, mappedShipmentStatus, orderData);

            // Publish event to update order status (cross-module via RabbitMQ)
            publishOrderStatusUpdateEvent(shipment, mappedOrderStatus, oldStatus.toString(), orderData);

            // If delivered, process reseller wallet and payment transaction
            if (mappedShipmentStatus == Shipment.ShipmentStatus.DELIVERED) {
                publishDeliveryCompletedEvent(shipment, orderData);
            }

        } catch (Exception e) {
            log.error("❌ Error processing Koombiyo order: {}", e.getMessage(), e);
        }
    }

    /**
     * Record shipment tracking event
     */
    private void recordTrackingEvent(Shipment shipment, Shipment.ShipmentStatus status, Map<String, Object> courierData) {
        try {
            String description = (String) courierData.get("orderstatus");
            String location = (String) courierData.get("district");
            String receiverName = (String) courierData.get("recever");

            if (receiverName != null && !receiverName.isEmpty()) {
                description = description + " - Received by: " + receiverName;
            }

            String rawPayload = objectMapper.writeValueAsString(courierData);

            trackingEventRepository.save(
                    org.psint.beyosclothing.modules.delivery.entity.ShipmentTrackingEvent.builder()
                            .shipment(shipment)
                            .status(status)
                            .description(description)
                            .location(location)
                            .eventSource("COURIER")
                            .rawPayload(rawPayload)
                            .eventTime(LocalDateTime.now())
                            .build()
            );

            log.info("✅ Tracking event recorded for shipment {}", shipment.getUuid());

        } catch (Exception e) {
            log.error("❌ Error recording tracking event: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish order status update event to order module (RabbitMQ - using Map)
     */
    private void publishOrderStatusUpdateEvent(Shipment shipment, String newOrderStatus, String oldOrderStatus, Map<String, Object> courierData) {
        try {
            if (shipment.getOrderId() == null) {
                log.warn("Shipment has no order ID, skipping order status update");
                return;
            }

            Map<String, Object> event = new HashMap<>();
            event.put("orderId", shipment.getOrderId());
            event.put("oldStatus", oldOrderStatus);
            event.put("newStatus", newOrderStatus);
            event.put("shipmentUuid", shipment.getUuid());
            event.put("waybillId", shipment.getWayBillId());
            event.put("courierStatus", courierData.get("orderstatus"));
            event.put("courierData", courierData);
            event.put("eventSource", "COURIER_TRACKING_UPDATE");
            event.put("timestamp", System.currentTimeMillis());

            rabbitTemplate.convertAndSend(orderExchange, "order.status.updated.courier", event);
            log.info("✅ Published order status update event for order: {}", shipment.getOrderId());

        } catch (Exception e) {
            log.error("❌ Error publishing order status update event: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish delivery completed event (for wallet and payment updates)
     */
    private void publishDeliveryCompletedEvent(Shipment shipment, Map<String, Object> courierData) {
        try {
            if (shipment.getOrderId() == null) {
                log.warn("Shipment has no order ID, skipping delivery completed event");
                return;
            }

            Map<String, Object> event = new HashMap<>();
            event.put("orderId", shipment.getOrderId());
            event.put("shipmentId", shipment.getId());
            event.put("shipmentUuid", shipment.getUuid());
            event.put("waybillId", shipment.getWayBillId());
            event.put("deliveryAddress", courierData.get("deliveryaddress"));
            event.put("receiverName", courierData.get("recever"));
            event.put("codAmount", courierData.get("codamount"));
            event.put("deliveryCharge", courierData.get("deliverycharge"));
            event.put("deliveredAt", LocalDateTime.now());
            event.put("eventSource", "COURIER_DELIVERY_CONFIRMATION");
            event.put("timestamp", System.currentTimeMillis());

            rabbitTemplate.convertAndSend(orderExchange, "order.delivery.completed", event);
            log.info("✅ Published delivery completed event for order: {}", shipment.getOrderId());

        } catch (Exception e) {
            log.error("❌ Error publishing delivery completed event: {}", e.getMessage(), e);
        }
    }

    /**
     * Map Koombiyo courier status to internal ShipmentStatus enum
     * Koombiyo orderstatus values: "Pending", "Confirmed", "Picked", "Delivered", "Failed", etc.
     */
    private Shipment.ShipmentStatus mapCourierStatusToShipmentStatus(String courierStatus) {
        if (courierStatus == null) {
            return Shipment.ShipmentStatus.PROCESSING;
        }

        String status = courierStatus.toUpperCase().trim();

        return switch (status) {
            case "PENDING", "PENDING_DIFFERENT_DESTINATION" -> Shipment.ShipmentStatus.PENDING;
            case "CONFIRMED", "CONFIRMED_BY_BRANCH" -> Shipment.ShipmentStatus.BOOKED;
            case "PICKED", "PICK" -> Shipment.ShipmentStatus.PICKED;
            case "PROCESSING" -> Shipment.ShipmentStatus.PROCESSING;
            case "INTRANSIT", "IN_TRANSIT" -> Shipment.ShipmentStatus.IN_TRANSIT;
            case "COLLECTED_BY_KOOMBIYO" -> Shipment.ShipmentStatus.COLLECTED_BY_KOOMBIYO;
            case "DISPATCH_TO_DESTINATION" -> Shipment.ShipmentStatus.DISPATCH_TO_DESTINATION;
            case "RECEIVED_AT_DESTINATION" -> Shipment.ShipmentStatus.RECEIVED_AT_DESTINATION;
            case "OUT_FOR_DELIVERY" -> Shipment.ShipmentStatus.OUT_FOR_DELIVERY;
            case "DELIVERED" -> Shipment.ShipmentStatus.DELIVERED;
            case "DELIVERED_NOT_CONFIRMED" -> Shipment.ShipmentStatus.DELIVERED_NOT_CONFIRMED;
            case "PARTIALLY_DELIVERED" -> Shipment.ShipmentStatus.PARTIALLY_DELIVERED;
            case "FAILED_TO_DELIVER" -> Shipment.ShipmentStatus.FAILED_TO_DELIVER;
            case "RESCHEDULED" -> Shipment.ShipmentStatus.RESCHEDULED;
            case "RETURN_TO_CLIENT" -> Shipment.ShipmentStatus.RETURN_TO_CLIENT;
            case "RETURN_TO_HO" -> Shipment.ShipmentStatus.RETURN_TO_HO;
            case "EXCHANGE_COLLECTED" -> Shipment.ShipmentStatus.EXCHANGE_COLLECTED;
            case "EXCHANGE_RECEIVED" -> Shipment.ShipmentStatus.EXCHANGE_RECEIVED;
            case "FAILED" -> Shipment.ShipmentStatus.FAILED;
            case "RETURNED" -> Shipment.ShipmentStatus.RETURNED;
            default -> Shipment.ShipmentStatus.PROCESSING;
        };
    }

    /**
     * Map Koombiyo courier status to internal OrderStatus enum
     * Order module OrderStatus values: PENDING, PAID, PROCESSING, OUT_FOR_DELIVERY, DELIVERED, COMPLETED, etc.
     */
    private String mapCourierStatusToOrderStatus(String courierStatus) {
        if (courierStatus == null) {
            return "PROCESSING";
        }

        String status = courierStatus.toUpperCase().trim();

        return switch (status) {
            case "PENDING", "PENDING_DIFFERENT_DESTINATION", "CONFIRMED", "CONFIRMED_BY_BRANCH" -> "COURIER_ORDER_PLACED";
            case "PICKED", "INTRANSIT", "IN_TRANSIT", "COLLECTED_BY_KOOMBIYO", "DISPATCH_TO_DESTINATION",
                 "RECEIVED_AT_DESTINATION", "OUT_FOR_DELIVERY" -> "OUT_FOR_DELIVERY";
            case "DELIVERED" -> "DELIVERED";
            case "DELIVERED_NOT_CONFIRMED" -> "OUT_FOR_DELIVERY"; // Still in transit, not confirmed
            case "PARTIALLY_DELIVERED" -> "OUT_FOR_DELIVERY";
            case "FAILED_TO_DELIVER", "FAILED" -> "CANCELLED"; // Treat as failed
            case "RETURN_TO_CLIENT", "RETURN_TO_HO", "RETURNED" -> "RETURN_REQUESTED";
            case "RESCHEDULED" -> "OUT_FOR_DELIVERY";
            default -> "PROCESSING";
        };
    }

    /**
     * Get shipment by waybill ID (helper method for repository)
     */
}


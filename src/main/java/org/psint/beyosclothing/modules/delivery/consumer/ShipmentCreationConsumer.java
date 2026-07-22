package org.psint.beyosclothing.modules.delivery.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.dto.external.ShipmentCreationRequest;
import org.psint.beyosclothing.modules.delivery.dto.external.ShipmentCreationResponse;
import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.entity.Shipment;
import org.psint.beyosclothing.modules.delivery.repository.CourierRepository;
import org.psint.beyosclothing.modules.delivery.repository.ShipmentRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer for shipment creation requests from Order module
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentCreationConsumer {

    private final ShipmentRepository shipmentRepository;
    private final CourierRepository courierRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${app.rabbitmq.queue.shipment-create-request}")
    @Transactional
    public ShipmentCreationResponse handleShipmentCreationRequest(ShipmentCreationRequest request) {
        log.info("Received shipment creation request - Request ID: {}, Order ID: {}, Courier ID: {}",
                request.getRequestId(), request.getOrderId(), request.getCourierId());

        try {
            // Validate courier exists
            Courier courier = courierRepository.findById(request.getCourierId())
                    .orElse(null);

            if (courier == null) {
                log.warn("Courier not found - ID: {}", request.getCourierId());
                return ShipmentCreationResponse.builder()
                        .requestId(request.getRequestId())
                        .success(false)
                        .errorMessage("Courier not found with ID: " + request.getCourierId())
                        .build();
            }

            // Create shipment
            Shipment shipment = Shipment.builder()
                    .orderId(request.getOrderId())
                    .courier(courier)
                    .shipmentWeight(request.getShipmentWeight())
                    .shippingCost(request.getShippingCost())
                    .shippingBreakdown(convertBreakdownToJson(request.getShippingBreakdown()))
                    .paymentMethodId(request.getPaymentMethodId())
                    .payerType(Shipment.PayerType.valueOf(request.getPayerType()))
                    .status(Shipment.ShipmentStatus.PENDING)
                    .build();

            shipment = shipmentRepository.save(shipment);

            log.info("Shipment created successfully - ID: {}, UUID: {}, Order ID: {}",
                    shipment.getId(), shipment.getUuid(), shipment.getOrderId());

            return ShipmentCreationResponse.builder()
                    .requestId(request.getRequestId())
                    .success(true)
                    .shipmentId(shipment.getId())
                    .shipmentUuid(shipment.getUuid())
                    .build();

        } catch (Exception e) {
            log.error("Error creating shipment - Request ID: {}", request.getRequestId(), e);
            return ShipmentCreationResponse.builder()
                    .requestId(request.getRequestId())
                    .success(false)
                    .errorMessage("Error creating shipment: " + e.getMessage())
                    .build();
        }
    }

    private String convertBreakdownToJson(Object breakdown) {
        try {
            if (breakdown == null) {
                return null;
            }
            return objectMapper.writeValueAsString(breakdown);
        } catch (Exception e) {
            log.warn("Failed to convert breakdown to JSON", e);
            return null;
        }
    }
}


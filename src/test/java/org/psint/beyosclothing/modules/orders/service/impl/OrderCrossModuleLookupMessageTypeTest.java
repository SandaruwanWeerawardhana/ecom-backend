package org.psint.beyosclothing.modules.orders.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.psint.beyosclothing.core.config.RabbitMQConfig;
import org.psint.beyosclothing.modules.customers.consumer.CustomerAddressLookupConsumer;
import org.psint.beyosclothing.modules.customers.consumer.CustomerLookupConsumer;
import org.psint.beyosclothing.modules.delivery.consumer.ShippingCalculationConsumer;
import org.psint.beyosclothing.modules.payment.consumer.PaymentMethodLookupConsumer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Guards the RabbitMQ request-reply contract between {@link OrderCrossModuleLookupServiceImpl}
 * and the consumers that answer its lookups.
 * <p>
 * The shared converter uses a DefaultClassMapper, so a consumer deserializes strictly from the
 * __TypeId__ header written from the sender payload's runtime class; the listener's declared
 * parameter type is ignored. A sender that ships a generic payload therefore produces a message
 * the consumer cannot accept, and the caller only sees a null reply that looks like "not found".
 * <p>
 * Each test invokes the real service method, captures the payload it hands to RabbitTemplate, runs
 * that payload through the real converter, and asserts it arrives as the type the consumer declares.
 */
@ExtendWith(MockitoExtension.class)
class OrderCrossModuleLookupMessageTypeTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Captor
    private ArgumentCaptor<Object> payloadCaptor;

    private final MessageConverter converter = new RabbitMQConfig().jsonMessageConverter();

    private OrderCrossModuleLookupServiceImpl lookupService;

    @BeforeEach
    void setUp() {
        lookupService = new OrderCrossModuleLookupServiceImpl(rabbitTemplate, new ObjectMapper());
    }

    /**
     * Captures the payload the service just sent and mirrors the broker hop:
     * serialise on the sender, deserialise on the consumer.
     */
    private Class<?> typeConsumerReceives() {
        verify(rabbitTemplate).convertSendAndReceive(any(), any(), payloadCaptor.capture());
        Message message = converter.toMessage(payloadCaptor.getValue(), new MessageProperties());
        return converter.fromMessage(message).getClass();
    }

    /**
     * Reads the payload type a @RabbitListener method actually accepts, so these tests fail if a
     * consumer's signature changes without its sender being updated to match.
     */
    private Class<?> listenerPayloadType(Class<?> consumer, String methodName) {
        return Arrays.stream(consumer.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No method " + methodName + " on " + consumer.getName()))
                .getParameterTypes()[0];
    }

    @Test
    void customerLookupReachesConsumerAsDeclaredType() {
        lookupService.lookupCustomer("c97bbb71-d912-4a60-9d07-f8ba7707d8d2");

        assertEquals(
                listenerPayloadType(CustomerLookupConsumer.class, "handleCustomerLookupRequest"),
                typeConsumerReceives());
    }

    @Test
    void customerAddressLookupReachesConsumerAsDeclaredType() {
        lookupService.lookupCustomerAddress(42L, 7L);

        assertEquals(
                listenerPayloadType(CustomerAddressLookupConsumer.class, "handleAddressLookupRequest"),
                typeConsumerReceives());
    }

    @Test
    void paymentMethodLookupReachesConsumerAsDeclaredType() {
        lookupService.lookupPaymentMethod(3L);

        assertEquals(
                listenerPayloadType(PaymentMethodLookupConsumer.class, "handlePaymentMethodLookupRequest"),
                typeConsumerReceives());
    }

    @Test
    void shippingCostCalculationReachesConsumerAsDeclaredType() {
        lookupService.calculateShippingCost("courier-uuid", BigDecimal.ONE, 3L, "CUSTOMER");

        assertEquals(
                listenerPayloadType(ShippingCalculationConsumer.class, "handleShippingCalculation"),
                typeConsumerReceives());
    }

    /**
     * Reproduces the original defect: the generic ObjectNode payload the order module used to send
     * arrives as an ObjectNode, which the consumer cannot accept, so the lookup is never answered.
     */
    @Test
    void genericObjectNodePayloadWouldNotReachConsumerAsDeclaredType() {
        Object legacyPayload = new ObjectMapper().createObjectNode()
                .put("requestId", "req-1")
                .put("customerUuid", "c97bbb71-d912-4a60-9d07-f8ba7707d8d2");

        Message message = converter.toMessage(legacyPayload, new MessageProperties());

        assertNotEquals(
                listenerPayloadType(CustomerLookupConsumer.class, "handleCustomerLookupRequest"),
                converter.fromMessage(message).getClass());
    }
}

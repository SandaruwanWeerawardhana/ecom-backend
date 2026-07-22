package org.psint.beyosclothing.modules.auth.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.auth.entity.User;
import org.psint.beyosclothing.modules.auth.repository.UserRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEmailConsumer {

    private static final String CUSTOMER_TYPE = "CUSTOMER";
    private final UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    @RabbitListener(queues = "${app.rabbitmq.queue.user-email-lookup-request:user.email.lookup.request.queue}")
    public String handleLookupAndReturnEmail(String payload) {
        try {
            Optional<String> email = extractEmailIfCustomer(payload);
            if (email.isPresent()) {
                log.info("Extracted customer email from lookup payload: {}", email.get());
                return email.get();
            }
            log.info("Lookup payload is not a CUSTOMER or does not contain email");
            return null;
        } catch (Exception e) {
            log.error("Error handling customer lookup payload", e);
            return null;
        }
    }

    public List<String> getAllCustomerEmails() {
        try {
            List<User> users = userRepository.findAllByUserTypeIgnoreCaseAndIsActiveTrue(CUSTOMER_TYPE);
            return users.stream()
                    .map(User::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch customer emails", e);
            return Collections.emptyList();
        }
    }

    public Optional<String> extractEmailIfCustomer(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode root = mapper.readTree(jsonPayload);
            JsonNode typeNode = root.path("type");
            if (!typeNode.isTextual()) {
                return Optional.empty();
            }

            if (!CUSTOMER_TYPE.equalsIgnoreCase(typeNode.asText())) {
                return Optional.empty();
            }

            JsonNode emailNode = root.path("email");
            if (emailNode.isTextual()) {
                return Optional.ofNullable(emailNode.asText());
            }

            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to parse customer email message", e);
            return Optional.empty();
        }
    }
}

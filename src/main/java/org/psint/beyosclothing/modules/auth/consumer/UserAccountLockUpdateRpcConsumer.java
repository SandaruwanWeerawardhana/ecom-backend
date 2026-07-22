package org.psint.beyosclothing.modules.auth.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.auth.entity.User;
import org.psint.beyosclothing.modules.auth.repository.UserRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * RPC Consumer for updating the user's account_locked flag.
 * Routing key: auth.user.update.account.locked.rpc
 * Queue: auth.user.update.account.locked.rpc.queue
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserAccountLockUpdateRpcConsumer {

    private final UserRepository userRepository;

    @RabbitListener(queues = "auth.user.update.account.locked.rpc.queue")
    @Transactional("authTransactionManager")
    public Map<String, Object> handle(Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            Object userIdObj = request.get("userId");
            Object accountLockedObj = request.get("accountLocked");

            if (!(userIdObj instanceof Number) || !(accountLockedObj instanceof Boolean)) {
                response.put("success", false);
                response.put("error", "Invalid payload. Expected {userId:number, accountLocked:boolean}");
                return response;
            }

            Long userId = ((Number) userIdObj).longValue();
            boolean accountLocked = (Boolean) accountLockedObj;

            User user = userRepository.findActiveUserById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Active user not found for id=" + userId));

            user.setAccountLocked(accountLocked);
            userRepository.save(user);

            log.info(" Updated accountLocked={} for userId={}", accountLocked, userId);

            response.put("success", true);
            response.put("userId", userId);
            response.put("accountLocked", user.getAccountLocked());
            return response;

        } catch (Exception e) {
            log.error(" Failed to update account lock via RPC. payload={}", request, e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }
}


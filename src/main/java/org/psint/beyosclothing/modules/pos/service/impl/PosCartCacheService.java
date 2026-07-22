package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.dto.response.PosCartItemResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosCartResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class PosCartCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CART_CACHE_PREFIX = "pos:cart:terminal:"; // base
    private static final String ITEMS_KEY_SUFFIX = ":items";
    private static final String ITEMS_HASH_SUFFIX = ":items:hash";
    private static final long CART_CACHE_TTL_SECONDS = TimeUnit.HOURS.toSeconds(4);
    private static final String ACTIVE_KEYS_SET = "pos:cart:active_keys";

    public String getMetaKey(String terminalUuid) {
        return CART_CACHE_PREFIX + terminalUuid;
    }

    public String getItemsZSetKey(String terminalUuid) {
        return CART_CACHE_PREFIX + terminalUuid + ITEMS_KEY_SUFFIX;
    }

    public String getItemsHashKey(String terminalUuid) {
        return CART_CACHE_PREFIX + terminalUuid + ITEMS_HASH_SUFFIX;
    }

    public void cacheFullCart(String terminalUuid, PosCartResponse resp) {
        try {
            String metaKey = getMetaKey(terminalUuid);
            String itemsHash = getItemsHashKey(terminalUuid);
            String itemsSet = getItemsZSetKey(terminalUuid);

            // store meta as a hash
            Map<String, Object> meta = new HashMap<>();
            meta.put("uuid", resp.getUuid());
            meta.put("terminalId", resp.getTerminalId());
            meta.put("cashierId", resp.getCashierId());
            meta.put("customerId", resp.getCustomerId());
            meta.put("subtotal", resp.getSubtotal());
            meta.put("taxAmount", resp.getTaxAmount());
            meta.put("taxPercentage", resp.getTaxPercentage());
            meta.put("discountAmount", resp.getDiscountAmount());
            meta.put("total", resp.getTotal());
            meta.put("isActive", resp.getIsActive());
            meta.put("createdAt", resp.getCreatedAt());
            meta.put("updatedAt", resp.getUpdatedAt());
            meta.put("lastModified", Instant.now().toString());
            meta.put("opsCount", 0);
            meta.put("lastSyncedAt", Instant.now().toString());

            redisTemplate.opsForHash().putAll(metaKey, meta);

            // store items
            if (resp.getItems() != null && !resp.getItems().isEmpty()) {
                long now = Instant.now().toEpochMilli();
                for (int i = 0; i < resp.getItems().size(); i++) {
                    PosCartItemResponse item = resp.getItems().get(i);
                    redisTemplate.opsForHash().put(itemsHash, item.getUuid(), item);
                    redisTemplate.opsForZSet().add(itemsSet, item.getUuid(), now + i);
                }
            }

            // add to active set for scheduler
            redisTemplate.opsForSet().add(ACTIVE_KEYS_SET, metaKey);

            // set TTL
            redisTemplate.expire(metaKey, CART_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            redisTemplate.expire(itemsHash, CART_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            redisTemplate.expire(itemsSet, CART_CACHE_TTL_SECONDS, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.warn("Failed to cache cart in Redis for terminal {}: {}", terminalUuid, e.getMessage());
        }
    }

    public Optional<PosCartResponse> loadCartFromCache(String terminalUuid) {
        try {
            String metaKey = getMetaKey(terminalUuid);
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(metaKey))) {
                return Optional.empty();
            }

            Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey);
            if (meta == null || meta.isEmpty()) return Optional.empty();

            PosCartResponse resp = new PosCartResponse();
            resp.setUuid((String) meta.get("uuid"));
            resp.setTerminalId((Long) meta.get("terminalId"));
            resp.setCashierId((Long) meta.get("cashierId"));
            resp.setCustomerId((Long) meta.get("customerId"));
            resp.setSubtotal(meta.get("subtotal") == null ? 0.0 : Double.parseDouble(meta.get("subtotal").toString()));
            resp.setTaxAmount(meta.get("taxAmount") == null ? 0.0 : Double.parseDouble(meta.get("taxAmount").toString()));
            resp.setTaxPercentage(meta.get("taxPercentage") == null ? 0.0 : Double.parseDouble(meta.get("taxPercentage").toString()));
            resp.setDiscountAmount(meta.get("discountAmount") == null ? 0.0 : Double.parseDouble(meta.get("discountAmount").toString()));
            resp.setTotal(meta.get("total") == null ? 0.0 : Double.parseDouble(meta.get("total").toString()));
            resp.setIsActive(meta.get("isActive") == null ? Boolean.FALSE : Boolean.parseBoolean(meta.get("isActive").toString()));
            resp.setCreatedAt(meta.get("createdAt") == null ? null : (java.time.LocalDateTime) meta.get("createdAt"));
            resp.setUpdatedAt(meta.get("updatedAt") == null ? null : (java.time.LocalDateTime) meta.get("updatedAt"));

            String itemsHash = getItemsHashKey(terminalUuid);
            Map<Object, Object> items = redisTemplate.opsForHash().entries(itemsHash);
            if (items != null && !items.isEmpty()) {
                List<PosCartItemResponse> itemList = new ArrayList<>();
                for (Object value : items.values()) {
                    if (value instanceof PosCartItemResponse) {
                        itemList.add((PosCartItemResponse) value);
                    }
                }
                // sort by zset order
                String itemsSet = getItemsZSetKey(terminalUuid);
                Set<Object> ordered = redisTemplate.opsForZSet().range(itemsSet, 0, -1);
                if (ordered != null && !ordered.isEmpty()) {
                    List<PosCartItemResponse> orderedList = new ArrayList<>();
                    for (Object member : ordered) {
                        String itemUuid = (String) member;
                        Object obj = redisTemplate.opsForHash().get(itemsHash, itemUuid);
                        if (obj instanceof PosCartItemResponse) orderedList.add((PosCartItemResponse) obj);
                    }
                    resp.setItems(orderedList);
                } else {
                    resp.setItems(itemList);
                }
            } else {
                resp.setItems(Collections.emptyList());
            }

            return Optional.of(resp);
        } catch (Exception e) {
            log.warn("Failed to load cart from Redis for terminal {}: {}", terminalUuid, e.getMessage());
            return Optional.empty();
        }
    }

    public void updateMetaAfterWrite(String terminalUuid) {
        try {
            String metaKey = getMetaKey(terminalUuid);
            redisTemplate.opsForHash().increment(metaKey, "opsCount", 1);
            redisTemplate.opsForHash().put(metaKey, "lastModified", Instant.now().toString());
            redisTemplate.expire(metaKey, CART_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to update meta after write for {}: {}", terminalUuid, e.getMessage());
        }
    }

    public int getOpsCount(String terminalUuid) {
        try {
            String metaKey = getMetaKey(terminalUuid);
            Object v = redisTemplate.opsForHash().get(metaKey, "opsCount");
            if (v == null) return 0;
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    public Optional<Instant> getLastSyncedAt(String terminalUuid) {
        try {
            String metaKey = getMetaKey(terminalUuid);
            Object v = redisTemplate.opsForHash().get(metaKey, "lastSyncedAt");
            if (v == null) return Optional.empty();
            return Optional.of(Instant.parse(v.toString()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void markSynced(String terminalUuid) {
        try {
            String metaKey = getMetaKey(terminalUuid);
            redisTemplate.opsForHash().put(metaKey, "lastSyncedAt", Instant.now().toString());
            redisTemplate.opsForHash().put(metaKey, "opsCount", 0);
        } catch (Exception e) {
            log.warn("Failed to mark synced for {}: {}", terminalUuid, e.getMessage());
        }
    }

    public void addOrUpdateItem(String terminalUuid, PosCartItemResponse item) {
        try {
            String itemsHash = getItemsHashKey(terminalUuid);
            String itemsSet = getItemsZSetKey(terminalUuid);
            // if not exists in zset, add with now score
            Boolean exists = redisTemplate.opsForHash().hasKey(itemsHash, item.getUuid());
            redisTemplate.opsForHash().put(itemsHash, item.getUuid(), item);
            if (exists == null || !exists) {
                redisTemplate.opsForZSet().add(itemsSet, item.getUuid(), Instant.now().toEpochMilli());
            }
            redisTemplate.expire(itemsHash, CART_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            redisTemplate.expire(itemsSet, CART_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to add/update item in Redis for terminal {}: {}", terminalUuid, e.getMessage());
        }
    }

    public void removeItem(String terminalUuid, String itemUuid) {
        try {
            String itemsHash = getItemsHashKey(terminalUuid);
            String itemsSet = getItemsZSetKey(terminalUuid);
            redisTemplate.opsForHash().delete(itemsHash, itemUuid);
            redisTemplate.opsForZSet().remove(itemsSet, itemUuid);
        } catch (Exception e) {
            log.warn("Failed to remove item in Redis for terminal {}: {}", terminalUuid, e.getMessage());
        }
    }

    public boolean metaKeyExists(String metaKey) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(metaKey));
        } catch (Exception e) {
            log.warn("Failed to check existence of meta key {}: {}", metaKey, e.getMessage());
            return true;
        }
    }

    /**
     * Removes a stale member from the active-keys tracking set. Cart keys expire via TTL,
     * but the tracking set has no TTL — without this cleanup it grows without bound.
     */
    public void removeActiveKey(String metaKey) {
        try {
            redisTemplate.opsForSet().remove(ACTIVE_KEYS_SET, metaKey);
        } catch (Exception e) {
            log.warn("Failed to remove stale active cart key {}: {}", metaKey, e.getMessage());
        }
    }

    public Set<String> getActiveCartMetaKeys() {
        try {
            Set<Object> members = redisTemplate.opsForSet().members(ACTIVE_KEYS_SET);
            if (members == null) return Collections.emptySet();
            Set<String> keys = new HashSet<>();
            for (Object o : members) keys.add(o.toString());
            return keys;
        } catch (Exception e) {
            log.warn("Failed to read active cart keys set: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    public void removeCacheForTerminal(String terminalUuid) {
        try {
            String metaKey = getMetaKey(terminalUuid);
            String itemsHash = getItemsHashKey(terminalUuid);
            String itemsSet = getItemsZSetKey(terminalUuid);
            redisTemplate.delete(metaKey);
            redisTemplate.delete(itemsHash);
            redisTemplate.delete(itemsSet);
            redisTemplate.opsForSet().remove(ACTIVE_KEYS_SET, metaKey);
        } catch (Exception e) {
            log.warn("Failed to remove cache for terminal {}: {}", terminalUuid, e.getMessage());
        }
    }
}

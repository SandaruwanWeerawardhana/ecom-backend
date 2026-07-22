package org.psint.beyosclothing.modules.products.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.products.dto.request.AddAttributeValuesRequest;
import org.psint.beyosclothing.modules.products.dto.request.CreateAttributeWithValuesRequest;
import org.psint.beyosclothing.modules.products.dto.request.UpdateAttributeRequest;
import org.psint.beyosclothing.modules.products.dto.response.AttributeResponse;
import org.psint.beyosclothing.modules.products.entity.ProductAttribute;
import org.psint.beyosclothing.modules.products.entity.ProductAttributeValue;
import org.psint.beyosclothing.modules.products.exception.DuplicateResourceException;
import org.psint.beyosclothing.modules.products.exception.InvalidRequestException;
import org.psint.beyosclothing.modules.products.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.products.repository.ProductAttributeRepository;
import org.psint.beyosclothing.modules.products.repository.ProductAttributeValueRepository;
import org.psint.beyosclothing.modules.products.service.ProductAttributeService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Product Attribute Service Implementation
 * Handles business logic for product attributes and their values
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductAttributeServiceImpl implements ProductAttributeService {

    private final ProductAttributeRepository attributeRepository;
    private final ProductAttributeValueRepository attributeValueRepository;

    @Override
    @Transactional
    @CacheEvict(value = "attributes", allEntries = true)
    public AttributeResponse createAttributeWithValues(CreateAttributeWithValuesRequest request) {
        log.info("Creating attribute '{}' with {} values", request.getAttributeName(), request.getValues().size());

        // Validate attribute name uniqueness
        if (attributeRepository.existsByName(request.getAttributeName()) && attributeRepository.existsActiveByName(request.getAttributeName())   ) {
            throw new DuplicateResourceException("Attribute with name '" + request.getAttributeName() + "' already exists");
        }

        // Validate values are not empty
        if (request.getValues().isEmpty()) {
            throw new InvalidRequestException("At least one attribute value is required");
        }

        // Check for duplicate values in request
        long distinctCount = request.getValues().stream().distinct().count();
        if (distinctCount != request.getValues().size()) {
            throw new InvalidRequestException("Duplicate values found in request");
        }

        // Create attribute
        ProductAttribute attribute = ProductAttribute.builder()
                .name(request.getAttributeName())
                .isActive(true)
                .build();

        ProductAttribute savedAttribute = attributeRepository.save(attribute);
        log.info("Attribute created with UUID: {}", savedAttribute.getUuid());

        // Create attribute values
        List<ProductAttributeValue> attributeValues = new ArrayList<>();
        for (String value : request.getValues()) {
            ProductAttributeValue attributeValue = ProductAttributeValue.builder()
                    .attributeId(savedAttribute.getId())
                    .value(value.trim())
                    .isActive(true)
                    .build();
            attributeValues.add(attributeValue);
        }

        List<ProductAttributeValue> savedValues = attributeValueRepository.saveAll(attributeValues);
        log.info("Created {} attribute values for attribute '{}'", savedValues.size(), savedAttribute.getName());

        return mapToResponse(savedAttribute, savedValues);
    }

    @Override
    @Transactional("productTransactionManager")
    @CacheEvict(value = "attributes", allEntries = true)
    public AttributeResponse addValuesToAttribute(String attributeUuid, AddAttributeValuesRequest request) {
        log.info("Adding {} values to attribute UUID: {}", request.getValues().size(), attributeUuid);

        ProductAttribute attribute = attributeRepository.findByUuid(attributeUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Attribute not found with UUID: " + attributeUuid));

        if (!attribute.getIsActive()) {
            throw new InvalidRequestException("Cannot add values to inactive attribute");
        }

        if (request == null || request.getValues() == null || request.getValues().isEmpty()) {
            throw new InvalidRequestException("At least one attribute value is required");
        }

        // Normalize + validate request values (non-empty + no duplicates in request)
        List<String> trimmedValues = new ArrayList<>();
        Set<String> requestKeys = new HashSet<>();
        for (String value : request.getValues()) {
            if (value == null || value.trim().isEmpty()) {
                throw new InvalidRequestException("Attribute value cannot be empty");
            }
            String trimmed = value.trim();
            String key = normalizeValueKey(trimmed);
            if (!requestKeys.add(key)) {
                throw new InvalidRequestException("Duplicate values found in request");
            }
            trimmedValues.add(trimmed);
        }

        // Get existing values for this attribute (active + inactive)
        List<ProductAttributeValue> existingValues = attributeValueRepository.findByAttributeId(attribute.getId());
        Map<String, ProductAttributeValue> existingValueMap = new HashMap<>();
        for (ProductAttributeValue val : existingValues) {
            if (val.getValue() != null) {
                existingValueMap.put(normalizeValueKey(val.getValue()), val);
            }
        }

        // Check for ACTIVE duplicates; reactivate deactivated values (due to unique constraint)
        List<String> activeDuplicates = new ArrayList<>();
        List<ProductAttributeValue> toReactivate = new ArrayList<>();
        List<ProductAttributeValue> toCreate = new ArrayList<>();

        for (String trimmed : trimmedValues) {
            String key = normalizeValueKey(trimmed);
            ProductAttributeValue existing = existingValueMap.get(key);

            if (existing != null) {
                if (Boolean.TRUE.equals(existing.getIsActive())) {
                    // Value is ACTIVE - reject as duplicate
                    activeDuplicates.add(trimmed);
                } else {
                    // Value is deactivated - reactivate it (can't create new due to unique constraint)
                    existing.setIsActive(true);
                    toReactivate.add(existing);
                    log.info("Reactivating deactivated value: {}", trimmed);
                }
            } else {
                // Value doesn't exist - create new record
                toCreate.add(ProductAttributeValue.builder()
                        .attributeId(attribute.getId())
                        .value(trimmed)
                        .isActive(true)
                        .build());
            }
        }

        if (!activeDuplicates.isEmpty()) {
            throw new DuplicateResourceException("The following values already exist: " + String.join(", ", activeDuplicates));
        }

        if (toReactivate.isEmpty() && toCreate.isEmpty()) {
            throw new InvalidRequestException("No new values to add");
        }

        if (!toReactivate.isEmpty()) {
            attributeValueRepository.saveAll(toReactivate);
            log.info("Reactivated {} deactivated value(s) for attribute '{}'", toReactivate.size(), attribute.getName());
        }
        if (!toCreate.isEmpty()) {
            attributeValueRepository.saveAll(toCreate);
            log.info("Added {} new values to attribute '{}'", toCreate.size(), attribute.getName());
        }

        // Return attribute with ACTIVE values only
        List<ProductAttributeValue> activeValues = attributeValueRepository.findByAttributeId(attribute.getId()).stream()
                .filter(v -> Objects.equals(v.getIsActive(), true))
                .toList();
        return mapToResponse(attribute, activeValues);
    }

    @Override
    @Transactional("productTransactionManager")
    @CacheEvict(value = "attributes", allEntries = true)
    public AttributeResponse updateValuesToAttribute(String attributeUuid, AddAttributeValuesRequest request) {
        int requestSize = (request == null || request.getValues() == null) ? 0 : request.getValues().size();
        log.info("Updating (replacing) {} values for attribute UUID: {}", requestSize, attributeUuid);

        ProductAttribute attribute = attributeRepository.findByUuid(attributeUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Attribute not found with UUID: " + attributeUuid));

        if (!attribute.getIsActive()) {
            throw new InvalidRequestException("Cannot update values for inactive attribute");
        }

        if (request == null || request.getValues() == null || request.getValues().isEmpty()) {
            throw new InvalidRequestException("At least one attribute value is required");
        }

        // Normalize + validate request values (non-empty + no duplicates)
        List<String> trimmedValues = new ArrayList<>();
        Set<String> requestKeys = new HashSet<>();
        for (String value : request.getValues()) {
            if (value == null || value.trim().isEmpty()) {
                throw new InvalidRequestException("Attribute value cannot be empty");
            }
            String trimmed = value.trim();
            String key = normalizeValueKey(trimmed);
            if (!requestKeys.add(key)) {
                throw new InvalidRequestException("Duplicate values found in request");
            }
            trimmedValues.add(trimmed);
        }

        // Existing values (active + inactive)
        List<ProductAttributeValue> existingValues = attributeValueRepository.findByAttributeId(attribute.getId());
        Map<String, ProductAttributeValue> existingValueMap = new HashMap<>();
        for (ProductAttributeValue val : existingValues) {
            if (val.getValue() != null) {
                existingValueMap.put(normalizeValueKey(val.getValue()), val);
            }
        }
        List<ProductAttributeValue> toCreate = new ArrayList<>();

        // Additive semantics:
        // - ACTIVE values already in DB are skipped
        // - INACTIVE matching values are hard-deleted first, then re-inserted as fresh rows
        // - Only values that do not exist at all are inserted
        for (String trimmed : trimmedValues) {
            String key = normalizeValueKey(trimmed);
            ProductAttributeValue existing = existingValueMap.get(key);

            if (existing == null) {
                toCreate.add(ProductAttributeValue.builder()
                        .attributeId(attribute.getId())
                        .value(trimmed)
                        .isActive(true)
                        .build());
            } else {
                if (Boolean.TRUE.equals(existing.getIsActive())) {
                    log.info("Skipping active existing value without changing database state: {}", trimmed);
                } else {
                    // Remove inactive row so the same value can be inserted again with a new UUID
                    attributeValueRepository.delete(existing);
                    toCreate.add(ProductAttributeValue.builder()
                            .attributeId(attribute.getId())
                            .value(trimmed)
                            .isActive(true)
                            .build());
                    log.info("Replaced inactive value with a fresh row: {}", trimmed);
                }
            }
        }

        if (!toCreate.isEmpty()) {
            attributeValueRepository.saveAll(toCreate);
        }

        List<ProductAttributeValue> activeValues = attributeValueRepository.findByAttributeId(attribute.getId()).stream()
                .filter(v -> Objects.equals(v.getIsActive(), true))
                .toList();
        return mapToResponse(attribute, activeValues);
    }

    private static String normalizeValueKey(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    @Override
    @Transactional("productTransactionManager")
    @CacheEvict(value = {"attributes", "activeAttributes"}, allEntries = true)
    public AttributeResponse updateAttribute(String uuid, UpdateAttributeRequest request) {
        log.info("Updating attribute with UUID: {}", uuid);

        ProductAttribute attribute = attributeRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Attribute not found with UUID: " + uuid));

        // Check if name is being changed and if the new name already exists
        if (request.getName() != null && !request.getName().equals(attribute.getName())) {
            if (attributeRepository.existsByName(request.getName())) {
                throw new DuplicateResourceException("Attribute with name '" + request.getName() + "' already exists");
            }
            String oldName = attribute.getName();
            attribute.setName(request.getName());
            log.info("Attribute name updated from '{}' to '{}'", oldName, request.getName());
        }

        // Update isActive status if provided
        if (request.getIsActive() != null) {
            attribute.setIsActive(request.getIsActive());
            log.info("Attribute activation status updated to: {}", request.getIsActive());
        }

        ProductAttribute updatedAttribute = attributeRepository.save(attribute);

        // Update individual attribute values if provided
        if (request.getValues() != null && !request.getValues().isEmpty()) {
            log.info("Updating {} attribute values", request.getValues().size());

            for (UpdateAttributeRequest.AttributeValueUpdate valueUpdate : request.getValues()) {
                ProductAttributeValue attributeValue = attributeValueRepository.findByUuid(valueUpdate.getUuid())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Attribute value not found with UUID: " + valueUpdate.getUuid()));

                 // Verify the value belongs to this attribute
                 if (!attributeValue.getAttributeId().equals(attribute.getId())) {
                     throw new InvalidRequestException(
                             "Attribute value " + valueUpdate.getUuid() + " does not belong to attribute " + uuid);
                 }

                 // Update value text if provided
                if (valueUpdate.getValue() != null) {
                    String newValueTrimmed = valueUpdate.getValue().trim();
                    if (newValueTrimmed.isEmpty()) {
                        throw new InvalidRequestException("Attribute value cannot be empty");
                    }
                    String currentValue = attributeValue.getValue();
                    if (!newValueTrimmed.equals(currentValue)) {
                        // Check for duplicate value in this attribute (compare trimmed values)
                        List<ProductAttributeValue> existingValues = attributeValueRepository.findByAttributeId(attribute.getId());
                        boolean duplicateExists = existingValues.stream()
                                .anyMatch(v -> !v.getUuid().equals(valueUpdate.getUuid())
                                        && v.getValue() != null
                                        && v.getValue().trim().equals(newValueTrimmed));

                        if (duplicateExists) {
                            throw new DuplicateResourceException(
                                    "Value '" + newValueTrimmed + "' already exists for this attribute");
                        }

                        attributeValue.setValue(newValueTrimmed);
                        log.info("Updated value from '{}' to '{}'", currentValue, newValueTrimmed);
                    }
                }

                 // Update isActive status if provided
                 if (valueUpdate.getIsActive() != null) {
                     attributeValue.setIsActive(valueUpdate.getIsActive());
                     log.info("Updated value '{}' activation status to: {}",
                             attributeValue.getValue(), valueUpdate.getIsActive());
                 }

                 attributeValueRepository.save(attributeValue);
             }
         }

         // Get all values to return in response
         List<ProductAttributeValue> values = attributeValueRepository.findByAttributeId(attribute.getId());

         log.info("Attribute updated successfully: {}", uuid);
         return mapToResponse(updatedAttribute, values);
     }

    @Override
    @Cacheable(value = "attributes", key = "#uuid")
    public AttributeResponse getAttributeByUuid(String uuid) {
        log.info("Fetching attribute with UUID: {}", uuid);

        ProductAttribute attribute = attributeRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Attribute not found with UUID: " + uuid));

        // Validate attribute is active
        if (!Objects.equals(attribute.getIsActive(), true)) {
            throw new ResourceNotFoundException("Attribute not found or inactive with UUID: " + uuid);
        }

        List<ProductAttributeValue> values = attributeValueRepository.findByAttributeId(attribute.getId()).stream()
                .filter(value -> Objects.equals(value.getIsActive(), true))
                .collect(Collectors.toList());

        return mapToResponse(attribute, values);
    }

    @Override
    public PageResponse<AttributeResponse> getAllAttributes(Pageable pageable) {
        log.info("Fetching all attributes with pagination");

        Page<ProductAttribute> attributePage = attributeRepository.findAll(pageable);

        List<AttributeResponse> responses = attributePage.getContent().stream()
                .filter(attribute -> Objects.equals(attribute.getIsActive(), true))
                .map(attribute -> {
                    log.info("Attribute: {} isActive: {}", attribute.getName(), attribute.getIsActive());
                    List<ProductAttributeValue> values = attributeValueRepository.findByAttributeId(attribute.getId()).stream()
                            .filter(value -> Objects.equals(value.getIsActive(), true))
                            .collect(Collectors.toList());
                    return mapToResponse(attribute, values);
                })
                .collect(Collectors.toList());

        return PageResponse.<AttributeResponse>builder()
                .content(responses)
                .pageNumber(attributePage.getNumber())
                .pageSize(attributePage.getSize())
                .totalElements((long) responses.size())
                .totalPages((int) Math.ceil((double) responses.size() / pageable.getPageSize()))
                .last(responses.size() <= pageable.getPageSize())
                .first(pageable.getPageNumber() == 0)
                .empty(responses.isEmpty())
                .build();
    }

    @Override
    @Cacheable(value = "activeAttributes")
    public List<AttributeResponse> getAllActiveAttributes() {
        log.info("Fetching all active attributes");

        List<ProductAttribute> attributes = attributeRepository.findAll().stream()
                .filter(ProductAttribute::getIsActive)
                .collect(Collectors.toList());

        return attributes.stream()
                .map(attribute -> {
                    List<ProductAttributeValue> values = attributeValueRepository.findByAttributeId(attribute.getId()).stream()
                            .filter(v -> Objects.equals(v.getIsActive(), true))
                            .collect(Collectors.toList());
                    return mapToResponse(attribute, values);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional("productTransactionManager")
    @CacheEvict(value = {"attributes", "activeAttributes"}, allEntries = true)
    public void deleteAttribute(String uuid) {
        log.info("Deleting attribute with UUID: {}", uuid);

        ProductAttribute attribute = attributeRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Attribute not found with UUID: " + uuid));

        // Soft delete attribute
        attribute.setIsActive(false);
        attributeRepository.save(attribute);

        // Soft delete all attribute values
        List<ProductAttributeValue> values = attributeValueRepository.findByAttributeId(attribute.getId());
        values.forEach(value -> value.setIsActive(false));
        attributeValueRepository.saveAll(values);

        log.info("Attribute and its values soft deleted successfully: {}", uuid);
    }

    @Override
    @Transactional("productTransactionManager")
    @CacheEvict(value = {"attributes", "activeAttributes"}, allEntries = true)
    public void deleteAttributeValue(String attributeUuid, String valueUuid) {
        log.info("Deleting attribute value UUID: {} from attribute UUID: {}", valueUuid, attributeUuid);

        ProductAttribute attribute = attributeRepository.findByUuid(attributeUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Attribute not found with UUID: " + attributeUuid));

        ProductAttributeValue value = attributeValueRepository.findByUuid(valueUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Attribute value not found with UUID: " + valueUuid));

        // Verify the value belongs to the attribute
        if (!value.getAttributeId().equals(attribute.getId())) {
            throw new InvalidRequestException("Attribute value does not belong to the specified attribute");
        }

        // Soft delete
        value.setIsActive(false);
        attributeValueRepository.save(value);

        log.info("Attribute value soft deleted successfully: {}", valueUuid);
    }

    @Override
    @Transactional("productTransactionManager")
    @CacheEvict(value = {"attributes", "activeAttributes"}, allEntries = true)
    public AttributeResponse activateAttribute(String uuid) {
        log.info("Activating attribute with UUID: {}", uuid);

        ProductAttribute attribute = attributeRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Attribute not found with UUID: " + uuid));

        attribute.setIsActive(true);
        ProductAttribute activatedAttribute = attributeRepository.save(attribute);

        // Activate all attribute values
        List<ProductAttributeValue> values = attributeValueRepository.findByAttributeId(attribute.getId());
        values.forEach(value -> value.setIsActive(true));
        List<ProductAttributeValue> activatedValues = attributeValueRepository.saveAll(values);

        log.info("Attribute and {} values activated successfully: {}", activatedValues.size(), uuid);
        return mapToResponse(activatedAttribute, activatedValues);
    }

    // Helper method to map entity to response
    private AttributeResponse mapToResponse(ProductAttribute attribute, List<ProductAttributeValue> values) {
        List<AttributeResponse.AttributeValueResponse> valueResponses = values.stream()
                .map(value -> AttributeResponse.AttributeValueResponse.builder()
                        .uuid(value.getUuid())
                        .value(value.getValue())
                        .isActive(value.getIsActive())
                        .dateCreated(value.getDateCreated())
                        .dateUpdated(value.getDateUpdated())
                        .build())
                .collect(Collectors.toList());

        return AttributeResponse.builder()
                .uuid(attribute.getUuid())
                .name(attribute.getName())
                .isActive(attribute.getIsActive())
                .dateCreated(attribute.getDateCreated())
                .dateUpdated(attribute.getDateUpdated())
                .values(valueResponses)
                .build();
    }
}


package org.psint.beyosclothing.modules.products.service;

import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.products.dto.request.AddAttributeValuesRequest;
import org.psint.beyosclothing.modules.products.dto.request.CreateAttributeWithValuesRequest;
import org.psint.beyosclothing.modules.products.dto.request.UpdateAttributeRequest;
import org.psint.beyosclothing.modules.products.dto.response.AttributeResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Product Attribute Service Interface
 * Business logic for product attributes and their values
 */
public interface ProductAttributeService {

    /**
     * Create a new product attribute with its values in one transaction
     * @param request Attribute and values creation request
     * @return Created attribute with values
     */
    AttributeResponse createAttributeWithValues(CreateAttributeWithValuesRequest request);

    /**
     * Add new values to an existing attribute
     * @param attributeUuid Attribute UUID
     * @param request Request containing new values to add
     * @return Updated attribute with all values
     */
    AttributeResponse addValuesToAttribute(String attributeUuid, AddAttributeValuesRequest request);

    /**
     * Update (replace) all values for an existing attribute
     * @param attributeUuid Attribute UUID
     * @param request Request containing values to replace existing ones
     * @return Updated attribute with new values
     */
    AttributeResponse updateValuesToAttribute(String attributeUuid, AddAttributeValuesRequest request);

    /**
     * Update attribute details (name and/or active status)
     * @param uuid Attribute UUID
     * @param request Update request containing new attribute data
     * @return Updated attribute with values
     */
    AttributeResponse updateAttribute(String uuid, UpdateAttributeRequest request);

    /**
     * Get attribute by UUID with its values
     * @param uuid Attribute UUID
     * @return Attribute details with values
     */
    AttributeResponse getAttributeByUuid(String uuid);

    /**
     * Get all attributes with pagination
     * @param pageable Pagination parameters
     * @return Paginated attribute list
     */
    PageResponse<AttributeResponse> getAllAttributes(Pageable pageable);

    /**
     * Get all active attributes
     * @return List of all active attributes with their values
     */
    List<AttributeResponse> getAllActiveAttributes();

    /**
     * Delete attribute (soft delete - also deactivates all its values)
     * @param uuid Attribute UUID
     */
    void deleteAttribute(String uuid);

    /**
     * Delete a specific attribute value (soft delete)
     * @param attributeUuid Attribute UUID
     * @param valueUuid Attribute value UUID
     */
    void deleteAttributeValue(String attributeUuid, String valueUuid);

    /**
     * Activate attribute
     * @param uuid Attribute UUID
     * @return Updated attribute details
     */
    AttributeResponse activateAttribute(String uuid);
}


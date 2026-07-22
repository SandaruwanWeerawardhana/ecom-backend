package org.psint.beyosclothing.modules.admin.service;

import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.admin.dto.request.AdminCreationRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.request.AdminUpdatePasswordDTO;
import org.psint.beyosclothing.modules.admin.dto.request.AdminUpdateRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.request.AssignRoleRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.response.AdminDetailsResponseDTO;
import org.psint.beyosclothing.modules.customers.dto.external.CustomerDetailsLookupResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Admin Service Interface
 * Business logic for admin management
 */
public interface AdminService {

    /**
     * Create a new admin user
     * @param request Admin creation request
     * @param createdBy Who created this admin
     * @return Created admin details
     */
    AdminDetailsResponseDTO createAdmin(AdminCreationRequestDTO request, String createdBy);

    /**
     * Assign role to an admin user
     * @param request Role assignment request
     * @param assignedBy Who assigned the role
     * @return Updated admin details
     */
    AdminDetailsResponseDTO assignRoleToAdmin(AssignRoleRequestDTO request, String assignedBy);

    /**
     * Get admin by ID
     * @param adminId Admin ID
     * @return Admin details
     */
    AdminDetailsResponseDTO getAdminById(Long adminId);

    /**
     * Get admin by user ID
     * @param userId User ID from auth module
     * @return Admin details
     */
    AdminDetailsResponseDTO getAdminByUserId(Long userId);

    /**
     * Get admin by UUID
     * @param uuid Admin UUID
     * @return Admin details
     */
    AdminDetailsResponseDTO getAdminByUuid(String uuid);

    /**
     * Get all admins with pagination
     * @param pageable Pagination parameters
     * @return Paginated admin list
     */
    PageResponse<AdminDetailsResponseDTO> getAllAdmins(Pageable pageable);

    /**
     * Get all admin details (paginated)
     * @return paginated admin details
     */
    PageResponse<AdminDetailsResponseDTO> getAllAdminDetails();

    /**
     * Update admin details
     * @param adminId Admin ID
     * @param request Update request
     * @param updatedBy Who updated
     * @return Updated admin details
     */
    AdminDetailsResponseDTO updateAdmin(Long adminId, AdminUpdateRequestDTO request, String updatedBy);

    /**
     * Update admin password
     * @param adminId Admin ID
     * @param request Update request containing password
     * @param updatedBy Who updated
     * @return Updated admin details
     */
    AdminDetailsResponseDTO updatePassword(Long adminId, AdminUpdatePasswordDTO request);

    /**
     * Deactivate admin (soft delete)
     * @param adminId Admin ID
     * @param deactivatedBy Who deactivated
     */
    void deactivateAdmin(Long adminId, String deactivatedBy);

    /**
     * Check if user has specific permission
     * @param userId User ID
     * @param permissionCode Permission code
     * @return true if user has permission
     */
    boolean hasPermission(Long userId, String permissionCode);

    /**
     * Fetch paginated customer details from the Customer module via RabbitMQ
     * @param page zero-based page index
     * @param size page size
     * @param includeAddresses whether to include addresses in response
     * @return paginated list of customer details
     */
    PageResponse<CustomerDetailsLookupResponse> getAllCustomerDetails(int page, int size, boolean includeAddresses);
}

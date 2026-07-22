package org.psint.beyosclothing.modules.admin.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * RPC Request: Lookup users whose role matches one of the given roleCodes,
 * together with each role's active permissions, from Auth DB.
 * Published by : Admin module  (AdminServiceImpl)
 * Consumed by  : Auth module   (AdminRolePermissionLookupConsumer)
 * Request  → exchange: beyos.exchange.admin
 *            routing-key: admin.role.permission.lookup.request
 * Response ← List<Map> with keys:
 *             userId, roleCode, roleName, userType,
 *             permissions: [ {permissionId, permissionCode, permissionName, description, module} ]
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRolePermissionEvent implements Serializable {

    /** Correlation id for tracing */
    private String requestId;

    private List<String> roleCodes;
}


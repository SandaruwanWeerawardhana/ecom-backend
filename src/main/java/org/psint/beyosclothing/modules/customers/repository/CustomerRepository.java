package org.psint.beyosclothing.modules.customers.repository;

import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Customer Repository
 * Database: beyos_customers_db
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    long countByIsActiveTrue();

    Optional<Customer> findByUserId(Long userId);

    Optional<Customer> findByUuid(String uuid);

    Optional<Customer> findByEmail(String email);

    boolean existsByUserId(Long userId);

    boolean existsByEmail(String email);

    @Query("SELECT c FROM Customer c WHERE c.userId = :userId AND c.isActive = true")
    Optional<Customer> findActiveCustomerByUserId(@Param("userId") Long userId);

    Optional<Customer> findById(Long id);

    // Added for cross-module lookups: find customers by UUID list
    List<Customer> findAllByUuidIn(List<String> uuids);

    // Support paginated active customer lookup
    Page<Customer> findAllByIsActiveTrue(Pageable pageable);

    /**
     * Search active customers for POS autocomplete
     * Searches by name, phone, or email with partial matching
     * Results ordered by most recent customers first (dateCreated DESC)
     * Optimized for < 100ms response time
     */
    @Query("SELECT c FROM Customer c WHERE c.isActive = true AND " +
            "(LOWER(c.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(c.lastName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(CONCAT(c.firstName, ' ', c.lastName)) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "c.phoneNumber LIKE CONCAT('%', :q, '%')) " +
            "ORDER BY c.dateCreated DESC")
    List<Customer> searchActiveCustomers(@Param("q") String q, Pageable pageable);
}

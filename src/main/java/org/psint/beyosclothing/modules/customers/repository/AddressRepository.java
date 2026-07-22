package org.psint.beyosclothing.modules.customers.repository;

import org.psint.beyosclothing.modules.customers.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Address Repository
 * Database: beyos_customers_db
 */
@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByCustomerId(Long customerId);

    @Query("SELECT a FROM Address a WHERE a.customer.id = :customerId AND a.isDefault = true")
    Optional<Address> findDefaultAddressByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT a FROM Address a WHERE a.customer.id = :customerId AND a.isActive = true")
    List<Address> findActiveAddressesByCustomerId(@Param("customerId") Long customerId);
}


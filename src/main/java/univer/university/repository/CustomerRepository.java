package univer.university.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univer.university.entity.Customer;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    /** Parallel to'lovlar qarzdan oshib ketmasligi uchun mijoz qatori bloklanadi. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> lockById(@Param("id") Long id);
}

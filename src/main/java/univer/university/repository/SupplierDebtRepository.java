package univer.university.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univer.university.entity.SupplierDebt;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SupplierDebtRepository extends JpaRepository<SupplierDebt, Long> {

    List<SupplierDebt> findByClosedFalseOrderByDueDateAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SupplierDebt d where d.id = :id")
    Optional<SupplierDebt> lockById(@Param("id") Long id);

    @Query("select coalesce(sum(d.remaining), 0) from SupplierDebt d where d.closed = false")
    BigDecimal sumOpen();
}

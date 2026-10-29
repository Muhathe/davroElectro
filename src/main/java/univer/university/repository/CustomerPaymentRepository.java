package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univer.university.entity.CustomerPayment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, Long> {

    List<CustomerPayment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @Query("select coalesce(sum(p.amount), 0) from CustomerPayment p where p.customerId = :cid")
    BigDecimal sumByCustomer(@Param("cid") Long customerId);

    @Query("select coalesce(sum(p.amount), 0) from CustomerPayment p where p.createdAt >= :fromDt and p.createdAt < :toDt")
    BigDecimal sumBetween(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);
}

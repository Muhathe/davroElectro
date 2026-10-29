package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univer.university.entity.Sale;
import univer.university.entity.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    Optional<Sale> findByCheckNo(String checkNo);

    List<Sale> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    List<Sale> findByCustomerIdAndPaymentTypeOrderByCreatedAtDesc(Long customerId, PaymentType paymentType);

    @Query("select coalesce(sum(s.total), 0) from Sale s where s.createdAt >= :fromDt and s.createdAt < :toDt")
    BigDecimal sumTotal(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);

    @Query("select count(s) from Sale s where s.createdAt >= :fromDt and s.createdAt < :toDt")
    long countInRange(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);

    @Query("select coalesce(sum(s.total), 0) from Sale s where s.createdAt >= :fromDt and s.createdAt < :toDt "
            + "and s.paymentType <> univer.university.entity.enums.PaymentType.NASIYA")
    BigDecimal sumNonCredit(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);

    @Query("select coalesce(sum(s.total), 0) from Sale s where s.customerId = :cid "
            + "and s.paymentType = univer.university.entity.enums.PaymentType.NASIYA")
    BigDecimal sumCreditByCustomer(@Param("cid") Long customerId);

    @Query("select s.userId, count(s), coalesce(sum(s.total), 0) from Sale s "
            + "where s.createdAt >= :fromDt and s.createdAt < :toDt group by s.userId")
    List<Object[]> groupByUser(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);

    @Query("select s.customerId, count(s), coalesce(sum(s.total), 0) from Sale s "
            + "where s.customerId is not null and s.createdAt >= :fromDt group by s.customerId")
    List<Object[]> groupByCustomer(@Param("fromDt") LocalDateTime fromDt);

    @Query("select s.customerId, max(s.createdAt) from Sale s where s.customerId is not null group by s.customerId")
    List<Object[]> lastPurchaseByCustomer();

    @Query("select x.createdAt, x.total from Sale x where x.createdAt >= :fromDt and x.createdAt < :toDt")
    List<Object[]> rowsBetween(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);
}

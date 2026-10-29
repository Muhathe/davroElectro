package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univer.university.entity.SaleReturn;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {

    List<SaleReturn> findTop20ByOrderByCreatedAtDesc();

    @Query("select coalesce(sum(r.total), 0) from SaleReturn r where r.createdAt >= :fromDt and r.createdAt < :toDt")
    BigDecimal sumTotal(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);

    /** Pul chiqargan (nasiyasiz) vozvratlar. */
    @Query("select coalesce(sum(r.total), 0) from SaleReturn r "
            + "where r.createdAt >= :fromDt and r.createdAt < :toDt and r.toDebt = false")
    BigDecimal sumCashReturns(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);

    @Query("select coalesce(sum(r.total), 0) from SaleReturn r where r.customerId = :cid and r.toDebt = true")
    BigDecimal sumToDebtByCustomer(@Param("cid") Long customerId);

    @Query("select r.userId, count(r), coalesce(sum(r.total), 0) from SaleReturn r "
            + "where r.createdAt >= :fromDt and r.createdAt < :toDt group by r.userId")
    List<Object[]> groupByUser(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);

    @Query("select r.customerId, coalesce(sum(r.total), 0) from SaleReturn r "
            + "where r.customerId is not null and r.createdAt >= :fromDt group by r.customerId")
    List<Object[]> sumByCustomer(@Param("fromDt") LocalDateTime fromDt);

    @Query("select x.createdAt, x.total from SaleReturn x where x.createdAt >= :fromDt and x.createdAt < :toDt")
    List<Object[]> rowsBetween(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);
}

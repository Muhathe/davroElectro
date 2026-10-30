package univer.university.repository;

import univer.university.entity.enums.FiscalStatus;
import org.springframework.data.domain.Pageable;
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

    /** Fiskal navbati: urinishlar soni max dan kam bo'lgan cheklar. */
    @Query("select x.id from SaleReturn x where x.fiscal.status = :st and coalesce(x.fiscal.attempts, 0) < :max order by x.id")
    List<Long> fiscalQueue(@Param("st") FiscalStatus st, @Param("max") int max, Pageable page);
}

package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univer.university.entity.CashOperation;
import univer.university.entity.enums.CashDirection;
import univer.university.entity.enums.PayMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface CashOperationRepository extends JpaRepository<CashOperation, Long> {

    @Query("select coalesce(sum(c.amount), 0) from CashOperation c where c.direction = :dir")
    BigDecimal sumByDirection(@Param("dir") CashDirection dir);

    @Query("select coalesce(sum(c.amount), 0) from CashOperation c where c.direction = :dir and c.createdAt < :beforeDt")
    BigDecimal sumByDirectionBefore(@Param("dir") CashDirection dir, @Param("beforeDt") LocalDateTime beforeDt);

    List<CashOperation> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
            LocalDateTime from, LocalDateTime to);

    @Query("select coalesce(sum(c.amount), 0) from CashOperation c where c.direction = :dir and c.method = :method")
    BigDecimal sumByDirectionAndMethod(@Param("dir") CashDirection dir, @Param("method") PayMethod method);

    @Query("select coalesce(sum(c.amount), 0) from CashOperation c "
            + "where c.direction = :dir and c.createdAt >= :from and c.createdAt < :to")
    BigDecimal sumByDirectionBetween(@Param("dir") CashDirection dir,
                                     @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(c.amount), 0) from CashOperation c "
            + "where c.direction = :dir and c.method = :method and c.createdAt >= :from and c.createdAt < :to")
    BigDecimal sumByDirectionAndMethodBetween(@Param("dir") CashDirection dir, @Param("method") PayMethod method,
                                              @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univer.university.entity.Expense;
import univer.university.entity.enums.ExpenseType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    boolean existsByCategory_Id(Long categoryId);

    @Query("select coalesce(sum(e.amount), 0) from Expense e "
            + "where e.status = univer.university.entity.enums.ExpenseStatus.ACTIVE "
            + "and e.createdAt >= :fromDt and e.createdAt < :toDt")
    BigDecimal sumActive(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);

    @Query("select coalesce(sum(e.amount), 0) from Expense e "
            + "where e.status = univer.university.entity.enums.ExpenseStatus.ACTIVE and e.category.type = :type "
            + "and e.createdAt >= :fromDt and e.createdAt < :toDt")
    BigDecimal sumActiveByType(@Param("type") ExpenseType type,
                               @Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);
}

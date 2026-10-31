package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univer.university.entity.CashShift;
import univer.university.entity.enums.ShiftStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CashShiftRepository extends JpaRepository<CashShift, Long> {

    Optional<CashShift> findByUserIdAndStatus(Long userId, ShiftStatus status);

    Optional<CashShift> findFirstByStatusOrderByOpenedAtDesc(ShiftStatus status);

    @Query("select s from CashShift s where s.openedAt >= :from and s.openedAt < :to order by s.openedAt desc")
    List<CashShift> findByPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    boolean existsByUserIdAndStatus(Long userId, ShiftStatus status);
}

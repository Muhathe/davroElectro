package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univer.university.entity.RecurringPayment;

import java.util.List;

public interface RecurringPaymentRepository extends JpaRepository<RecurringPayment, Long> {

    List<RecurringPayment> findAllByOrderByDayOfMonthAscIdAsc();

    List<RecurringPayment> findByActiveTrueOrderByDayOfMonthAscIdAsc();
}

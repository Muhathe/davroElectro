package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univer.university.entity.FinancePlan;

import java.util.Optional;

public interface FinancePlanRepository extends JpaRepository<FinancePlan, Long> {

    Optional<FinancePlan> findByMonth(String month);
}

package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univer.university.entity.ExpenseCategory;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    Optional<ExpenseCategory> findByName(String name);

    List<ExpenseCategory> findAllByOrderByIdAsc();
}

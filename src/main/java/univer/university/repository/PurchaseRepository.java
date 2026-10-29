package univer.university.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import univer.university.entity.Purchase;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Page<Purchase> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

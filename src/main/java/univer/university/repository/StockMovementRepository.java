package univer.university.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import univer.university.entity.StockMovement;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);

    Page<StockMovement> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Query("select m.productId, max(m.createdAt) from StockMovement m "
            + "where m.type = univer.university.entity.enums.MovementType.INCOMING group by m.productId")
    List<Object[]> lastIncomingByProduct();
}

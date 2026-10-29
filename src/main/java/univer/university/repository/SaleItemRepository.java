package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univer.university.entity.SaleItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    @Query("select coalesce(sum(i.qty * i.cost), 0) from SaleItem i "
            + "where i.sale.createdAt >= :fromDt and i.sale.createdAt < :toDt")
    BigDecimal sumCost(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);

    @Query("select i.productId, sum(i.qty) from SaleItem i where i.sale.createdAt >= :fromDt group by i.productId")
    List<Object[]> soldQtyByProduct(@Param("fromDt") LocalDateTime fromDt);

    @Query("select s.userId, coalesce(sum(i.qty * (i.price - i.cost)), 0) from SaleItem i join i.sale s "
            + "where s.createdAt >= :fromDt and s.createdAt < :toDt group by s.userId")
    List<Object[]> grossByUser(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);

    @Query("select s.userId, i.productId, sum(i.qty * i.price) from SaleItem i join i.sale s "
            + "where s.createdAt >= :fromDt and s.createdAt < :toDt group by s.userId, i.productId")
    List<Object[]> revenueByUserAndProduct(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);

    @Query("select s.customerId, coalesce(sum(i.qty * (i.price - i.cost)), 0) from SaleItem i join i.sale s "
            + "where s.customerId is not null and s.createdAt >= :fromDt group by s.customerId")
    List<Object[]> grossByCustomer(@Param("fromDt") LocalDateTime fromDt);
}

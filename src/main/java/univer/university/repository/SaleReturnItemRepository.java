package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univer.university.entity.SaleReturnItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleReturnItemRepository extends JpaRepository<SaleReturnItem, Long> {

    @Query("select coalesce(sum(i.qty * i.cost), 0) from SaleReturnItem i "
            + "where i.saleReturn.createdAt >= :fromDt and i.saleReturn.createdAt < :toDt")
    BigDecimal sumCost(@Param("fromDt") LocalDateTime fromDt, @Param("toDt") LocalDateTime toDt);

    /** Chek bo'yicha avval qaytarilgan miqdor (tovar kesimida). */
    @Query("select i.productId, sum(i.qty) from SaleReturnItem i where i.saleReturn.saleId = :saleId group by i.productId")
    List<Object[]> returnedQtyBySale(@Param("saleId") Long saleId);

    @Query("select r.customerId, coalesce(sum(i.qty * (i.price - i.cost)), 0) from SaleReturnItem i join i.saleReturn r "
            + "where r.customerId is not null and r.createdAt >= :fromDt group by r.customerId")
    List<Object[]> grossByCustomer(@Param("fromDt") LocalDateTime fromDt);
}

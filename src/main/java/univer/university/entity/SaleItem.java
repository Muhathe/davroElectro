package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "sale_items", indexes = {
        @Index(name = "idx_sale_item_sale", columnList = "sale_id"),
        @Index(name = "idx_sale_item_product", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal qty;

    /** Chegirmadan keyingi sotuv narxi. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    /** Sotuv paytidagi tannarx. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal cost;
}

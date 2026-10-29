package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;
import univer.university.entity.enums.MovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_movement_product", columnList = "product_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MovementType type;

    /** Ishorali miqdor: kirimda +, sotuvda -. */
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal qty;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal cost;

    /** Hujjat id (sotuv, vozvrat, kirim). */
    private Long refId;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

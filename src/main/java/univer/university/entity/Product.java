package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;
import univer.university.component.DateUtils;
import univer.university.entity.enums.Unit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_product_barcode", columnList = "barcode")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, length = 40)
    private String barcode;

    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Unit unit;

    /** O'rtacha tortilgan tannarx. */
    @Builder.Default
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal avgCost = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal lastCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal qty = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal minQty = BigDecimal.ZERO;

    private Long supplierId;

    /** Rasm fayli nomi (kontent xeshi); bo'sh bo'lsa rasm yo'q. */
    @Column(length = 32)
    private String imageKey;

    /** MXIK (IKPU) kodi — fiskal chek uchun. */
    @Column(length = 17)
    private String mxikCode;

    /** O'lchov birligi / qadoq kodi — fiskal chek uchun. */
    @Column(length = 20)
    private String packageCode;

    /** QQS stavkasi, %. */
    @Column(precision = 5, scale = 2)
    private BigDecimal vatPercent;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime createdAt = DateUtils.now();
}

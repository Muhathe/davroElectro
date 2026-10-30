package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;
import univer.university.entity.enums.ReturnReason;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sale_returns", indexes = {
        @Index(name = "idx_return_sale", columnList = "sale_id"),
        @Index(name = "idx_return_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_id", nullable = false)
    private Long saleId;

    /** Chek mijozi (nasiya qarzini hisoblash uchun). */
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnReason reason;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal total;

    /** Chek nasiya bo'lgan: pul chiqmaydi, mijoz qarzi kamayadi. */
    @Column(nullable = false)
    private boolean toDebt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private Long userId;

    @Embedded
    private FiscalData fiscal;

    @Builder.Default
    @OneToMany(mappedBy = "saleReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SaleReturnItem> items = new ArrayList<>();

    public void addItem(SaleReturnItem item) {
        item.setSaleReturn(this);
        items.add(item);
    }
}

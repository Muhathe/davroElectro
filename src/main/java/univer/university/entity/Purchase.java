package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;
import univer.university.entity.enums.PayMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** To'liq to'langan kirimda bo'sh bo'lishi mumkin; qarz qolsa StockService majburiy qiladi. */
    private Long supplierId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal paid;

    /** To'langan qism qaysi usulda to'langani. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PayMethod payMethod;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Long userId;

    @Builder.Default
    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseItem> items = new ArrayList<>();

    public void addItem(PurchaseItem item) {
        item.setPurchase(this);
        items.add(item);
    }
}

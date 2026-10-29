package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Kassa prognozi uchun rejadagi doimiy to'lovlar (ijara, oylik, kommunal, soliq, egasi puli).
 * Summa belgilangan yoki oldingi oy tushumining foizi bo'lishi mumkin.
 */
@Entity
@Table(name = "recurring_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String label;

    /** Harajat kategoriyasi nomi (Ijara, Oylik, Soliq, Egasi olgan pul ...). */
    @Column(nullable = false)
    private String category;

    /** Oyning kuni, 1 dan 28 gacha. */
    @Column(nullable = false)
    private int dayOfMonth;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    /** Oldingi oy tushumidan foiz (soliq uchun). amount bo'sh bo'lsa ishlatiladi. */
    @Column(precision = 5, scale = 2)
    private BigDecimal percentOfPrevRevenue;

    /** Egasi olgan pul (OWNER_DRAW) ekanini bildiradi. */
    @Column(nullable = false)
    private boolean ownerDraw;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}

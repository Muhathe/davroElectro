package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;
import univer.university.component.DateUtils;
import univer.university.entity.enums.ShiftStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Kassa smenasi: ochilish/yopilish vaqti, naqd qoldig'i taqqoslash. */
@Entity
@Table(name = "cash_shifts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Smenani ochgan xodim. */
    @Column(nullable = false)
    private Long userId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ShiftStatus status = ShiftStatus.OPEN;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime openedAt = DateUtils.now();

    /** Yopilish vaqti (OPEN holatida null). */
    private LocalDateTime closedAt;

    /** Smenani ochishda sanab topilgan naqd pul. */
    @Builder.Default
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal openingCash = BigDecimal.ZERO;

    /** Smenani yopishda sanab topilgan naqd pul (OPEN holatida null). */
    @Column(precision = 18, scale = 2)
    private BigDecimal closingCash;

    /** Kutilgan naqd = openingCash + smena davomidagi naqd kirimi - naqd chiqimi. */
    @Column(precision = 18, scale = 2)
    private BigDecimal expectedCash;

    /** closingCash - expectedCash (musbat = ortiqcha, manfiy = kamomad). */
    @Column(precision = 18, scale = 2)
    private BigDecimal difference;

    @Column(length = 300)
    private String openNote;

    @Column(length = 300)
    private String closeNote;
}

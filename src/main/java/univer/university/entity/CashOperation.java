package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;
import univer.university.entity.enums.CashDirection;
import univer.university.entity.enums.CashSource;
import univer.university.entity.enums.PayMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Kassa yagona manbasi. Qoldiq = SUM(IN) - SUM(OUT). Yozish faqat CashService orqali. */
@Entity
@Table(name = "cash_operations", indexes = {
        @Index(name = "idx_cash_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private CashDirection direction;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** Pul qaysi usulda harakatlangani (jurnalda 5010 yoki 5110 ni ajratish uchun). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PayMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashSource sourceType;

    private Long sourceId;

    @Column(length = 500)
    private String note;
}

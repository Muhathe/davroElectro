package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;
import univer.university.entity.enums.PaymentType;

import java.math.BigDecimal;

/** Chekning to'lov qismi (NAQD/KARTA/CLICK/NASIYA). Eski sotuvlarda qatorlar yo'q. */
@Entity
@Table(name = "sale_payments", indexes = @Index(name = "idx_sale_payment_sale", columnList = "sale_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentType method;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
}

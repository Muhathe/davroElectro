package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;
import univer.university.entity.enums.PayMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_payments", indexes = {
        @Index(name = "idx_cpay_customer", columnList = "customer_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PayMethod method;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Long userId;
}

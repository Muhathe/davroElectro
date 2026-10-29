package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "supplier_debts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierDebt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long purchaseId;

    @Column(nullable = false)
    private Long supplierId;

    /** Dastlabki qarz summasi. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** Qolgan (to'lanmagan) summa. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal remaining;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean closed;
}

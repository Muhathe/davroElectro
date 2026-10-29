package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "journal_lines", indexes = {
        @Index(name = "idx_jline_account", columnList = "account_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id")
    private JournalEntry entry;

    @Column(name = "account_code", nullable = false, length = 10)
    private String accountCode;

    @Builder.Default
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal debit = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;
}

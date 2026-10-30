package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;
import univer.university.entity.enums.PrintJobStatus;
import univer.university.entity.enums.PrintJobType;

import java.time.LocalDateTime;

/** Kassa printeri navbati: print-agent so'raydi, chek matni olish paytida yaratiladi. */
@Entity
@Table(name = "print_jobs", indexes = {
        @Index(name = "idx_print_job_status", columnList = "status, id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrintJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PrintJobType type;

    /** Sotuv yoki vozvrat id si (TEST da bo'sh). */
    private Long refId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PrintJobStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Long userId;

    private LocalDateTime takenAt;

    private LocalDateTime printedAt;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    @Column(length = 500)
    private String error;
}

package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Kassa kompyuteri (print-agent o'rnatilgan joy). Har birining o'z tokeni bor, cheklar shu kassaga yo'naltiriladi. */
@Entity
@Table(name = "print_terminals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrintTerminal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    /** Agent tokenining SHA-256 si (tokenning o'zi saqlanmaydi). */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Long createdBy;

    /** Agent oxirgi marta bog'langan payt (har 5 daqiqada yoziladi, aniq qiymati xotirada). */
    private LocalDateTime lastSeen;

    @Column(length = 100)
    private String hostName;

    @Column(length = 120)
    private String printerName;

    @Column(length = 64)
    private String agentVersion;
}

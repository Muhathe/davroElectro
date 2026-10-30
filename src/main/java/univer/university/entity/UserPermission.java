package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;
import univer.university.entity.enums.Module;

/** Xodimning shaxsiy ruxsati: qator bor bo'lsa shu modul uchun rol ruxsati o'rniga ishlatiladi. */
@Entity
@Table(name = "user_permissions", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "module"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Module module;

    @Column(nullable = false)
    private boolean canView;

    @Column(nullable = false)
    private boolean canCreate;

    @Column(nullable = false)
    private boolean canEdit;

    @Column(nullable = false)
    private boolean canDelete;
}

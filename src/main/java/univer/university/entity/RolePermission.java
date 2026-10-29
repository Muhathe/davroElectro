package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;
import univer.university.entity.enums.Module;
import univer.university.entity.enums.Role;

@Entity
@Table(name = "role_permissions", uniqueConstraints = @UniqueConstraint(columnNames = {"role", "module"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

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

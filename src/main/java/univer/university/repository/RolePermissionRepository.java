package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univer.university.entity.RolePermission;
import univer.university.entity.enums.Module;
import univer.university.entity.enums.Role;

import java.util.Optional;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    Optional<RolePermission> findByRoleAndModule(Role role, Module module);
}

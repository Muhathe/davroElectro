package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univer.university.entity.UserPermission;
import univer.university.entity.enums.Module;

import java.util.List;
import java.util.Optional;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    Optional<UserPermission> findByUserIdAndModule(Long userId, Module module);

    List<UserPermission> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}

package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univer.university.entity.Supplier;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByName(String name);
}

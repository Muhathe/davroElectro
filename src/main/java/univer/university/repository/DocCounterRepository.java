package univer.university.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univer.university.entity.DocCounter;

import java.util.Optional;

public interface DocCounterRepository extends JpaRepository<DocCounter, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DocCounter c where c.name = :name")
    Optional<DocCounter> lockByName(@Param("name") String name);
}

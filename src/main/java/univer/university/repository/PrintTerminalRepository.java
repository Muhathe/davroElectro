package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univer.university.entity.PrintTerminal;

import java.util.List;
import java.util.Optional;

public interface PrintTerminalRepository extends JpaRepository<PrintTerminal, Long> {

    Optional<PrintTerminal> findByTokenHashAndActiveTrue(String tokenHash);

    List<PrintTerminal> findByActiveTrueOrderById();

    long countByActiveTrue();
}

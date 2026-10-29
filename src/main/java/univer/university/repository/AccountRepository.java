package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univer.university.entity.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByCode(String code);

    List<Account> findAllByOrderByCodeAsc();
}

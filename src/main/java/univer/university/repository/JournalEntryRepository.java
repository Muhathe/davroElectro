package univer.university.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univer.university.entity.JournalEntry;

import java.time.LocalDate;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    Page<JournalEntry> findByEntryDateBetweenOrderByEntryDateDescIdDesc(LocalDate fromDate, LocalDate toDate, Pageable pageable);

    @Query(value = "select distinct e from JournalEntry e join e.lines l "
            + "where e.entryDate between :fromDt and :toDt and l.accountCode = :code order by e.entryDate desc, e.id desc",
            countQuery = "select count(distinct e) from JournalEntry e join e.lines l "
                    + "where e.entryDate between :fromDt and :toDt and l.accountCode = :code")
    Page<JournalEntry> findByAccount(@Param("code") String code, @Param("fromDt") LocalDate fromDt,
                                     @Param("toDt") LocalDate toDt, Pageable pageable);
}

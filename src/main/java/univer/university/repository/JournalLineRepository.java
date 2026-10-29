package univer.university.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univer.university.entity.JournalLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    /** Davr bo'yicha hisoblar aylanmasi: [kod, debet, kredit]. */
    @Query("select l.accountCode, coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0) from JournalLine l "
            + "where l.entry.entryDate >= :fromDt and l.entry.entryDate <= :toDt group by l.accountCode")
    List<Object[]> turnover(@Param("fromDt") LocalDate fromDt, @Param("toDt") LocalDate toDt);

    /** Davr boshigacha bo'lgan aylanma (boshlang'ich qoldiq uchun). */
    @Query("select l.accountCode, coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0) from JournalLine l "
            + "where l.entry.entryDate < :beforeDt group by l.accountCode")
    List<Object[]> turnoverBefore(@Param("beforeDt") LocalDate beforeDt);

    /** Egasi olgan pul: 8410 hisobi bo'yicha harajat yozuvlari (debet - kredit). */
    @Query("select coalesce(sum(l.debit), 0) - coalesce(sum(l.credit), 0) from JournalLine l "
            + "where l.accountCode = '8410' and l.entry.sourceType in ('EXPENSE', 'EXPENSE_STORNO') "
            + "and l.entry.entryDate >= :fromDt and l.entry.entryDate <= :toDt")
    BigDecimal ownerDrawNet(@Param("fromDt") LocalDate fromDt, @Param("toDt") LocalDate toDt);
}

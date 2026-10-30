package univer.university.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univer.university.entity.PrintJob;
import univer.university.entity.enums.PrintJobStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface PrintJobRepository extends JpaRepository<PrintJob, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from PrintJob j where j.status = univer.university.entity.enums.PrintJobStatus.NEW order by j.id")
    List<PrintJob> lockNext(Pageable page);

    /** Agent o'chiq paytda yig'ilgan eski cheklar keyin birdaniga chiqib ketmasin. */
    @Modifying
    @Query("update PrintJob j set j.status = univer.university.entity.enums.PrintJobStatus.EXPIRED "
            + "where j.status = univer.university.entity.enums.PrintJobStatus.NEW and j.createdAt < :before")
    int expireOlderThan(@Param("before") LocalDateTime before);

    /** Agent olib, javob bermay qolgan (o'chib qolgan) vazifalar qayta navbatga. */
    @Modifying
    @Query("update PrintJob j set j.status = univer.university.entity.enums.PrintJobStatus.NEW "
            + "where j.status = univer.university.entity.enums.PrintJobStatus.PRINTING and j.takenAt < :before")
    int releaseStuck(@Param("before") LocalDateTime before);

    long countByStatus(PrintJobStatus status);

    List<PrintJob> findTop50ByOrderByIdDesc();
}

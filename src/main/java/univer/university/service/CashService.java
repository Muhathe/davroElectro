package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.DateUtils;
import univer.university.component.MoneyUtils;
import univer.university.dto.response.PosResponses.CashBalance;
import univer.university.entity.CashOperation;
import univer.university.entity.enums.CashDirection;
import univer.university.entity.enums.CashSource;
import univer.university.entity.enums.PayMethod;
import univer.university.exception.BusinessException;
import univer.university.repository.CashOperationRepository;
import univer.university.repository.DocCounterRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** CashOperation yagona manba. Kassaga yozish faqat shu servis orqali. */
@Service
@RequiredArgsConstructor
public class CashService {

    public static final String LOCK_NAME = "CASH_LOCK";

    private final CashOperationRepository repository;
    private final DocCounterRepository counters;

    @Value("${app.cash.allow-negative:false}")
    private boolean allowNegative;

    @Transactional
    public CashOperation record(CashDirection direction, BigDecimal amount, PayMethod method,
                                CashSource source, Long sourceId, String note) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Summa 0 dan katta bo'lishi kerak");
        }
        BigDecimal value = MoneyUtils.round(amount);
        if (direction == CashDirection.OUT) {
            counters.lockByName(LOCK_NAME); // parallel chiqimlarda qoldiq tekshiruvi to'g'ri bo'lishi uchun
            if (!allowNegative && balance(method).compareTo(value) < 0) {
                throw new BusinessException("Kassada mablag' yetarli emas (" + method + " qoldig'i: "
                        + balance(method).toPlainString() + ")");
            }
        }
        return repository.save(CashOperation.builder().createdAt(DateUtils.now()).direction(direction)
                .amount(value).method(method).sourceType(source).sourceId(sourceId).note(note).build());
    }

    @Transactional(readOnly = true)
    public BigDecimal balance(PayMethod method) {
        return MoneyUtils.round(repository.sumByDirectionAndMethod(CashDirection.IN, method)
                .subtract(repository.sumByDirectionAndMethod(CashDirection.OUT, method)));
    }

    /** Qoldiq = SUM(IN) - SUM(OUT). */
    @Transactional(readOnly = true)
    public BigDecimal balance() {
        return MoneyUtils.round(repository.sumByDirection(CashDirection.IN)
                .subtract(repository.sumByDirection(CashDirection.OUT)));
    }

    @Transactional(readOnly = true)
    public CashBalance balances() {
        BigDecimal naqd = balance(PayMethod.NAQD);
        BigDecimal karta = balance(PayMethod.KARTA).add(balance(PayMethod.CLICK));
        return new CashBalance(balance(), naqd, karta);
    }

    /** Davr ichida kassaga tushgan jami pul (barcha to'lov turlari). */
    @Transactional(readOnly = true)
    public BigDecimal periodIn(LocalDateTime from, LocalDateTime to) {
        return MoneyUtils.round(repository.sumByDirectionBetween(CashDirection.IN, from, to));
    }

    /** Davr ichida kassadan chiqgan jami pul. */
    @Transactional(readOnly = true)
    public BigDecimal periodOut(LocalDateTime from, LocalDateTime to) {
        return MoneyUtils.round(repository.sumByDirectionBetween(CashDirection.OUT, from, to));
    }

    /** Smena uchun: berilgan vaqtgacha naqd qoldig'i. */
    @Transactional(readOnly = true)
    public BigDecimal naqdBalanceAt(LocalDateTime dt) {
        BigDecimal in = MoneyUtils.round(repository.sumByDirectionBefore(CashDirection.IN, dt));
        BigDecimal out = MoneyUtils.round(repository.sumByDirectionBefore(CashDirection.OUT, dt));
        return in.subtract(out);
    }

    /** Smena davomidagi naqd kirim va chiqim. */
    @Transactional(readOnly = true)
    public BigDecimal naqdPeriodIn(LocalDateTime from, LocalDateTime to) {
        return MoneyUtils.round(repository.sumByDirectionAndMethodBetween(CashDirection.IN, PayMethod.NAQD, from, to));
    }

    @Transactional(readOnly = true)
    public BigDecimal naqdPeriodOut(LocalDateTime from, LocalDateTime to) {
        return MoneyUtils.round(repository.sumByDirectionAndMethodBetween(CashDirection.OUT, PayMethod.NAQD, from, to));
    }
}

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
}

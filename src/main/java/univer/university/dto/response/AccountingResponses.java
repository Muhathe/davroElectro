package univer.university.dto.response;

import univer.university.entity.enums.AccountType;
import univer.university.entity.enums.CashDirection;
import univer.university.entity.enums.PayMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AccountingResponses {
    private AccountingResponses() {
    }

    public record LineDto(String accountCode, String accountName, BigDecimal debit, BigDecimal credit) {
    }

    public record EntryDto(Long id, LocalDate date, String description, String sourceType, Long sourceId,
                           List<LineDto> lines) {
    }

    public record TrialRow(String code, String name, AccountType type,
                           BigDecimal openingDebit, BigDecimal openingCredit,
                           BigDecimal turnoverDebit, BigDecimal turnoverCredit,
                           BigDecimal closingDebit, BigDecimal closingCredit) {
    }

    public record TrialBalance(LocalDate from, LocalDate to, List<TrialRow> rows,
                               BigDecimal totalTurnoverDebit, BigDecimal totalTurnoverCredit, boolean balanced) {
    }

    public record ProfitLoss(LocalDate from, LocalDate to, BigDecimal revenue, BigDecimal returns,
                             BigDecimal netRevenue, BigDecimal cogs, BigDecimal grossProfit, BigDecimal opex,
                             BigDecimal netProfit, BigDecimal ownerDraw) {
    }

    public record CashBookRow(LocalDateTime time, CashDirection direction, BigDecimal amount, PayMethod method,
                              String source, String note, BigDecimal balanceAfter) {
    }

    public record CashBook(LocalDate from, LocalDate to, BigDecimal opening, BigDecimal totalIn, BigDecimal totalOut,
                           BigDecimal closing, List<CashBookRow> rows) {
    }
}

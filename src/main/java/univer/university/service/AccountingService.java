package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.DateUtils;
import univer.university.component.MoneyUtils;
import univer.university.dto.response.AccountingResponses.*;
import univer.university.entity.Account;
import univer.university.entity.CashOperation;
import univer.university.entity.JournalEntry;
import univer.university.entity.JournalLine;
import univer.university.entity.enums.CashDirection;
import univer.university.exception.BusinessException;
import univer.university.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountingService {

    public static final String KASSA = "5010";
    public static final String KARTA = "5110";
    public static final String SKLAD = "4010";
    public static final String MIJOZLAR = "4110";
    public static final String TAMINOTCHI = "3310";
    public static final String EGASI = "8410";
    public static final String TUSHUM = "6010";
    public static final String VOZVRAT = "6090";
    public static final String TANNARX = "9010";
    public static final String HARAJAT = "9410";

    private final JournalEntryRepository entries;
    private final JournalLineRepository lines;
    private final AccountRepository accounts;
    private final CashOperationRepository cashOps;

    /** Qo'sh yozuv. Debet va kredit teng bo'lishi shart. lines: {kod, debet, kredit}. */
    @Transactional
    public JournalEntry post(LocalDate date, String description, String sourceType, Long sourceId, Object[]... rows) {
        BigDecimal d = BigDecimal.ZERO;
        BigDecimal c = BigDecimal.ZERO;
        JournalEntry e = JournalEntry.builder().entryDate(date).description(description).sourceType(sourceType)
                .sourceId(sourceId).createdAt(DateUtils.now()).build();
        for (Object[] r : rows) {
            BigDecimal debit = MoneyUtils.round(MoneyUtils.bd(r[1]));
            BigDecimal credit = MoneyUtils.round(MoneyUtils.bd(r[2]));
            if (debit.signum() == 0 && credit.signum() == 0) {
                continue;
            }
            d = d.add(debit);
            c = c.add(credit);
            e.addLine(JournalLine.builder().accountCode((String) r[0]).debit(debit).credit(credit).build());
        }
        if (e.getLines().isEmpty()) {
            return null;
        }
        if (d.compareTo(c) != 0) {
            throw new BusinessException("Jurnal yozuvi muvozanatda emas: debet=" + d + ", kredit=" + c, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return entries.save(e);
    }

    public static Object[] dt(String code, BigDecimal amount) {
        return new Object[]{code, amount, BigDecimal.ZERO};
    }

    public static Object[] kt(String code, BigDecimal amount) {
        return new Object[]{code, BigDecimal.ZERO, amount};
    }

    // ---------- hisobotlar ----------

    @Transactional(readOnly = true)
    public List<EntryDto> journal(LocalDate from, LocalDate to, String account, int page, int size) {
        LocalDate f = from == null ? DateUtils.today().withDayOfMonth(1) : from;
        LocalDate t = to == null ? DateUtils.today() : to;
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200)));
        var res = (account == null || account.isBlank()) ? entries.findByEntryDateBetweenOrderByEntryDateDescIdDesc(f, t, pr)
                : entries.findByAccount(account, f, t, pr);
        Map<String, String> names = accountNames();
        return res.stream().map(e -> new EntryDto(e.getId(), e.getEntryDate(), e.getDescription(), e.getSourceType(),
                e.getSourceId(), e.getLines().stream().map(l -> new LineDto(l.getAccountCode(), names.get(l.getAccountCode()),
                l.getDebit(), l.getCredit())).toList())).toList();
    }

    @Transactional(readOnly = true)
    public TrialBalance trialBalance(LocalDate from, LocalDate to) {
        LocalDate f = from == null ? DateUtils.today().withDayOfMonth(1) : from;
        LocalDate t = to == null ? DateUtils.today() : to;
        Map<String, BigDecimal[]> opening = toMap(lines.turnoverBefore(f));
        Map<String, BigDecimal[]> turn = toMap(lines.turnover(f, t));
        List<TrialRow> rows = new ArrayList<>();
        BigDecimal totD = BigDecimal.ZERO;
        BigDecimal totC = BigDecimal.ZERO;
        for (Account a : accounts.findAllByOrderByCodeAsc()) {
            BigDecimal[] o = opening.getOrDefault(a.getCode(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] x = turn.getOrDefault(a.getCode(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal openNet = o[0].subtract(o[1]);
            BigDecimal closeNet = openNet.add(x[0]).subtract(x[1]);
            rows.add(new TrialRow(a.getCode(), a.getName(), a.getType(),
                    MoneyUtils.round(openNet.max(BigDecimal.ZERO)), MoneyUtils.round(openNet.negate().max(BigDecimal.ZERO)),
                    MoneyUtils.round(x[0]), MoneyUtils.round(x[1]),
                    MoneyUtils.round(closeNet.max(BigDecimal.ZERO)), MoneyUtils.round(closeNet.negate().max(BigDecimal.ZERO))));
            totD = totD.add(x[0]);
            totC = totC.add(x[1]);
        }
        BigDecimal cd = rows.stream().map(TrialRow::closingDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cc = rows.stream().map(TrialRow::closingCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean balanced = totD.compareTo(totC) == 0 && cd.compareTo(cc) == 0;
        return new TrialBalance(f, t, rows, MoneyUtils.round(totD), MoneyUtils.round(totC), balanced);
    }

    @Transactional(readOnly = true)
    public ProfitLoss profitLoss(LocalDate from, LocalDate to) {
        LocalDate f = from == null ? DateUtils.today().withDayOfMonth(1) : from;
        LocalDate t = to == null ? DateUtils.today() : to;
        Map<String, BigDecimal[]> turn = toMap(lines.turnover(f, t));
        BigDecimal revenue = net(turn, TUSHUM, false);
        BigDecimal returns = net(turn, VOZVRAT, true);
        BigDecimal cogs = net(turn, TANNARX, true);
        BigDecimal opex = net(turn, HARAJAT, true);
        BigDecimal netRev = revenue.subtract(returns);
        BigDecimal gross = netRev.subtract(cogs);
        BigDecimal draw = MoneyUtils.nz(lines.ownerDrawNet(f, t));
        return new ProfitLoss(f, t, MoneyUtils.round(revenue), MoneyUtils.round(returns), MoneyUtils.round(netRev),
                MoneyUtils.round(cogs), MoneyUtils.round(gross), MoneyUtils.round(opex),
                MoneyUtils.round(gross.subtract(opex)), MoneyUtils.round(draw));
    }

    @Transactional(readOnly = true)
    public CashBook cashBook(LocalDate from, LocalDate to) {
        LocalDate f = from == null ? DateUtils.today().withDayOfMonth(1) : from;
        LocalDate t = to == null ? DateUtils.today() : to;
        LocalDateTime start = DateUtils.startOfDay(f);
        BigDecimal opening = cashOps.sumByDirectionBefore(CashDirection.IN, start).subtract(cashOps.sumByDirectionBefore(CashDirection.OUT, start));
        BigDecimal running = opening;
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        List<CashBookRow> rows = new ArrayList<>();
        for (CashOperation c : cashOps.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                start, DateUtils.startOfDay(t.plusDays(1)))) {
            if (c.getDirection() == CashDirection.IN) {
                running = running.add(c.getAmount());
                in = in.add(c.getAmount());
            } else {
                running = running.subtract(c.getAmount());
                out = out.add(c.getAmount());
            }
            rows.add(new CashBookRow(c.getCreatedAt(), c.getDirection(), c.getAmount(), c.getMethod(),
                    c.getSourceType().name(), c.getNote(), MoneyUtils.round(running)));
        }
        return new CashBook(f, t, MoneyUtils.round(opening), MoneyUtils.round(in), MoneyUtils.round(out), MoneyUtils.round(running), rows);
    }

    // ---------- yordamchi ----------

    private Map<String, String> accountNames() {
        return accounts.findAll().stream().collect(Collectors.toMap(Account::getCode, Account::getName));
    }

    private static Map<String, BigDecimal[]> toMap(List<Object[]> rows) {
        Map<String, BigDecimal[]> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put((String) r[0], new BigDecimal[]{MoneyUtils.bd(r[1]), MoneyUtils.bd(r[2])});
        }
        return m;
    }

    private static BigDecimal net(Map<String, BigDecimal[]> m, String code, boolean debitSide) {
        BigDecimal[] v = m.get(code);
        if (v == null) {
            return BigDecimal.ZERO;
        }
        return debitSide ? v[0].subtract(v[1]) : v[1].subtract(v[0]);
    }
}

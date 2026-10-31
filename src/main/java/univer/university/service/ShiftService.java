package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.CurrentUserProvider;
import univer.university.component.DateUtils;
import univer.university.component.MoneyUtils;
import univer.university.dto.request.PosRequests;
import univer.university.dto.response.PosResponses.ShiftDto;
import univer.university.entity.CashShift;
import univer.university.entity.User;
import univer.university.entity.enums.ShiftStatus;
import univer.university.exception.BusinessException;
import univer.university.exception.NotFoundException;
import univer.university.repository.CashShiftRepository;
import univer.university.repository.SaleRepository;
import univer.university.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShiftService {

    private final CashShiftRepository shifts;
    private final UserRepository users;
    private final SaleRepository sales;
    private final CashService cash;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;

    @Transactional
    public ShiftDto openShift(PosRequests.OpenShift req) {
        Long userId = currentUser.id();
        if (shifts.existsByUserIdAndStatus(userId, ShiftStatus.OPEN)) {
            throw new BusinessException("Sizda allaqachon ochiq smena mavjud. Avval uni yoping.");
        }
        BigDecimal opening = MoneyUtils.round(req.openingCash());
        CashShift shift = shifts.save(CashShift.builder()
                .userId(userId)
                .status(ShiftStatus.OPEN)
                .openedAt(DateUtils.now())
                .openingCash(opening)
                .openNote(req.note())
                .build());
        audit.log("CREATE", "CashShift", shift.getId(), "ochildi, naqd=" + opening);
        return toDto(shift);
    }

    @Transactional
    public ShiftDto closeShift(PosRequests.CloseShift req) {
        Long userId = currentUser.id();
        CashShift shift = shifts.findByUserIdAndStatus(userId, ShiftStatus.OPEN)
                .orElseThrow(() -> new NotFoundException("Ochiq smena topilmadi"));

        LocalDateTime closedAt = DateUtils.now();
        BigDecimal closing = MoneyUtils.round(req.closingCash());

        // Kutilgan naqd = smena boshidagi naqd qoldig' + davomidagi kirim - davomidagi chiqim
        BigDecimal naqdIn = cash.naqdPeriodIn(shift.getOpenedAt(), closedAt);
        BigDecimal naqdOut = cash.naqdPeriodOut(shift.getOpenedAt(), closedAt);
        BigDecimal expected = MoneyUtils.round(shift.getOpeningCash().add(naqdIn).subtract(naqdOut));
        BigDecimal diff = MoneyUtils.round(closing.subtract(expected));

        shift.setClosedAt(closedAt);
        shift.setClosingCash(closing);
        shift.setExpectedCash(expected);
        shift.setDifference(diff);
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setCloseNote(req.note());
        shifts.save(shift);

        audit.log("UPDATE", "CashShift", shift.getId(),
                "yopildi, kutilgan=" + expected + ", topilgan=" + closing + ", farq=" + diff);
        return toDto(shift);
    }

    @Transactional(readOnly = true)
    public ShiftDto currentShift() {
        Long userId = currentUser.id();
        CashShift shift = shifts.findByUserIdAndStatus(userId, ShiftStatus.OPEN)
                .orElseThrow(() -> new NotFoundException("Ochiq smena yo'q"));
        return toDto(shift);
    }

    @Transactional(readOnly = true)
    public ShiftDto get(Long id) {
        CashShift shift = shifts.findById(id).orElseThrow(() -> new NotFoundException("Smena topilmadi"));
        return toDto(shift);
    }

    @Transactional(readOnly = true)
    public List<ShiftDto> history(LocalDate from, LocalDate to) {
        LocalDate f = from == null ? DateUtils.today().withDayOfMonth(1) : from;
        LocalDate t = to == null ? DateUtils.today() : to;
        List<CashShift> list = shifts.findByPeriod(DateUtils.startOfDay(f), DateUtils.startOfDay(t.plusDays(1)));
        Map<Long, String> names = userNames(list);
        return list.stream().map(s -> toDtoWithNames(s, names)).toList();
    }

    private Map<Long, String> userNames(List<CashShift> list) {
        List<Long> ids = list.stream().map(CashShift::getUserId).distinct().toList();
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private ShiftDto toDto(CashShift s) {
        String name = users.findById(s.getUserId()).map(User::getFullName).orElse("?");
        return buildDto(s, name);
    }

    private ShiftDto toDtoWithNames(CashShift s, Map<Long, String> names) {
        return buildDto(s, names.getOrDefault(s.getUserId(), "?"));
    }

    private ShiftDto buildDto(CashShift s, String cashierName) {
        LocalDateTime end = s.getClosedAt() != null ? s.getClosedAt() : DateUtils.now();
        long minutes = ChronoUnit.MINUTES.between(s.getOpenedAt(), end);

        // Smena davomidagi sotuvlar (sana bo'yicha)
        LocalDateTime shiftTo = s.getClosedAt() != null ? s.getClosedAt() : DateUtils.now();
        long salesCount = sales.countInRange(s.getOpenedAt(), shiftTo);
        BigDecimal salesTotal = MoneyUtils.round(sales.sumTotal(s.getOpenedAt(), shiftTo));

        BigDecimal naqdIn = cash.naqdPeriodIn(s.getOpenedAt(), shiftTo);
        BigDecimal naqdOut = cash.naqdPeriodOut(s.getOpenedAt(), shiftTo);

        return new ShiftDto(
                s.getId(), s.getUserId(), cashierName, s.getStatus(),
                s.getOpenedAt(), s.getClosedAt(), minutes,
                s.getOpeningCash(), s.getClosingCash(),
                s.getExpectedCash(), s.getDifference(),
                salesCount, salesTotal,
                naqdIn, naqdOut,
                s.getOpenNote(), s.getCloseNote()
        );
    }
}

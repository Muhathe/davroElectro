package univer.university.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.component.CurrentUserProvider;
import univer.university.dto.response.StaffResponses.MyStat;
import univer.university.dto.response.StaffResponses.StaffStat;
import univer.university.service.StaffService;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService service;
    private final CurrentUserProvider currentUser;

    /** Menejer va buxgalter uchun: barcha xodimlar statistikasi (pul ko'rsatkichlari bilan). */
    @GetMapping("/stats")
    @PreAuthorize("@perm.view('XODIMLAR') or @perm.view('MOLIYA')")
    public List<StaffStat> stats(@RequestParam(required = false) String month) {
        return service.stats(month);
    }

    /** Sotuvchi/kassir uchun: faqat o'z statistikasi, pul va marja ko'rsatkichlarsiz. */
    @GetMapping("/my-stats")
    @PreAuthorize("isAuthenticated()")
    public MyStat myStats(@RequestParam(required = false) String month) {
        return service.myStats(currentUser.id(), month);
    }
}

package univer.university.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.response.StaffResponses.StaffStat;
import univer.university.service.StaffService;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService service;

    @GetMapping("/stats")
    @PreAuthorize("@perm.view('XODIMLAR') or @perm.view('MOLIYA')")
    public List<StaffStat> stats(@RequestParam(required = false) String month) {
        return service.stats(month);
    }
}

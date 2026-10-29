package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.FinanceRequests;
import univer.university.dto.response.FinanceResponses.*;
import univer.university.service.FinanceService;
import univer.university.service.ForecastService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService service;
    private final ForecastService forecast;

    @GetMapping("/summary")
    @PreAuthorize("@perm.view('MOLIYA')")
    public Summary summary(@RequestParam(required = false) String month) {
        return service.summary(month);
    }

    @GetMapping("/plan")
    @PreAuthorize("@perm.view('MOLIYA')")
    public PlanDto plan(@RequestParam(required = false) String month) {
        return service.plan(month);
    }

    @PutMapping("/plan")
    @PreAuthorize("@perm.edit('MOLIYA')")
    public PlanDto savePlan(@Valid @RequestBody FinanceRequests.Plan req) {
        return service.savePlan(req);
    }

    @GetMapping("/plan-fact")
    @PreAuthorize("@perm.view('MOLIYA')")
    public PlanFact planFact(@RequestParam(required = false) String month) {
        return service.planFact(month);
    }

    @GetMapping("/forecast")
    @PreAuthorize("@perm.view('MOLIYA')")
    public Forecast forecast() {
        return forecast.forecast();
    }

    @GetMapping("/daily")
    @PreAuthorize("@perm.view('MOLIYA')")
    public List<DailyPoint> daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.daily(from, to);
    }

    @GetMapping("/recurring")
    @PreAuthorize("@perm.view('MOLIYA')")
    public List<RecurringDto> recurring() {
        return service.listRecurring();
    }

    @PostMapping("/recurring")
    @PreAuthorize("@perm.create('MOLIYA')")
    public RecurringDto createRecurring(@Valid @RequestBody FinanceRequests.Recurring req) {
        return service.saveRecurring(null, req);
    }

    @PutMapping("/recurring/{id}")
    @PreAuthorize("@perm.edit('MOLIYA')")
    public RecurringDto updateRecurring(@PathVariable Long id, @Valid @RequestBody FinanceRequests.Recurring req) {
        return service.saveRecurring(id, req);
    }
}

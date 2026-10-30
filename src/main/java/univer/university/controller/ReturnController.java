package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.ReturnRequests;
import univer.university.dto.response.ReturnResponses.ReturnDto;
import univer.university.dto.response.ReturnResponses.ReturnSearch;
import univer.university.dto.response.ReturnResponses.SaleForReturn;
import univer.university.dto.response.ReturnResponses.SaleSearch;
import univer.university.dto.response.ReturnResponses.SellerOption;
import univer.university.entity.enums.PrintJobType;
import univer.university.fiscal.FiscalReceipt;
import univer.university.service.FiscalService;
import univer.university.service.PrintService;
import univer.university.service.ReturnService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService service;
    private final FiscalService fiscal;
    private final PrintService print;

    @GetMapping("/sales/{checkNo}")
    @PreAuthorize("@perm.view('VOZVRAT')")
    public SaleForReturn findSale(@PathVariable String checkNo) {
        return service.findSale(checkNo);
    }

    @GetMapping("/sales/search")
    @PreAuthorize("@perm.view('VOZVRAT')")
    public SaleSearch searchSales(@RequestParam(required = false) String checkNo,
                                  @RequestParam(required = false) Long sellerId,
                                  @RequestParam(required = false) Long productId,
                                  @RequestParam(required = false) String product,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "50") int size) {
        return service.searchSales(checkNo, sellerId, productId, product, from, to, page, size);
    }

    @GetMapping("/search")
    @PreAuthorize("@perm.view('VOZVRAT')")
    public ReturnSearch search(@RequestParam(required = false) String checkNo,
                               @RequestParam(required = false) Long sellerId,
                               @RequestParam(required = false) Long productId,
                               @RequestParam(required = false) String product,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size) {
        return service.searchReturns(checkNo, sellerId, productId, product, from, to, page, size);
    }

    @GetMapping("/sellers")
    @PreAuthorize("@perm.view('VOZVRAT')")
    public List<SellerOption> sellers() {
        return service.sellers();
    }

    @GetMapping
    @PreAuthorize("@perm.view('VOZVRAT')")
    public List<ReturnDto> recent() {
        return service.recent();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.view('VOZVRAT')")
    public ReturnDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('VOZVRAT')")
    public ReturnDto create(@Valid @RequestBody ReturnRequests.Create req,
                            @RequestHeader(value = PrintController.KASSA, required = false) String kassa) {
        ReturnDto ret = service.create(req);
        // fiskal qaytarish cheki va chop etish commit dan keyin
        if (fiscal.enabled()) {
            fiscal.sendAfterCommit(FiscalReceipt.Type.RETURN, ret.id());
            ret = service.get(ret.id());
        }
        print.autoEnqueue(PrintJobType.RETURN, ret.id(), kassa);
        return ret;
    }

    @PostMapping("/{id}/fiscalize")
    @PreAuthorize("@perm.edit('FISKAL')")
    public ReturnDto fiscalize(@PathVariable Long id) {
        fiscal.resend(FiscalReceipt.Type.RETURN, id);
        return service.get(id);
    }
}

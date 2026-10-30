package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import univer.university.dto.request.StockRequests;
import univer.university.dto.response.ProductResponses.*;
import univer.university.entity.enums.PayMethod;
import univer.university.entity.enums.StockStatus;
import univer.university.service.StockImportService;
import univer.university.service.StockService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final StockService service;
    private final StockImportService importService;

    @GetMapping
    @PreAuthorize("@perm.view('SKLAD')")
    public StockList list(@RequestParam(required = false) String q,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) StockStatus status) {
        return service.list(q, category, status);
    }

    @PostMapping("/incoming")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('SKLAD')")
    public IncomingResult incoming(@Valid @RequestBody StockRequests.Incoming req) {
        return service.incoming(req);
    }

    @PostMapping("/adjust")
    @PreAuthorize("@perm.edit('SKLAD')")
    public ProductDto adjust(@Valid @RequestBody StockRequests.Adjust req) {
        return service.adjust(req);
    }

    @GetMapping("/movements")
    @PreAuthorize("@perm.view('SKLAD')")
    public List<MovementDto> movements(@RequestParam(required = false) Long productId,
                                       @RequestParam(defaultValue = "100") int limit) {
        return service.movements(productId, limit);
    }

    @GetMapping("/purchases")
    @PreAuthorize("@perm.view('SKLAD')")
    public List<PurchaseDto> purchases(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        return service.purchases(page, size);
    }

    @GetMapping("/purchases/{id}")
    @PreAuthorize("@perm.view('SKLAD')")
    public PurchaseDto purchase(@PathVariable Long id) {
        return service.purchase(id);
    }

    @GetMapping("/order-suggestions")
    @PreAuthorize("@perm.view('SKLAD')")
    public List<OrderSuggestion> suggestions() {
        return service.orderSuggestions();
    }

    @GetMapping("/import/template")
    @PreAuthorize("@perm.create('SKLAD')")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sklad-kirim-shablon.xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX)).body(importService.template());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@perm.create('SKLAD')")
    public ImportResult importFile(@RequestPart("file") MultipartFile file,
                                   @RequestParam(required = false) Long supplierId,
                                   @RequestParam(required = false) BigDecimal paid,
                                   @RequestParam(required = false) PayMethod method) {
        return importService.importFile(file, supplierId, paid, method);
    }
}

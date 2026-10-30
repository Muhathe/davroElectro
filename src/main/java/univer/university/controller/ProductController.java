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
import univer.university.dto.request.ProductRequests;
import univer.university.dto.request.SupplierRequests;
import univer.university.dto.response.ProductResponses.ImportResult;
import univer.university.dto.response.ProductResponses.ProductDto;
import univer.university.dto.response.ProductResponses.SupplierDto;
import univer.university.service.ProductImageService;
import univer.university.service.ProductImportService;
import univer.university.service.ProductService;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ProductService service;
    private final ProductImportService importService;
    private final ProductImageService images;

    @GetMapping("/products")
    @PreAuthorize("@perm.view('SKLAD') or @perm.view('KASSA')")
    public List<ProductDto> list(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) String category,
                                 @RequestParam(defaultValue = "false") boolean onlyActive) {
        return service.list(q, category, onlyActive);
    }

    @GetMapping("/products/categories")
    @PreAuthorize("@perm.view('SKLAD') or @perm.view('KASSA')")
    public List<String> categories() {
        return service.categories();
    }

    @GetMapping("/products/barcode/{code}")
    @PreAuthorize("@perm.view('SKLAD') or @perm.view('KASSA')")
    public ProductDto byBarcode(@PathVariable String code) {
        return service.byBarcode(code);
    }

    @GetMapping("/products/{id}")
    @PreAuthorize("@perm.view('SKLAD') or @perm.view('KASSA')")
    public ProductDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('SKLAD')")
    public ProductDto create(@Valid @RequestBody ProductRequests.Save req) {
        return service.create(req);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("@perm.edit('SKLAD')")
    public ProductDto update(@PathVariable Long id, @Valid @RequestBody ProductRequests.Save req) {
        return service.update(id, req);
    }

    /** JPEG/PNG/WEBP, 5 MB gacha; 800 px va 200 px nusxa saqlanadi. */
    @PostMapping(value = "/products/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@perm.edit('SKLAD')")
    public ProductDto uploadImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return images.upload(id, file);
    }

    @DeleteMapping("/products/{id}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.edit('SKLAD')")
    public void deleteImage(@PathVariable Long id) {
        images.delete(id);
    }

    @GetMapping("/products/import/template")
    @PreAuthorize("@perm.create('SKLAD')")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mahsulotlar-shablon.xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX)).body(importService.template());
    }

    @PostMapping(value = "/products/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@perm.create('SKLAD')")
    public ImportResult importFile(@RequestPart("file") MultipartFile file) {
        return importService.importFile(file);
    }

    // ---------- ta'minotchilar ----------

    @GetMapping("/suppliers")
    @PreAuthorize("@perm.view('SKLAD')")
    public List<SupplierDto> suppliers() {
        return service.listSuppliers();
    }

    @GetMapping("/suppliers/{id}")
    @PreAuthorize("@perm.view('SKLAD')")
    public SupplierDto supplier(@PathVariable Long id) {
        return service.getSupplier(id);
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('SKLAD')")
    public SupplierDto createSupplier(@Valid @RequestBody SupplierRequests.Save req) {
        return service.createSupplier(req);
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("@perm.edit('SKLAD')")
    public SupplierDto updateSupplier(@PathVariable Long id, @Valid @RequestBody SupplierRequests.Save req) {
        return service.updateSupplier(id, req);
    }
}

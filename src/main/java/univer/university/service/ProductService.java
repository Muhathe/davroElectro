package univer.university.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.BarcodeValidator;
import univer.university.component.MoneyUtils;
import univer.university.component.QtyUtils;
import univer.university.dto.request.ProductRequests;
import univer.university.dto.request.SupplierRequests;
import univer.university.dto.response.ProductResponses.ProductDto;
import univer.university.dto.response.ProductResponses.SupplierDto;
import univer.university.entity.Product;
import univer.university.entity.Supplier;
import univer.university.exception.BusinessException;
import univer.university.exception.NotFoundException;
import univer.university.repository.ProductRepository;
import univer.university.repository.SupplierRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final BarcodeValidator barcodes;
    private final AuditService audit;

    public static ProductDto toDto(Product p) {
        return new ProductDto(p.getId(), p.getName(), p.getBarcode(), p.getCategory(), p.getUnit(), p.getAvgCost(),
                p.getLastCost(), p.getPrice(), p.getQty(), p.getMinQty(), p.getSupplierId(), p.isActive());
    }

    @Transactional(readOnly = true)
    public List<ProductDto> list(String q, String category, boolean onlyActive) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(root.get("barcode"), like)));
            }
            if (category != null && !category.isBlank()) {
                p.add(cb.equal(root.get("category"), category));
            }
            if (onlyActive) {
                p.add(cb.isTrue(root.get("active")));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return products.findAll(spec, Sort.by("name")).stream().map(ProductService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return products.categories();
    }

    @Transactional(readOnly = true)
    public ProductDto byBarcode(String code) {
        barcodes.requireValid(code);
        return products.findByBarcode(code).map(ProductService::toDto)
                .orElseThrow(() -> new NotFoundException("Barcode bo'yicha tovar topilmadi: " + code));
    }

    @Transactional(readOnly = true)
    public ProductDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public ProductDto create(ProductRequests.Save req) {
        barcodes.requireValid(req.barcode());
        if (products.existsByBarcode(req.barcode())) {
            throw BusinessException.conflict("Bu barcode allaqachon mavjud");
        }
        checkSupplier(req.supplierId());
        Product p = products.save(Product.builder().name(req.name().trim()).barcode(req.barcode())
                .category(blankToNull(req.category())).unit(req.unit()).price(MoneyUtils.round(req.price()))
                .minQty(QtyUtils.scale(req.minQty())).supplierId(req.supplierId()).active(true).build());
        audit.log("CREATE", "Product", p.getId(), p.getName() + " / " + p.getBarcode());
        return toDto(p);
    }

    @Transactional
    public ProductDto update(Long id, ProductRequests.Save req) {
        Product p = find(id);
        barcodes.requireValid(req.barcode());
        if (!req.barcode().equals(p.getBarcode()) && products.existsByBarcode(req.barcode())) {
            throw BusinessException.conflict("Bu barcode allaqachon mavjud");
        }
        checkSupplier(req.supplierId());
        p.setName(req.name().trim());
        p.setBarcode(req.barcode());
        p.setCategory(blankToNull(req.category()));
        p.setUnit(req.unit());
        p.setPrice(MoneyUtils.round(req.price()));
        p.setMinQty(QtyUtils.scale(req.minQty()));
        p.setSupplierId(req.supplierId());
        if (req.active() != null) {
            p.setActive(req.active());
        }
        audit.log("UPDATE", "Product", p.getId(), p.getName() + ", narx=" + p.getPrice());
        return toDto(p);
    }

    // ---------- ta'minotchilar ----------

    @Transactional(readOnly = true)
    public List<SupplierDto> listSuppliers() {
        return suppliers.findAll(Sort.by("name")).stream().map(s -> new SupplierDto(s.getId(), s.getName(), s.getPhone())).toList();
    }

    @Transactional(readOnly = true)
    public SupplierDto getSupplier(Long id) {
        Supplier s = suppliers.findById(id).orElseThrow(() -> new NotFoundException("Ta'minotchi topilmadi"));
        return new SupplierDto(s.getId(), s.getName(), s.getPhone());
    }

    @Transactional
    public SupplierDto createSupplier(SupplierRequests.Save req) {
        if (suppliers.findByName(req.name().trim()).isPresent()) {
            throw BusinessException.conflict("Bunday ta'minotchi mavjud");
        }
        Supplier s = suppliers.save(Supplier.builder().name(req.name().trim()).phone(blankToNull(req.phone())).build());
        audit.log("CREATE", "Supplier", s.getId(), s.getName());
        return new SupplierDto(s.getId(), s.getName(), s.getPhone());
    }

    @Transactional
    public SupplierDto updateSupplier(Long id, SupplierRequests.Save req) {
        Supplier s = suppliers.findById(id).orElseThrow(() -> new NotFoundException("Ta'minotchi topilmadi"));
        s.setName(req.name().trim());
        s.setPhone(blankToNull(req.phone()));
        audit.log("UPDATE", "Supplier", s.getId(), s.getName());
        return new SupplierDto(s.getId(), s.getName(), s.getPhone());
    }

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Tovar topilmadi"));
    }

    private void checkSupplier(Long id) {
        if (id != null && !suppliers.existsById(id)) {
            throw new NotFoundException("Ta'minotchi topilmadi");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static BigDecimal nz(BigDecimal v) {
        return MoneyUtils.nz(v);
    }
}

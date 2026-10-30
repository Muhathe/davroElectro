package univer.university.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import univer.university.component.DateUtils;
import univer.university.component.MoneyUtils;
import univer.university.dto.response.PosResponses.FiscalInfo;
import univer.university.entity.*;
import univer.university.entity.enums.FiscalStatus;
import univer.university.exception.BusinessException;
import univer.university.exception.NotFoundException;
import univer.university.fiscal.FiscalException;
import univer.university.fiscal.FiscalProvider;
import univer.university.fiscal.FiscalReceipt;
import univer.university.fiscal.FiscalResult;
import univer.university.repository.ProductRepository;
import univer.university.repository.SaleRepository;
import univer.university.repository.SaleReturnRepository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fiskal chek: sotuv/vozvrat commit bo'lgandan keyin provayderga yuboriladi. Javob bo'lmasa chek PENDING qoladi
 * va fon navbati qayta yuboradi; max-attempts dan keyin FAILED. Sotuvning o'zi hech qachon bekor qilinmaydi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FiscalService {

    private final SaleRepository sales;
    private final SaleReturnRepository returns;
    private final ProductRepository products;
    private final ObjectProvider<FiscalProvider> provider;
    private final TransactionTemplate tx;

    @Value("${app.fiscal.enabled:false}")
    private boolean enabled;

    @Value("${app.fiscal.max-attempts:20}")
    private int maxAttempts;

    public boolean enabled() {
        return enabled;
    }

    /** Yangi chek uchun boshlang'ich holat; fiskal o'chirilgan bo'lsa null. */
    public FiscalData initialData() {
        return enabled ? FiscalData.pending() : null;
    }

    public static FiscalInfo toInfo(FiscalData f) {
        if (f == null || f.getStatus() == null) {
            return null;
        }
        return new FiscalInfo(f.getStatus(), f.getTerminalId(), f.getReceiptSeq(), f.getFiscalSign(), f.getQrUrl(),
                f.getFiscalizedAt(), f.getError());
    }

    /** Commit dan keyin chaqiriladi; har qanday xato faqat logga yoziladi. */
    public void sendAfterCommit(FiscalReceipt.Type type, Long id) {
        if (!enabled) {
            return;
        }
        try {
            send(type, id);
        } catch (RuntimeException e) {
            log.error("Fiskal yuborishda kutilmagan xato: {} #{}", type, id, e);
        }
    }

    /** PENDING/FAILED chekni qo'lda qayta yuborish (urinishlar hisobi nolga tushadi). */
    public void resend(FiscalReceipt.Type type, Long id) {
        if (!enabled) {
            throw new BusinessException("Fiskal chek o'chirilgan (app.fiscal.enabled=false)");
        }
        tx.executeWithoutResult(s -> {
            FiscalData f = dataOf(type, id);
            if (f.getStatus() == FiscalStatus.SUCCESS) {
                throw BusinessException.conflict("Chek allaqachon fiskal");
            }
            f.setStatus(FiscalStatus.PENDING);
            f.setAttempts(0);
        });
        sendAfterCommit(type, id);
    }

    @Scheduled(fixedDelayString = "${app.fiscal.retry-delay-ms:60000}", initialDelayString = "${app.fiscal.retry-delay-ms:60000}")
    public void retryPending() {
        if (!enabled) {
            return;
        }
        for (Long id : sales.fiscalQueue(FiscalStatus.PENDING, maxAttempts, PageRequest.of(0, 50))) {
            sendAfterCommit(FiscalReceipt.Type.SALE, id);
        }
        for (Long id : returns.fiscalQueue(FiscalStatus.PENDING, maxAttempts, PageRequest.of(0, 50))) {
            sendAfterCommit(FiscalReceipt.Type.RETURN, id);
        }
    }

    /** Ketma-ket: bitta chek parallel (qo'lda + fon) ikki marta yuborilmasin. */
    private synchronized void send(FiscalReceipt.Type type, Long id) {
        FiscalReceipt receipt = tx.execute(s -> buildReceipt(type, id));
        if (receipt == null) {
            return;
        }
        FiscalResult result = null;
        String error;
        boolean retryable;
        try {
            FiscalProvider p = provider.getIfAvailable();
            if (p == null) {
                throw new FiscalException("Fiskal provayder ulanmagan", true);
            }
            result = p.register(receipt);
            error = null;
            retryable = false;
        } catch (FiscalException e) {
            error = e.getMessage();
            retryable = e.isRetryable();
        } catch (RuntimeException e) {
            log.warn("Fiskal provayder xatosi: {} #{}", type, id, e);
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            retryable = true;
        }
        FiscalResult ok = result;
        String err = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        boolean retry = retryable;
        tx.executeWithoutResult(s -> {
            FiscalData f = dataOf(type, id);
            int attempts = (f.getAttempts() == null ? 0 : f.getAttempts()) + 1;
            f.setAttempts(attempts);
            if (ok != null) {
                f.setStatus(FiscalStatus.SUCCESS);
                f.setTerminalId(ok.terminalId());
                f.setReceiptSeq(ok.receiptSeq());
                f.setFiscalSign(ok.fiscalSign());
                f.setQrUrl(ok.qrUrl());
                f.setFiscalizedAt(ok.fiscalizedAt() == null ? DateUtils.now() : ok.fiscalizedAt());
                f.setError(null);
            } else {
                f.setStatus(retry && attempts < maxAttempts ? FiscalStatus.PENDING : FiscalStatus.FAILED);
                f.setError(err);
            }
        });
    }

    /** Muvaffaqiyatli chek uchun null (qayta yuborilmaydi). */
    private FiscalReceipt buildReceipt(FiscalReceipt.Type type, Long id) {
        if (dataOf(type, id).getStatus() == FiscalStatus.SUCCESS) {
            return null;
        }
        if (type == FiscalReceipt.Type.SALE) {
            Sale s = sales.findById(id).orElseThrow();
            Map<Long, Product> prods = productsOf(s.getItems().stream().map(SaleItem::getProductId).toList());
            List<FiscalReceipt.Line> lines = s.getItems().stream()
                    .map(i -> line(prods.get(i.getProductId()), i.getQty(), i.getPrice())).toList();
            return new FiscalReceipt(type, s.getId(), s.getCheckNo(), null, null, s.getCreatedAt(), s.getPaymentType(),
                    s.getTotal(), s.getReceivedAmount(), lines);
        }
        SaleReturn r = returns.findById(id).orElseThrow();
        Sale orig = sales.findById(r.getSaleId()).orElseThrow();
        Map<Long, Product> prods = productsOf(r.getItems().stream().map(SaleReturnItem::getProductId).toList());
        List<FiscalReceipt.Line> lines = r.getItems().stream()
                .map(i -> line(prods.get(i.getProductId()), i.getQty(), i.getPrice())).toList();
        String origSign = orig.getFiscal() == null ? null : orig.getFiscal().getFiscalSign();
        return new FiscalReceipt(type, r.getId(), "VZ-" + r.getId(), orig.getCheckNo(), origSign, r.getCreatedAt(),
                orig.getPaymentType(), r.getTotal(), null, lines);
    }

    private static FiscalReceipt.Line line(Product p, BigDecimal qty, BigDecimal price) {
        return new FiscalReceipt.Line(p.getId(), p.getName(), p.getMxikCode(), p.getPackageCode(), p.getVatPercent(),
                qty, price, MoneyUtils.round(price.multiply(qty)));
    }

    private Map<Long, Product> productsOf(Collection<Long> ids) {
        return products.findAllById(ids).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    /** Tranzaksiya ichida chaqiriladi; o'zgarishlar dirty-checking bilan saqlanadi. */
    private FiscalData dataOf(FiscalReceipt.Type type, Long id) {
        if (type == FiscalReceipt.Type.SALE) {
            Sale s = sales.findById(id).orElseThrow(() -> new NotFoundException("Sotuv topilmadi"));
            if (s.getFiscal() == null) {
                s.setFiscal(FiscalData.pending());
            }
            return s.getFiscal();
        }
        SaleReturn r = returns.findById(id).orElseThrow(() -> new NotFoundException("Vozvrat topilmadi"));
        if (r.getFiscal() == null) {
            r.setFiscal(FiscalData.pending());
        }
        return r.getFiscal();
    }
}

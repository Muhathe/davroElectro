package univer.university.component;

import org.springframework.stereotype.Component;
import univer.university.exception.BusinessException;

/** Barcode formati: faqat raqam, 8 dan 14 gacha (EAN-8, UPC-A, EAN-13, ITF-14). */
@Component
public class BarcodeValidator {

    public boolean isValid(String code) {
        return code != null && code.matches("\\d{8,14}");
    }

    public String requireValid(String code) {
        if (!isValid(code)) {
            throw BusinessException.badRequest("Barcode noto'g'ri: faqat raqam, 8 dan 14 gacha belgi bo'lishi kerak");
        }
        return code;
    }
}

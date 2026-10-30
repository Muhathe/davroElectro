package univer.university.entity.enums;

import univer.university.exception.BusinessException;

/** Kassa operatsiyasi qaysi usulda bo'lgani (nasiya pul harakati emas, shuning uchun bu yerda yo'q). */
public enum PayMethod {
    NAQD, KARTA, CLICK;

    public static PayMethod fromPayment(PaymentType type) {
        return switch (type) {
            case NAQD -> NAQD;
            case KARTA -> KARTA;
            case CLICK -> CLICK;
            case NASIYA, ARALASH -> throw new BusinessException("Bu turdagi chek bitta pul to'lovi emas");
        };
    }
}

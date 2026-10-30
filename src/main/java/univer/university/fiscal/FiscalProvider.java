package univer.university.fiscal;

/**
 * Fiskal modul / provayder integratsiyasi. Provayder tanlangach shu interfeysni amalga oshiruvchi @Component
 * qo'shiladi (kalit va sertifikatlar faqat serverda, muhit o'zgaruvchilarida) va app.fiscal.enabled=true qilinadi.
 */
public interface FiscalProvider {

    /**
     * Chekni ro'yxatdan o'tkazadi. Vaqtinchalik xato (tarmoq, timeout) — FiscalException(retryable=true),
     * qayta yuborib bo'lmaydigan xato (noto'g'ri MXIK va h.k.) — retryable=false.
     */
    FiscalResult register(FiscalReceipt receipt);
}

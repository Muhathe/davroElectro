package univer.university;

import univer.university.fiscal.FiscalException;
import univer.university.fiscal.FiscalProvider;
import univer.university.fiscal.FiscalReceipt;
import univer.university.fiscal.FiscalResult;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/** Test uchun fiskal provayder: online=false bo'lsa "modul javob bermadi" (qayta urinish mumkin). */
public class TestFiscalProvider implements FiscalProvider {

    public static volatile boolean online = true;
    public static volatile FiscalReceipt last;
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Override
    public FiscalResult register(FiscalReceipt receipt) {
        if (!online) {
            throw new FiscalException("Fiskal modul javob bermadi", true);
        }
        last = receipt;
        int n = SEQ.incrementAndGet();
        return new FiscalResult("UZ170700000001", String.valueOf(n), "FS" + (100000 + n),
                "https://ofd.soliq.uz/check?t=UZ170700000001&r=" + n, LocalDateTime.now());
    }
}

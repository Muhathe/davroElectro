package univer.university.component;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import univer.university.dto.response.PosResponses.FiscalInfo;
import univer.university.dto.response.PosResponses.SaleDto;
import univer.university.dto.response.PosResponses.SaleLine;
import univer.university.dto.response.ReturnResponses.ReturnDto;
import univer.university.dto.response.ReturnResponses.ReturnableLine;
import univer.university.dto.response.SettingsResponses.StoreSettingsDto;
import univer.university.entity.enums.FiscalStatus;
import univer.university.entity.enums.PaymentType;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Chek -> ESC/POS baytlari (58 mm = 32 belgi, 80 mm = 48 belgi). */
@Component
public class ReceiptRenderer {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Value("${app.print.width:32}")
    private int width;

    @Value("${app.print.cut:false}")
    private boolean cut;

    public byte[] sale(SaleDto s, StoreSettingsDto store) {
        EscPos p = header(store);
        p.line("Chek: " + s.checkNo());
        p.line("Sana: " + DT.format(s.createdAt()));
        if (s.cashierName() != null) {
            p.line("Kassir: " + s.cashierName());
        }
        if (s.customerName() != null) {
            p.line("Mijoz: " + s.customerName());
        }
        p.separator();
        for (SaleLine l : s.items()) {
            p.line(l.name());
            p.pair("  " + qty(l.qty()) + " x " + money(l.price()), money(l.lineTotal()));
        }
        p.separator();
        if (s.discountAmount() != null && s.discountAmount().signum() > 0) {
            p.pair("Chegirmasiz:", money(s.subtotal()));
            p.pair("Chegirma " + qty(s.discountPercent()) + "%:", "-" + money(s.discountAmount()));
        }
        p.bold(true).tall(true).pair("JAMI:", money(s.total())).tall(false).bold(false);
        p.pair("To'lov turi:", label(s.paymentType()));
        if (s.receivedAmount() != null) {
            p.pair("Berildi:", money(s.receivedAmount()));
            p.pair("Qaytim:", money(s.change()));
        }
        fiscal(p, s.fiscal());
        return footer(p, store);
    }

    public byte[] saleReturn(ReturnDto r, StoreSettingsDto store) {
        EscPos p = header(store);
        p.center().bold(true).line("VOZVRAT").bold(false).left();
        p.line("Vozvrat: #" + r.id());
        p.line("Asl chek: " + r.checkNo());
        p.line("Sana: " + DT.format(r.createdAt()));
        p.separator();
        for (ReturnableLine l : r.items()) {
            p.line(l.name());
            p.pair("  " + qty(l.returnedQty()) + " x " + money(l.price()),
                    money(MoneyUtils.round(l.price().multiply(l.returnedQty()))));
        }
        p.separator();
        p.bold(true).tall(true).pair("QAYTARILDI:", money(r.total())).tall(false).bold(false);
        p.line(r.toDebt() ? "Mijozning nasiya qarzidan ayirildi" : "Pul mijozga qaytarildi");
        fiscal(p, r.fiscal());
        return footer(p, store);
    }

    public byte[] test(StoreSettingsDto store, LocalDateTime now) {
        EscPos p = header(store);
        p.center().bold(true).line("TEST CHEK").bold(false);
        p.line(DT.format(now));
        p.line("Printer serverga ulangan va ishlayapti");
        p.left();
        return footer(p, store);
    }

    private EscPos header(StoreSettingsDto store) {
        EscPos p = new EscPos(width);
        p.center().bold(true).tall(true).line(store.name()).tall(false).bold(false);
        if (store.address() != null) {
            p.line(store.address());
        }
        if (store.phone() != null) {
            p.line("Tel: " + store.phone());
        }
        if (store.inn() != null) {
            p.line("STIR: " + store.inn());
        }
        return p.left().separator();
    }

    private static void fiscal(EscPos p, FiscalInfo f) {
        p.separator().center();
        if (f == null) {
            p.line("Bu fiskal chek emas");
        } else if (f.status() == FiscalStatus.SUCCESS) {
            p.left();
            p.pair("FM:", nz(f.terminalId()));
            p.pair("Fiskal chek:", nz(f.receiptSeq()));
            p.pair("Fiskal belgi:", nz(f.fiscalSign()));
            if (f.qrUrl() != null) {
                p.center().qr(f.qrUrl());
            }
        } else {
            p.line("Fiskal chek kutilmoqda");
        }
    }

    private byte[] footer(EscPos p, StoreSettingsDto store) {
        p.center().line(store.receiptFooter() != null ? store.receiptFooter() : "Xaridingiz uchun rahmat!");
        p.left().feed(4);
        if (cut) {
            p.cut();
        }
        return p.bytes();
    }

    static String money(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        DecimalFormatSymbols sym = new DecimalFormatSymbols();
        sym.setGroupingSeparator(' ');
        sym.setDecimalSeparator('.');
        String pattern = v.stripTrailingZeros().scale() <= 0 ? "#,##0" : "#,##0.00";
        return new DecimalFormat(pattern, sym).format(v);
    }

    static String qty(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private static String label(PaymentType t) {
        return switch (t) {
            case NAQD -> "Naqd";
            case KARTA -> "Karta";
            case CLICK -> "Click";
            case NASIYA -> "Nasiya";
        };
    }

    private static String nz(String s) {
        return s == null ? "-" : s;
    }
}

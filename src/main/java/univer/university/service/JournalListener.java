package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import univer.university.entity.enums.PayMethod;
import univer.university.entity.enums.PaymentType;
import univer.university.event.*;

import java.math.BigDecimal;

import static univer.university.service.AccountingService.*;

/**
 * Hodisalardan avtomatik jurnal yozuvlari. BEFORE_COMMIT: yozuv asosiy tranzaksiya bilan birga saqlanadi,
 * jurnal yozilmasa sotuv/harajat ham commit bo'lmaydi.
 */
@Component
@RequiredArgsConstructor
public class JournalListener {

    private final AccountingService acc;

    static String cashAccount(PayMethod m) {
        return m == PayMethod.NAQD ? KASSA : KARTA;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onSale(SaleCompletedEvent e) {
        String debit = switch (e.paymentType()) {
            case NAQD -> KASSA;
            case KARTA, CLICK -> KARTA;
            case NASIYA -> MIJOZLAR;
        };
        acc.post(e.date(), "Sotuv " + e.checkNo() + (e.paymentType() == PaymentType.NASIYA ? " (nasiya)" : ""),
                "SALE", e.saleId(), dt(debit, e.total()), kt(TUSHUM, e.total()));
        acc.post(e.date(), "Sotilgan tovar tannarxi " + e.checkNo(), "SALE", e.saleId(),
                dt(TANNARX, e.cogs()), kt(SKLAD, e.cogs()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onReturn(ReturnCreatedEvent e) {
        String credit = e.method() == null ? MIJOZLAR : cashAccount(e.method());
        acc.post(e.date(), "Vozvrat #" + e.returnId(), "RETURN", e.returnId(), dt(VOZVRAT, e.total()), kt(credit, e.total()));
        acc.post(e.date(), "Vozvrat tannarxi #" + e.returnId(), "RETURN", e.returnId(), dt(SKLAD, e.cogs()), kt(TANNARX, e.cogs()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onExpense(ExpenseCreatedEvent e) {
        String debit = e.ownerDraw() ? EGASI : HARAJAT;
        acc.post(e.date(), (e.ownerDraw() ? "Egasi pul oldi X-" : "Harajat X-") + e.expenseId(), "EXPENSE", e.expenseId(),
                dt(debit, e.amount()), kt(cashAccount(e.method()), e.amount()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onExpenseCancelled(ExpenseCancelledEvent e) {
        String credit = e.ownerDraw() ? EGASI : HARAJAT;
        acc.post(e.date(), "Storno: X-" + e.expenseId(), "EXPENSE_STORNO", e.expenseId(),
                dt(cashAccount(e.method()), e.amount()), kt(credit, e.amount()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPurchase(PurchaseCreatedEvent e) {
        BigDecimal debt = e.total().subtract(e.paid());
        acc.post(e.date(), "Tovar kirimi #" + e.purchaseId(), "PURCHASE", e.purchaseId(),
                dt(SKLAD, e.total()),
                kt(e.method() == null ? KASSA : cashAccount(e.method()), e.paid()),
                kt(TAMINOTCHI, debt));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCustomerPayment(CustomerPaymentEvent e) {
        acc.post(e.date(), "Mijoz nasiyani to'ladi #" + e.paymentId(), "CUSTOMER_PAYMENT", e.paymentId(),
                dt(cashAccount(e.method()), e.amount()), kt(MIJOZLAR, e.amount()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onSupplierPayment(SupplierPaymentEvent e) {
        acc.post(e.date(), "Ta'minotchiga to'lov, qarz #" + e.debtId(), "SUPPLIER_PAYMENT", e.debtId(),
                dt(TAMINOTCHI, e.amount()), kt(cashAccount(e.method()), e.amount()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAdjust(StockAdjustedEvent e) {
        BigDecimal v = e.valueDelta();
        if (v.signum() < 0) {
            acc.post(e.date(), "Sklad kamomadi, tovar #" + e.productId(), "ADJUSTMENT", e.productId(),
                    dt(HARAJAT, v.negate()), kt(SKLAD, v.negate()));
        } else if (v.signum() > 0) {
            acc.post(e.date(), "Sklad ortiqchasi, tovar #" + e.productId(), "ADJUSTMENT", e.productId(),
                    dt(SKLAD, v), kt(EGASI, v));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOpening(OpeningBalanceEvent e) {
        acc.post(e.date(), "Boshlang'ich kassa", "OPENING", null, dt(KASSA, e.cash()), kt(EGASI, e.cash()));
        acc.post(e.date(), "Boshlang'ich sklad", "OPENING", null, dt(SKLAD, e.stock()), kt(EGASI, e.stock()));
    }
}

package univer.university.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import univer.university.component.DateUtils;
import univer.university.component.MoneyUtils;
import univer.university.entity.*;
import univer.university.entity.enums.*;
import univer.university.entity.enums.Module;
import univer.university.event.OpeningBalanceEvent;
import univer.university.repository.*;
import univer.university.security.PermissionMatrixService;
import univer.university.service.CashService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Boshlang'ich ma'lumotlar: ruxsatlar, xodimlar, kategoriyalar, hisob rejasi, rejadagi to'lovlar, demo tovar/mijoz. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final UserRepository users;
    private final RolePermissionRepository permissions;
    private final ExpenseCategoryRepository categories;
    private final AccountRepository accounts;
    private final RecurringPaymentRepository recurring;
    private final FinancePlanRepository plans;
    private final DocCounterRepository counters;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final StockMovementRepository movements;
    private final CashService cash;
    private final PermissionMatrixService matrix;
    private final ApplicationEventPublisher events;

    @Value("${app.seed.enabled:true}")
    private boolean enabled;
    @Value("${app.seed.demo-data:true}")
    private boolean demo;
    @Value("${app.seed.default-password:ChangeMe123!}")
    private String defaultPassword;
    /** Boshlang'ich kassa qoldig'i. 0 = demo uchun ham naqd pulni statik qo'shmaydi.
     *  Kerak bo'lsa application.properties da app.seed.opening-cash=XXXXX qo'ying. */
    @Value("${app.seed.opening-cash:0}")
    private BigDecimal openingCash;

    @Override
    public void run(ApplicationArguments args) {
        dropEnumChecks();
        if (!enabled) {
            return;
        }
        tx.executeWithoutResult(s -> {
            seedCounters();
            seedPermissions();
            seedUsers();
            seedCategories();
            seedAccounts();
            seedRecurring();
            seedPlan();
            if (demo && products.count() == 0) {
                seedDemo();
            }
        });
        matrix.clearCache();
        log.info("Seed yakunlandi");
    }

    private void seedCounters() {
        for (String n : List.of("CHECK", CashService.LOCK_NAME)) {
            if (!counters.existsById(n)) {
                counters.save(new DocCounter(n, 0));
            }
        }
    }

    /**
     * Mavjud bazada enum ustunlaridagi eski CHECK cheklovlari yangi qiymatlarni (ARALASH, yangi modullar) rad etadi;
     * ddl-auto=update ularni yangilamaydi. Qiymatlarni baribir Java enum nazorat qiladi.
     */
    private void dropEnumChecks() {
        jdbc.execute("ALTER TABLE sales DROP CONSTRAINT IF EXISTS sales_payment_type_check");
        jdbc.execute("ALTER TABLE role_permissions DROP CONSTRAINT IF EXISTS role_permissions_module_check");
        // KASSIR yangi rol qiymati — eski CHECK cheklovini olib tashlash
        jdbc.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
        // cash_shifts smenasi uchun
        jdbc.execute("ALTER TABLE cash_shifts DROP CONSTRAINT IF EXISTS cash_shifts_status_check");
    }

    /**
     * Faqat yetishmagan qatorlar qo'shiladi: ishlab turgan do'kondagi o'zgartirilgan ruxsatlar saqlanadi.
     * Tartib: EGASI, ADMIN, BUXGALTER, KASSIR, SOTUVCHI; har biri: ko'rish, yaratish, tahrirlash, o'chirish.
     * KASSIR: kassada sotuv/vozvrat qiladi, tannarx/moliya ko'rmaydi.
     * SOTUVCHI: KASSIR kabi, lekin mijozlar va smena boshqaruviga qo'shimcha kiradi.
     */
    private void seedPermissions() {
        put(Module.KASSA,        "TTTT", "TTTT", "TFFF", "TTFF", "TTFF");
        put(Module.KASSA_HARAJAT,"TTTT", "TTTT", "TTFF", "TTFF", "FFFF");
        put(Module.VOZVRAT,      "TTTT", "TTTT", "TFFF", "TTFF", "TTFF");
        put(Module.SKLAD,        "TTTT", "TTTT", "TFFF", "FFFF", "TFFF");
        put(Module.MIJOZLAR,     "TTTT", "TTTT", "TFFF", "TFFF", "TTFF");
        put(Module.QARZLAR,      "TTTT", "TTTT", "TTFF", "FFFF", "FFFF");
        put(Module.HARAJATLAR,   "TTTT", "TTTT", "TTTT", "FFFF", "FFFF");
        put(Module.MOLIYA,       "TTTT", "TTTT", "TFTF", "FFFF", "FFFF");
        put(Module.BUXGALTERIYA, "TTTT", "FFFF", "TTFF", "FFFF", "FFFF");
        put(Module.XODIMLAR,     "TTTT", "TTTT", "FFFF", "FFFF", "FFFF");
        put(Module.AI,           "TTTT", "TTTT", "TTFF", "FFFF", "FFFF");
        put(Module.SOZLAMALAR,   "TTTT", "FFFF", "FFFF", "FFFF", "FFFF");
        put(Module.PRINTER,      "TTTT", "TTTT", "FFFF", "TFFF", "FFFF");
        put(Module.FISKAL,       "TTTT", "TTTT", "FFFF", "FFFF", "FFFF");
        put(Module.SMENA,        "TTTT", "TTTT", "TTFF", "TTFF", "TTFF");
    }

    private void put(Module m, String egasi, String admin, String buxgalter, String kassir, String sotuvchi) {
        save(Role.EGASI, m, egasi);
        save(Role.ADMIN, m, admin);
        save(Role.BUXGALTER, m, buxgalter);
        save(Role.KASSIR, m, kassir);
        save(Role.SOTUVCHI, m, sotuvchi);
    }

    private void save(Role r, Module m, String f) {
        if (permissions.findByRoleAndModule(r, m).isPresent()) {
            return;
        }
        permissions.save(RolePermission.builder().role(r).module(m).canView(f.charAt(0) == 'T').canCreate(f.charAt(1) == 'T')
                .canEdit(f.charAt(2) == 'T').canDelete(f.charAt(3) == 'T').build());
    }

    private void seedUsers() {
        if (users.count() > 0) {
            return;
        }
        user("davron", "Davron (egasi)", Role.EGASI);
        user("admin", "Administrator", Role.ADMIN);
        user("buxgalter", "Buxgalter", Role.BUXGALTER);
        user("kassir1", "Kassir (1-kassa)", Role.KASSIR);
        user("sardor", "Sardor (sotuvchi)", Role.SOTUVCHI);
        user("jasur", "Jasur (sotuvchi)", Role.SOTUVCHI);
    }

    private void user(String username, String fullName, Role role) {
        users.save(User.builder().username(username).passwordHash(encoder.encode(defaultPassword)).fullName(fullName)
                .role(role).active(true).createdAt(DateUtils.now()).build());
    }

    private void seedCategories() {
        if (categories.count() > 0) {
            return;
        }
        cat("Ijara", ExpenseType.FIXED);
        cat("Oylik", ExpenseType.FIXED);
        cat("Kommunal", ExpenseType.FIXED);
        cat("Soliq", ExpenseType.FIXED);
        cat("Transport", ExpenseType.VARIABLE);
        cat("Xo'jalik", ExpenseType.VARIABLE);
        cat("Egasi olgan pul", ExpenseType.OWNER_DRAW);
        cat("Boshqa", ExpenseType.VARIABLE);
    }

    private void cat(String name, ExpenseType type) {
        categories.save(ExpenseCategory.builder().name(name).type(type).build());
    }

    private void seedAccounts() {
        if (accounts.count() > 0) {
            return;
        }
        acc("5010", "Kassa (naqd)", AccountType.AKTIV);
        acc("5110", "Karta va Click", AccountType.AKTIV);
        acc("4010", "Sklad (tovar)", AccountType.AKTIV);
        acc("4110", "Mijozlar qarzi (nasiya)", AccountType.AKTIV);
        acc("3310", "Ta'minotchilarga qarz", AccountType.PASSIV);
        acc("8410", "Egasi kapitali / egasi olgan pul", AccountType.KAPITAL);
        acc("6010", "Savdo tushumi", AccountType.DAROMAD);
        acc("6090", "Vozvrat", AccountType.DAROMAD);
        acc("9010", "Sotilgan tovar tannarxi", AccountType.XARAJAT);
        acc("9410", "Operatsion harajatlar", AccountType.XARAJAT);
    }

    private void acc(String code, String name, AccountType type) {
        accounts.save(Account.builder().code(code).name(name).type(type).build());
    }

    private void seedRecurring() {
        if (recurring.count() > 0) {
            return;
        }
        rec("Ijara", "Ijara", 5, "4000000", null, false);
        rec("Oylik", "Oylik", 10, "8000000", null, false);
        rec("Kommunal", "Kommunal", 15, "1200000", null, false);
        rec("Soliq", "Soliq", 20, null, "4", false);
        rec("Egasi olgan pul", "Egasi olgan pul", 25, "3000000", null, true);
    }

    private void rec(String label, String category, int day, String amount, String percent, boolean draw) {
        recurring.save(RecurringPayment.builder().label(label).category(category).dayOfMonth(day)
                .amount(amount == null ? null : new BigDecimal(amount))
                .percentOfPrevRevenue(percent == null ? null : new BigDecimal(percent)).ownerDraw(draw).active(true).build());
    }

    private void seedPlan() {
        String m = java.time.YearMonth.from(DateUtils.today()).toString();
        if (plans.findByMonth(m).isEmpty()) {
            plans.save(FinancePlan.builder().month(m).revenue(new BigDecimal("120000000")).grossProfit(new BigDecimal("30000000"))
                    .opex(new BigDecimal("14000000")).netProfit(new BigDecimal("16000000")).ownerDraw(new BigDecimal("3000000")).build());
        }
    }

    // ---------- demo ----------

    private void seedDemo() {
        Supplier s1 = suppliers.findByName("Elektro Optom").orElseGet(() -> suppliers.save(Supplier.builder().name("Elektro Optom").phone("+998901112233").build()));
        Supplier s2 = suppliers.findByName("Kabel Savdo").orElseGet(() -> suppliers.save(Supplier.builder().name("Kabel Savdo").phone("+998902223344").build()));
        BigDecimal stockValue = BigDecimal.ZERO;
        Object[][] data = {
                {"LED lampa 12W", "4780000000011", "Lampalar", Unit.DONA, "8000", "12000", "120", "20", s1},
                {"LED lampa 9W", "4780000000028", "Lampalar", Unit.DONA, "7000", "10000", "150", "30", s1},
                {"Rozetka ikkilik", "4780000000035", "Rozetka", Unit.DONA, "15000", "22000", "60", "10", s1},
                {"Kabel VVG 3x2.5", "4780000000042", "Kabel", Unit.METR, "9000", "13000", "500", "100", s2},
                {"Kabel PVS 2x1.5", "4780000000059", "Kabel", Unit.METR, "5000", "7500", "800", "150", s2},
                {"Avtomat 16A", "4780000000066", "Avtomat", Unit.DONA, "28000", "40000", "40", "8", s1},
                {"Avtomat 25A", "4780000000073", "Avtomat", Unit.DONA, "32000", "45000", "30", "8", s1},
                {"Izolyatsiya lentasi", "4780000000080", "Aksessuar", Unit.DONA, "3000", "5000", "200", "40", s1},
                {"Panel svetilnik 18W", "4780000000097", "Svetilnik", Unit.DONA, "45000", "65000", "25", "5", s1},
                {"Projektor 50W", "4780000000103", "Svetilnik", Unit.DONA, "60000", "85000", "15", "4", s1},
                {"Batareya AA", "4780000000110", "Aksessuar", Unit.DONA, "2500", "4000", "300", "50", s1},
                {"Gofra truba 20mm", "4780000000127", "Kabel", Unit.METR, "1500", "2500", "400", "80", s2}
        };
        for (Object[] d : data) {
            BigDecimal cost = new BigDecimal((String) d[4]);
            BigDecimal qty = new BigDecimal((String) d[6]);
            Product p = products.save(Product.builder().name((String) d[0]).barcode((String) d[1]).category((String) d[2])
                    .unit((Unit) d[3]).avgCost(cost).lastCost(cost).price(new BigDecimal((String) d[5])).qty(qty)
                    .minQty(new BigDecimal((String) d[7])).supplierId(((Supplier) d[8]).getId()).active(true).build());
            movements.save(StockMovement.builder().productId(p.getId()).type(MovementType.OPENING).qty(qty).cost(cost)
                    .createdAt(DateUtils.now()).build());
            stockValue = stockValue.add(qty.multiply(cost));
        }
        customers.save(Customer.builder().name("Usta Akmal").type(CustomerType.USTA).phone("+998933334455")
                .discountPercent(new BigDecimal("5")).active(true).createdAt(DateUtils.now()).build());
        customers.save(Customer.builder().name("Qurilish MChJ").type(CustomerType.KORXONA).phone("+998944445566")
                .discountPercent(new BigDecimal("8")).active(true).createdAt(DateUtils.now()).build());
        customers.save(Customer.builder().name("Doimiy mijoz Bobur").type(CustomerType.DOIMIY).phone("+998955556677")
                .discountPercent(new BigDecimal("3")).active(true).createdAt(DateUtils.now()).build());
        customers.save(Customer.builder().name("Usta Rustam").type(CustomerType.USTA).phone("+998966667788")
                .discountPercent(new BigDecimal("5")).active(true).createdAt(DateUtils.now()).build());

        BigDecimal cashAmount = MoneyUtils.round(openingCash);
        if (cashAmount.signum() > 0) {
            cash.record(CashDirection.IN, cashAmount, PayMethod.NAQD, CashSource.OPENING, null, "Boshlang'ich qoldiq");
        }
        events.publishEvent(new OpeningBalanceEvent(cashAmount, MoneyUtils.round(stockValue), LocalDate.now(DateUtils.ZONE)));
    }
}

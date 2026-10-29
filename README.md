# Davron Elektro — Backend

Java 17 · Spring Boot 3.3.5 · PostgreSQL · JWT · Apache POI · Swagger.
Paket: `univer.university`. Arxitektura: `controller → service → repository`, entity hech qachon tashqariga chiqmaydi (faqat DTO).

## Ishga tushirish

```bash
docker compose up -d            # PostgreSQL (davron_elektro)
export JWT_SECRET='kamida-32-belgili-maxfiy-kalit-kiriting-!!!'
export SEED_PASSWORD='Kuchli-Parol-123'    # demo foydalanuvchilar paroli
mvn spring-boot:run
```

Swagger: http://localhost:8080/swagger-ui.html  (Authorize tugmasiga login qaytargan JWT ni kiriting)

### Muhit o'zgaruvchilari
| O'zgaruvchi | Vazifasi | Default |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL | localhost/davron_elektro/postgres |
| `JWT_SECRET` | JWT kaliti (≥32 belgi) — **productionda albatta o'zgartiring** | demo kalit |
| `CORS_ORIGINS` | Frontend manzillari (vergul bilan) | http://localhost:3000 |
| `SEED_PASSWORD` | Seed foydalanuvchilari paroli | `ChangeMe123!` |
| `AI_API_KEY`, `AI_MODEL` | AI yordamchi (bo'sh bo'lsa AI o'chiq, 503 qaytadi) | — |

`application.properties`: `app.seed.enabled`, `app.seed.demo-data` (productionda `false`), `app.seed.opening-cash`, `app.cash.allow-negative`.

## Seed foydalanuvchilar (parol = `SEED_PASSWORD`)
`davron` (EGASI), `admin` (ADMIN), `buxgalter` (BUXGALTER), `sardor`, `jasur` (SOTUVCHI).
Demo ma'lumot: 12 ta tovar, 4 mijoz, 2 ta'minotchi, boshlang'ich kassa 12 000 000 so'm. **Demo sotuv tarixi yo'q** — shuning uchun sklad holatlarida hamma tovar "OLIK" ko'rinadi, prognoz bo'sh (savdo bo'lgach to'g'rilanadi).

## Endpointlar
TZ dagi 19.1 va 19.2 ro'yxati to'liq. Qo'shimchalar:
`GET /api/debts/customers`, `GET/POST/PUT /api/suppliers`, `POST /api/stock/adjust`, `GET /api/returns`,
`GET /api/finance/plan-fact`, `GET/POST/PUT /api/finance/recurring`, `GET /api/ai/quick-insight`.

## Ruxsatlar
`role_permissions` jadvali + `@PreAuthorize("@perm.view('MODUL')")`. EGASI doim ruxsat etilgan. Matritsa `/api/roles/matrix` orqali o'zgaradi.
Qo'shimcha modul `QARZLAR` (mijoz to'lovi / ta'minotchi qarzi) va `KASSA_HARAJAT` qo'shilgan.

## TZ dan chetga chiqishlar (ongli qarorlar)
1. **Jurnal `BEFORE_COMMIT`da** yoziladi (`@TransactionalEventListener`), `AFTER_COMMIT`da emas — jurnal yozilmasa sotuv/harajat ham saqlanmaydi, oborot qaydnomasi doim muvozanatda.
2. `CashOperation`ga `method` (NAQD/KARTA/CLICK) qo'shildi — jurnalda 5010 va 5110 ni ajratish va usul bo'yicha qoldiq uchun.
3. Qo'shimcha hodisalar: `ReturnCreatedEvent`, `ExpenseCancelledEvent` (storno jurnali), `PurchaseCreatedEvent`, `CustomerPaymentEvent`, `SupplierPaymentEvent`, `StockAdjustedEvent`, `OpeningBalanceEvent`.
4. Vozvratda qo'shimcha yozuv: Dt 4010 / Kt 9010 (tovar sklad qoldig'iga qaytgani uchun tannarx teskari yoziladi).
5. "Egasi olgan pul" kategoriyasidagi harajat Dt **8410** (9410 emas) — P&L ga operatsion harajat sifatida kirmaydi.
6. Boshlang'ich kassa va sklad (seed, Excel mahsulot importi) Dt 5010/4010 — Kt 8410 yozuvi bilan jurnalga tushadi.
7. `RecurringPayment` jadvali qo'shildi (prognoz uchun rejadagi to'lovlar, DB da boshqariladi).
8. Mijoz to'lovi qarzdan oshsa rad etiladi; FIFO jamlanma qarz hisobida amalga oshadi (alohida nasiya chek bo'yicha yopilmaydi).
9. Excel sklad importi: bitta kirim (Purchase) bo'lib saqlanadi; `paid` (default — to'liq), `method` (default NAQD), `supplierId` query parametrlari.
10. Pul usuli bo'yicha kassa tekshiruvi: chiqimda o'sha usul (NAQD yoki KARTA/CLICK) qoldig'i yetishi kerak.

## Eslatma
Bu kod yozilgan muhitda Maven Central'ga kirish yopiq edi, shuning uchun **loyiha u yerda kompilyatsiya qilinmagan va testdan o'tkazilmagan** (faqat sintaksis tekshirilgan). Birinchi `mvn compile` da kichik xatolar chiqishi mumkin.

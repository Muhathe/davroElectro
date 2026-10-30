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
| `FILES_DIR` | Tovar rasmlari papkasi (doimiy bo'lishi kerak) | `./files` |
| `FISCAL_ENABLED` | Fiskal chek (provayder ulangandan keyin `true`) | `false` |
| `PRINT_AGENT_TOKEN` | Kassa print-agenti tokeni (bo'sh bo'lsa agent endpointlari 503) | — |
| `PRINT_AUTO`, `PRINT_WIDTH`, `PRINT_CUT` | Sotuvdan keyin avtomatik chek; qator kengligi (58 mm = 32, 80 mm = 48); qog'oz kesish | `true`, `32`, `false` |

`application.properties`: `app.seed.enabled`, `app.seed.demo-data` (productionda `false`), `app.seed.opening-cash`, `app.cash.allow-negative`.

## Seed foydalanuvchilar (parol = `SEED_PASSWORD`)
`davron` (EGASI), `admin` (ADMIN), `buxgalter` (BUXGALTER), `sardor`, `jasur` (SOTUVCHI).
Demo ma'lumot: 12 ta tovar, 4 mijoz, 2 ta'minotchi, boshlang'ich kassa 12 000 000 so'm. **Demo sotuv tarixi yo'q** — shuning uchun sklad holatlarida hamma tovar "OLIK" ko'rinadi, prognoz bo'sh (savdo bo'lgach to'g'rilanadi).

## Endpointlar
TZ dagi 19.1 va 19.2 ro'yxati to'liq. Qo'shimchalar:
`GET /api/debts/customers`, `GET/POST/PUT /api/suppliers`, `POST /api/stock/adjust`, `GET /api/returns`,
`GET /api/finance/plan-fact`, `GET/POST/PUT /api/finance/recurring`, `GET /api/ai/quick-insight`.

Frontend uchun qo'shimcha (TZ da yo'q, lekin to'liq ishlash uchun kerak):

| Metod | Endpoint | Vazifa | Ruxsat |
|---|---|---|---|
| GET | `/api/auth/me` | Joriy foydalanuvchi va ruxsatlari | har kim |
| PUT | `/api/auth/password` | O'z parolini almashtirish (eski parol bilan) | har kim |
| GET | `/api/users/{id}` | Bitta xodim | XODIMLAR ko'rish |
| GET | `/api/audit-logs?from&to&userId&entity` | Audit jurnali | XODIMLAR ko'rish |
| PUT | `/api/expense-categories/{id}` | Kategoriya tahriri (harajat bor bo'lsa turi o'zgarmaydi) | HARAJATLAR tahrir |
| GET | `/api/expenses/{id}` | Bitta harajat | HARAJATLAR ko'rish |
| GET | `/api/customers/{id}/payments` | Mijoz to'lovlari tarixi | QARZLAR yoki MIJOZLAR ko'rish |
| GET | `/api/pos/sales/history?from&to&userId&customerId&paymentType&page&size` | Sotuvlar tarixi | KASSA ko'rish |
| GET | `/api/pos/sales/{id}`, `/api/pos/sales/check/{checkNo}` | Chek (qayta chop etish) | KASSA ko'rish |
| GET | `/api/returns/{id}` | Bitta vozvrat | VOZVRAT ko'rish |
| GET | `/api/products/categories` | Tovar kategoriyalari (dropdown) | SKLAD yoki KASSA ko'rish |
| GET | `/api/suppliers/{id}` | Bitta ta'minotchi | SKLAD ko'rish |
| GET | `/api/stock/purchases`, `/api/stock/purchases/{id}` | Kirimlar ro'yxati va tafsiloti | SKLAD ko'rish |
| GET | `/api/accounting/accounts` | Hisob rejasi | BUXGALTERIYA ko'rish |
| GET | `/api/accounting/balance-sheet?date` | Balans (TZ 13.3) | BUXGALTERIYA ko'rish |

### Tovar rasmi va chek
| Metod | Endpoint | Vazifa | Ruxsat |
|---|---|---|---|
| POST | `/api/products/{id}/image` (multipart `file`) | JPG/PNG/WEBP, ≤5 MB; 800 px va 200 px JPEG saqlanadi, EXIF olib tashlanadi | SKLAD tahrir |
| DELETE | `/api/products/{id}/image` | Rasmni o'chirish (204) | SKLAD tahrir |
| GET | `/files/products/{id}/{xesh}.jpg`, `..._thumb.jpg` | Rasm, **tokensiz**, `Cache-Control: immutable` | ochiq |
| GET | `/api/settings/store` | Do'kon rekvizitlari (chek uchun) | har kim |
| PUT | `/api/settings/store` | Rekvizitlarni saqlash | faqat EGASI |
| POST | `/api/pos/sales/{id}/fiscalize`, `/api/returns/{id}/fiscalize` | PENDING/FAILED chekni qayta yuborish | EGASI, ADMIN |
| GET | `/api/pos/sales?fiscalStatus=PENDING` | Fiskal navbatdagi cheklar (`/sales/history` da ham filtr bor) | KASSA ko'rish |

`ProductDto`: `imageUrl`, `thumbUrl` (nisbiy yo'l), `mxikCode` (17 raqam), `packageCode`, `vatPercent`; tahrirda bu uchtasi
yuborilmasa (`null`) o'zgarmaydi, bo'sh satr tozalaydi. `StockRow`: `thumbUrl`. Excel shablonida `MXIK`, `qadoq kodi`, `QQS %` ustunlari.
`SaleDto`: `subtotal`, `discountAmount`, `receivedAmount`, `change` (faqat naqd), `fiscal` (`FiscalInfo`, fiskal o'chiq bo'lsa yo'q).
`ReturnDto`: `fiscal`. Noma'lum manzil — 404, noto'g'ri metod — 405, katta fayl — 413.

**Fiskal chek.** Infratuzilma tayyor, provayder ulanmagan. Oqim: sotuv/vozvrat commit bo'ladi → `FiscalProvider.register(...)`.
Muvaffaqiyat — `SUCCESS`, javob yo'q — `PENDING` (fon navbati har daqiqada qayta yuboradi), `app.fiscal.max-attempts` dan keyin
yoki qayta yuborib bo'lmaydigan xatoda — `FAILED`. Sotuv hech qachon bekor qilinmaydi. Ulash uchun `fiscal.FiscalProvider` ni
amalga oshiruvchi `@Component` yozib, `FISCAL_ENABLED=true` qilinadi.

### Chek printeri (print-agent)
Printer kassa kompyuteriga USB orqali ulangan, server unga ulana olmaydi. Shuning uchun kassada `print-agent/` dagi
PowerShell agent ishlaydi: `GET /api/print-agent/jobs/next` (long-poll, `X-Agent-Token`) orqali navbatni so'raydi,
ESC/POS baytlarini Windows printeriga RAW holida yuboradi va `done`/`failed` deb javob beradi. Kiruvchi port yoki router
sozlamasi kerak emas. Chek matnini server yaratadi (`ReceiptRenderer`): do'kon rekvizitlari, tovarlar, chegirma, qaytim, fiskal QR.

| Metod | Endpoint | Vazifa | Ruxsat |
|---|---|---|---|
| — | (avtomatik) | Checkout va vozvratdan keyin chek navbatga qo'yiladi (`PRINT_AUTO`) | — |
| POST | `/api/pos/sales/{id}/print`, `/api/returns/{id}/print` | Qayta chop etish | KASSA / VOZVRAT ko'rish |
| POST | `/api/print/test` | Test chek | EGASI, ADMIN |
| GET | `/api/print/status` | Agent onlaynmi, printer nomi, navbatdagilar soni | har kim |
| GET | `/api/print/jobs` | Oxirgi 50 vazifa va holati | KASSA ko'rish |

Printer xatosida vazifa 3 martagacha qayta beriladi, keyin `FAILED`. Agent o'chiq paytda yig'ilgan cheklar 15 daqiqadan
keyin `EXPIRED` bo'ladi (agent yoqilganda eski cheklar birdaniga chiqib ketmaydi). Frontenddagi brauzer orqali chop etishni
o'chirish kerak, aks holda chek ikki marta chiqadi.

O'rnatish (kassa kompyuterida, Xprinter drayveri o'rnatilgan bo'lishi kerak):
```powershell
powershell -ExecutionPolicy Bypass -File print-agent\install.ps1 -Token <PRINT_AGENT_TOKEN>
```
Agent `%LOCALAPPDATA%\DavronPrintAgent` ga ko'chiriladi va Windows ga kirganda yashirin ishga tushadi (log: `agent.log`).
O'chirish: `print-agent\uninstall.ps1`.

## Ruxsatlar
`role_permissions` jadvali + `@PreAuthorize("@perm.view('MODUL')")`. EGASI doim ruxsat etilgan. Matritsa `/api/roles/matrix` orqali o'zgaradi:
faqat EGASI va ADMIN (TZ 6.3), ADMIN esa o'z rolining qatorlarini o'zgartira olmaydi.
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

## Testlar
`mvn test` — xotiradagi H2 (PostgreSQL rejimi, `src/test/resources/application-test.properties`), PostgreSQL kerak emas.
- `EndpointSmokeTest` — har bir GET endpoint EGASI uchun 200, yo'q obyekt 404, AI kalitsiz 503.
- `SecurityTest` — 401/403, rollar matritsasi, `/me`, parol almashtirish.
- `ReceiptAndImageTest` — rasm yuklash/berish/o'chirish, signatura va hajm tekshiruvi, chek summalari, do'kon rekvizitlari,
  fiskal PENDING → qayta yuborish → SUCCESS (test provayder bilan), 404/405.
- `PrintTest` — avtomatik navbat, agent tokeni, ESC/POS chek matni, qayta chop etish, 3 urinishdan keyin FAILED.
- `BusinessFlowTest` — TZ 23-band: sotuv → sklad/kassa/jurnal, harajat storno → kassa tiklanadi + AuditLog,
  qisman vozvrat chegarasi, nasiya va to'lov, parallel sotuvda qoldiq manfiy bo'lmasligi; har oqimdan keyin balans muvozanati.

## Mavjud bazani yangilash
`purchases.supplier_id` endi ixtiyoriy (to'liq to'langan kirimda ta'minotchi shart emas). `ddl-auto=update` NOT NULL cheklovini olib tashlamaydi, shuning uchun eski bazada bir marta bajaring:
```sql
ALTER TABLE purchases ALTER COLUMN supplier_id DROP NOT NULL;
```

## Server va CI/CD

- Server: `/opt/back` (`app.jar`, `.env`, `deploy.sh`, `logs/app.log`), systemd service `davron-back`, port 8082.
- Sozlamalar `/opt/back/.env` da (DB, JWT, seed paroli, CORS, AI kaliti). O'zgartirgandan keyin: `systemctl restart davron-back`.
- Loglar: `tail -f /opt/back/logs/app.log`.
- `.github/workflows/deploy.yml`: PR va `master` ga push bo'lganda testlar yuradi; `master` da muvaffaqiyatli bo'lsa jar serverga yuklanadi va `deploy.sh` ishga tushiradi. Ilova 3 daqiqada javob bermasa eski jar tiklanadi.
- GitHub secrets: `SSH_HOST`, `SSH_USER` (`root`), `SSH_KEY` (deploy kalitining private qismi; public qismi serverdagi `~/.ssh/authorized_keys` da bo'lishi kerak).

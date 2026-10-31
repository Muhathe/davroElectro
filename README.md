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
| `AI_API_KEY`, `AI_MODEL`, `AI_BASE_URL` | AI yordamchi, OpenAI mos API (kalit bo'sh bo'lsa AI o'chiq, 503 qaytadi). Bepul kalit: console.groq.com/keys | — , `openai/gpt-oss-120b`, Groq |
| `FILES_DIR` | Tovar rasmlari papkasi (doimiy bo'lishi kerak) | `./files` |
| `FISCAL_ENABLED` | Fiskal chek (provayder ulangandan keyin `true`) | `false` |
| `PRINT_AGENT_TOKEN` | Eski umumiy agent tokeni (ixtiyoriy; faqat kassasi ko'rsatilmagan cheklarni oladi) | — |
| `PRINT_PUBLIC_URL` | O'rnatish faylidagi server manzili (bo'sh bo'lsa so'rov kelgan manzil) | — |
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
| GET | `/api/returns/sales/search?checkNo&sellerId&productId&product&from&to&page&size` | Vozvrat uchun chek qidirish (chek qismi, sotuvchi, tovar nomi/barcode) | VOZVRAT ko'rish |
| GET | `/api/returns/search?checkNo&sellerId&productId&product&from&to&page&size` | Vozvratlar tarixi (`sellerId` — vozvratni qilgan xodim) | VOZVRAT ko'rish |
| GET | `/api/returns/sellers` | Qidiruv filtri uchun xodimlar ro'yxati | VOZVRAT ko'rish |
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
| PUT | `/api/settings/store` | Rekvizitlarni saqlash | SOZLAMALAR tahrir (standart: faqat EGASI) |
| POST | `/api/pos/sales/{id}/fiscalize`, `/api/returns/{id}/fiscalize` | PENDING/FAILED chekni qayta yuborish | FISKAL tahrir (standart: EGASI, ADMIN) |
| GET | `/api/pos/sales?fiscalStatus=PENDING` | Fiskal navbatdagi cheklar (`/sales/history` da ham filtr bor) | KASSA ko'rish |

`ProductDto`: `imageUrl`, `thumbUrl` (nisbiy yo'l), `mxikCode` (17 raqam), `packageCode`, `vatPercent`; tahrirda bu uchtasi
yuborilmasa (`null`) o'zgarmaydi, bo'sh satr tozalaydi. `StockRow`: `thumbUrl`.

**Excel importi.** Ikkita alohida import bor. `POST /api/products/import` (shablon: `/api/products/import/template`) do'konni ishga tushirishda
tovarlarni to'liq kiritadi: "Yangi tovar" formasidagi hamma maydon (rasmdan tashqari), qoidalari ham bir xil, `miqdor` boshlang'ich qoldiq bo'ladi
(Dt 4010 / Kt 8410). Bor barcode qatori tovarni yangilaydi, qoldiq va tannarx o'zgarmaydi. Shablonning birinchi varag'i toza, izoh va namuna
`Yo'riqnoma` varag'ida. `POST /api/stock/import` esa faqat bor tovarga kirim (`barcode`, `miqdor`, `tannarx`).
`SaleDto`: `subtotal`, `discountAmount`, `receivedAmount`, `change` (faqat naqd), `fiscal` (`FiscalInfo`, fiskal o'chiq bo'lsa yo'q).
`ReturnDto`: `fiscal`. Noma'lum manzil — 404, noto'g'ri metod — 405, katta fayl — 413.

**Yangi tovar.** `POST /api/products` da `initialQty` majburiy (0 bo'lishi mumkin), 0 dan katta bo'lsa `initialCost` ham. Boshlang'ich qoldiq
`OPENING` harakati va Dt 4010 / Kt 8410 yozuvi bilan tushadi. Keyingi tovar faqat kirim orqali; `PUT` bu maydonlarni e'tiborsiz qoldiradi.

**Aralash to'lov.** `POST /api/pos/checkout`: bitta usul uchun `paymentType`, bir nechtasi uchun `payments: [{method, amount}]` (NAQD/KARTA/CLICK).
Ortgan qoldiq avtomatik nasiya bo'ladi (mijoz shart), yig'indi chekdan oshsa rad etiladi, `receivedAmount` naqd qismi uchun. Natija:
`paymentType=ARALASH` (bir nechta qism bo'lsa), `creditAmount`, `payments`. Jurnal: Dt 5010 + Dt 5110 + Dt 4110 / Kt 6010.
Aralash chekda vozvrat summasi faqat NAQD dan chiqadi va mijoz qarzini kamaytirmaydi.

**AI.** Model do'kon ma'lumotini faqat o'qiydigan vositalar orqali oladi, vositalar so'rovchi xodim ruxsatiga qarab beriladi.
`POST /api/ai/chat` `{message, history?: [{role, content}]}` (`history` ≤10 xabar). Xatolar: 503 — kalit yo'q yoki noto'g'ri, 429 — bepul limit tugadi, 502 — xizmat javob bermadi.

**Fiskal chek.** Infratuzilma tayyor, provayder ulanmagan. Oqim: sotuv/vozvrat commit bo'ladi → `FiscalProvider.register(...)`.
Muvaffaqiyat — `SUCCESS`, javob yo'q — `PENDING` (fon navbati har daqiqada qayta yuboradi), `app.fiscal.max-attempts` dan keyin
yoki qayta yuborib bo'lmaydigan xatoda — `FAILED`. Sotuv hech qachon bekor qilinmaydi. Ulash uchun `fiscal.FiscalProvider` ni
amalga oshiruvchi `@Component` yozib, `FISCAL_ENABLED=true` qilinadi.

### Chek printeri (print-agent)
Printer kassa kompyuteriga USB orqali ulangan, server unga ulana olmaydi. Shuning uchun har bir kassa kompyuterida kichik
PowerShell agent ishlaydi: `GET /api/print-agent/jobs/next` (long-poll, `X-Agent-Token`) orqali navbatni so'raydi va
ESC/POS baytlarini printerga yuboradi. Kiruvchi port yoki router sozlamasi kerak emas.

- **Drayversiz**: agent chekni to'g'ridan-to'g'ri USB ga yozadi (Windows ning o'z `usbprint` drayveri,
  `GUID_DEVINTERFACE_USBPRINT`), shuning uchun Xprinter/POS drayveri o'rnatish shart emas. Printer USB ga ulanganda o'zi
  topiladi; ofis printerlari (1284 ID da POS belgisi yo'q) chetlab o'tiladi. USB da topilmasa, drayveri o'rnatilgan chek
  printeri navbatiga (RAW) yuboradi.
- **Kassalar**: har bir kompyuter alohida kassa (`PrintTerminal`, o'z tokeni). Brauzer `X-Kassa-Id` sarlavhasini yuborsa,
  chek aynan shu kassa printeridan chiqadi; yubormasa istalgan kassa oladi.
- **O'rnatish saytdan**: `POST /api/print/terminals` javobidagi `script` ni frontend `fileName` (DavronPrinter-kassaN.cmd)
  nomi bilan yuklab beradi va `terminal.id` ni shu brauzerda eslab qoladi. Kassa kompyuterida fayl ikki marta bosiladi:
  admin huquqi kerak emas, agent `%LOCALAPPDATA%\DavronPrintAgent` ga o'rnatiladi, Windows ga kirganda yashirin ishga
  tushadi va test chek chiqaradi. O'chirish: shu papkadagi `uninstall.cmd`.
- **Yangilanish**: agent soatda bir `/api/print-agent/version` (skript SHA-256) ni solishtiradi, farq qilsa yangi
  skriptni yuklab, o'zini qayta ishga tushiradi. Agent manbasi: `src/main/resources/print-agent/`.

| Metod | Endpoint | Vazifa | Ruxsat |
|---|---|---|---|
| — | (avtomatik) | Checkout va vozvratdan keyin chek `X-Kassa-Id` dagi kassaga navbatga qo'yiladi (`PRINT_AUTO`) | — |
| POST | `/api/pos/sales/{id}/print`, `/api/returns/{id}/print` | Qayta chop etish (`X-Kassa-Id` bilan) | KASSA / VOZVRAT ko'rish |
| POST | `/api/print/test?terminalId=` | Test chek | PRINTER yaratish |
| GET | `/api/print/status` | Agent onlaynmi, printer ulanganmi (`X-Kassa-Id` bo'yicha) | har kim |
| GET | `/api/print/jobs` | Oxirgi 50 vazifa va holati | KASSA ko'rish |
| GET | `/api/print/terminals` | Kassalar: onlayn, printer, kompyuter nomi | KASSA ko'rish |
| POST | `/api/print/terminals` `{name?}` | Yangi kassa + o'rnatish fayli (`terminal`, `fileName`, `script`) | PRINTER yaratish |
| POST | `/api/print/terminals/{id}/installer` | Yangi o'rnatish fayli (eski token bekor) | PRINTER tahrir |
| PUT / DELETE | `/api/print/terminals/{id}` | Nomini o'zgartirish / o'chirish | PRINTER tahrir / o'chirish |

Printer xatosida vazifa 3 martagacha qayta beriladi, keyin `FAILED`. Agent o'chiq paytda yig'ilgan cheklar 15 daqiqadan
keyin `EXPIRED` bo'ladi (agent yoqilganda eski cheklar birdaniga chiqib ketmaydi). Frontenddagi brauzer orqali chop etishni
o'chirish kerak, aks holda chek ikki marta chiqadi.

## Ruxsatlar
`role_permissions` jadvali + `@PreAuthorize("@perm.view('MODUL')")`. EGASI doim ruxsat etilgan. Matritsa `/api/roles/matrix` orqali o'zgaradi:
faqat EGASI va ADMIN (TZ 6.3), ADMIN esa o'z rolining qatorlarini o'zgartira olmaydi.
Qo'shimcha modul `QARZLAR` (mijoz to'lovi / ta'minotchi qarzi) va `KASSA_HARAJAT` qo'shilgan; `SOZLAMALAR`, `PRINTER`, `FISKAL` modullari
avvalgi rolga qattiq bog'langan ruxsatlar o'rnini bosadi (standart qiymatlari o'sha rollar bilan bir xil).

**Xodim ruxsati.** Har bir xodimga modul bo'yicha shaxsiy ruxsat berish mumkin (`user_permissions`): qator bor modulda u, qolganida rol ruxsati ishlaydi.

| Metod | Endpoint | Vazifa | Ruxsat |
|---|---|---|---|
| GET | `/api/roles/capabilities` | Modullar va ulardagi haqiqiy amallar (ekran shundan quriladi) | XODIMLAR ko'rish |
| GET | `/api/roles/users/{id}/permissions` | Xodimning amaldagi ruxsatlari (`custom` — shaxsiy) | XODIMLAR ko'rish |
| PUT | `/api/roles/users/{id}/permissions` | Yuborilgan modullarga shaxsiy ruxsat | XODIMLAR tahrir, faqat EGASI/ADMIN |
| DELETE | `/api/roles/users/{id}/permissions` | Rol ruxsatiga qaytarish | XODIMLAR tahrir, faqat EGASI/ADMIN |

ADMIN o'zining va boshqa ADMIN ruxsatini o'zgartira olmaydi. Seed faqat yetishmagan qatorlarni qo'shadi, o'zgartirilgan ruxsatlar saqlanadi.

**Yangilanish.** Ishga tushishda `DataSeeder` `sales_payment_type_check` va `role_permissions_module_check` cheklovlarini olib tashlaydi:
`ddl-auto=update` ularni yangilamaydi, aks holda `ARALASH` va yangi modullar saqlanmaydi. Qo'lda SQL kerak emas.

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
11. Yangi tovarning boshlang'ich miqdori Dt 4010 — Kt 8410 yozuvi bilan tushadi (Purchase emas).
12. Aralash chekda vozvrat butun summasi bilan NAQD dan chiqadi, chekdagi nasiya qismi mijoz qarzida qoladi.

## Testlar
`mvn test` — xotiradagi H2 (PostgreSQL rejimi, `src/test/resources/application-test.properties`), PostgreSQL kerak emas.
- `EndpointSmokeTest` — har bir GET endpoint EGASI uchun 200, yo'q obyekt 404, AI kalitsiz 503.
- `SecurityTest` — 401/403, rollar matritsasi, `/me`, parol almashtirish.
- `StoreChangesTest` — aralash to'lov va vozvrat, boshlang'ich miqdor, vozvrat qidiruvi, xodim ruxsatlari, Excel import.
- `AiTest` — vositalar sikli, ruxsatlar, xatolar (soxta server bilan, tashqi so'rovsiz).
- `ReceiptAndImageTest` — rasm yuklash/berish/o'chirish, signatura va hajm tekshiruvi, chek summalari, do'kon rekvizitlari,
  fiskal PENDING → qayta yuborish → SUCCESS (test provayder bilan), 404/405.
- `PrintTest` — avtomatik navbat, agent tokeni, ESC/POS chek matni, qayta chop etish, 3 urinishdan keyin FAILED,
  kassa o'rnatish fayli va agent versiyasi, chekning `X-Kassa-Id` dagi kassaga yo'naltirilishi.
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

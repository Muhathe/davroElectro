package univer.university.security;

import univer.university.dto.response.RoleResponses.Capability;
import univer.university.entity.enums.Module;

import java.util.List;

/** Har bir modulda haqiqatan ishlaydigan amallar (controller @PreAuthorize lari bilan bir xil). */
public final class PermissionCatalog {

    public static final List<Capability> ALL = List.of(
            new Capability(Module.KASSA, "Kassa", "Sotuvlar va kassa qoldig'ini ko'rish, chekni qayta chop etish", "Sotish", null, null),
            new Capability(Module.KASSA_HARAJAT, "Kassadan harajat", "Harajat kategoriyalarini ko'rish", "Kassadan harajat kiritish", null, null),
            new Capability(Module.VOZVRAT, "Vozvrat", "Cheklarni qidirish, vozvratlarni ko'rish", "Vozvrat qilish", null, null),
            new Capability(Module.SKLAD, "Sklad", "Tovarlar, qoldiq va kirimlarni ko'rish",
                    "Yangi tovar, ta'minotchi, kirim, Excel import", "Tovarni tahrirlash, qoldiqni tuzatish", null),
            new Capability(Module.MIJOZLAR, "Mijozlar", "Mijozlarni ko'rish", "Mijoz qo'shish", "Mijozni tahrirlash", null),
            new Capability(Module.QARZLAR, "Qarzlar", "Qarzlarni ko'rish", "Mijozdan qarz to'lovini qabul qilish",
                    "Ta'minotchiga qarz to'lash", null),
            new Capability(Module.HARAJATLAR, "Harajatlar", "Harajatlarni ko'rish", "Harajat va kategoriya kiritish",
                    "Harajatni bekor qilish, kategoriyani tahrirlash", null),
            new Capability(Module.MOLIYA, "Moliya", "Hisobotlar va prognozni ko'rish", "Rejali to'lov qo'shish",
                    "Moliya reja va rejali to'lovni tahrirlash", null),
            new Capability(Module.BUXGALTERIYA, "Buxgalteriya", "Jurnal, balans va hisobotlarni ko'rish", null, null, null),
            new Capability(Module.XODIMLAR, "Xodimlar", "Xodimlar, ruxsatlar va audit jurnalini ko'rish", "Xodim qo'shish",
                    "Xodimni tahrirlash", null),
            new Capability(Module.AI, "AI yordamchi", "Tezkor xulosa", "AI bilan suhbat", null, null),
            new Capability(Module.SOZLAMALAR, "Sozlamalar", null, null, "Do'kon sozlamalarini o'zgartirish", null),
            new Capability(Module.PRINTER, "Printer", null, "Kassa qo'shish, test chek", "Kassani tahrirlash, qayta o'rnatish", "Kassani o'chirish"),
            new Capability(Module.FISKAL, "Fiskal chek", null, null, "Chekni fiskal modulga qayta yuborish", null));

    private PermissionCatalog() {
    }
}

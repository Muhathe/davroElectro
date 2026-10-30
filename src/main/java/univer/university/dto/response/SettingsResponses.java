package univer.university.dto.response;

public final class SettingsResponses {
    private SettingsResponses() {
    }

    public record StoreSettingsDto(String name, String address, String phone, String inn, String receiptFooter) {
    }
}

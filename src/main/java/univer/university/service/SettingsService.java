package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.dto.request.SettingsRequests;
import univer.university.dto.response.SettingsResponses.StoreSettingsDto;
import univer.university.entity.StoreSettings;
import univer.university.repository.StoreSettingsRepository;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final StoreSettingsRepository repo;
    private final AuditService audit;

    /** Hali saqlanmagan bo'lsa chekda shu nom chiqadi. */
    @Value("${app.store.name:Davron Elektro}")
    private String defaultName;

    @Transactional(readOnly = true)
    public StoreSettingsDto store() {
        return repo.findById(StoreSettings.SINGLE_ID).map(SettingsService::toDto)
                .orElseGet(() -> new StoreSettingsDto(defaultName, null, null, null, null));
    }

    @Transactional
    public StoreSettingsDto updateStore(SettingsRequests.Store req) {
        StoreSettings s = repo.findById(StoreSettings.SINGLE_ID)
                .orElseGet(() -> StoreSettings.builder().id(StoreSettings.SINGLE_ID).build());
        s.setName(req.name().trim());
        s.setAddress(blankToNull(req.address()));
        s.setPhone(blankToNull(req.phone()));
        s.setInn(blankToNull(req.inn()));
        s.setReceiptFooter(blankToNull(req.receiptFooter()));
        repo.save(s);
        audit.log("UPDATE", "StoreSettings", s.getId(), s.getName());
        return toDto(s);
    }

    private static StoreSettingsDto toDto(StoreSettings s) {
        return new StoreSettingsDto(s.getName(), s.getAddress(), s.getPhone(), s.getInn(), s.getReceiptFooter());
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}

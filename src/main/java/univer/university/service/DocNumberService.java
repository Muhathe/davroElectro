package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import univer.university.entity.DocCounter;
import univer.university.repository.DocCounterRepository;

/** Ketma-ket raqamlar (chek raqami). Qator bloklanadi, shuning uchun takrorlanmaydi. */
@Service
@RequiredArgsConstructor
public class DocNumberService {

    private final DocCounterRepository repository;

    @Transactional(propagation = Propagation.REQUIRED)
    public long next(String name) {
        DocCounter c = repository.lockByName(name)
                .orElseGet(() -> repository.save(new DocCounter(name, 0)));
        c.setLastValue(c.getLastValue() + 1);
        return c.getLastValue();
    }

    public String nextCheckNo() {
        return String.format("CHK-%06d", next("CHECK"));
    }
}

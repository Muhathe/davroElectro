package univer.university.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Hujjat raqamlari ketma-ketligi (masalan CHK). Qatori bloklanib oshiriladi, takrorlanmaydi. */
@Entity
@Table(name = "doc_counters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocCounter {

    @Id
    @Column(length = 30)
    private String name;

    @Column(name = "last_value", nullable = false)
    private long lastValue;
}

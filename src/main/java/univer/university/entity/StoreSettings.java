package univer.university.entity;

import jakarta.persistence.*;
import lombok.*;

/** Chekdagi do'kon rekvizitlari; jadvalda bitta qator (id = 1). */
@Entity
@Table(name = "store_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreSettings {

    public static final long SINGLE_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 250)
    private String address;

    @Column(length = 40)
    private String phone;

    @Column(length = 14)
    private String inn;

    @Column(length = 500)
    private String receiptFooter;
}

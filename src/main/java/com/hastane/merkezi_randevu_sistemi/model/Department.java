package com.hastane.merkezi_randevu_sistemi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "departments")
@Data
@NoArgsConstructor // Lombok otomatik boş constructor oluşturur
@AllArgsConstructor // Lombok tüm alanlı constructor oluşturur
public class Department {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name;

    // --- MANUEL DOKUNUŞLAR (Eclipse Hatasını Engellemek İçin) ---

    // 1. Boş Constructor: Java'nın "new Department()" diyebilmesi için şart!
    public Department() {
    }

    // 2. İsimli Constructor: "new Department('Göz')" diyebilmek için şart!
    public Department(String name) {
        this.name = name;
    }

    // 3. Setter: "goz.setName('Göz')" diyebilmek için şart!
    public void setName(String name) {
        this.name = name;
    }

    // 4. Getter: İsmi okuyabilmek için şart!
    public String getName() {
        return name;
    }
}
package com.hastane.merkezi_randevu_sistemi.model;
import jakarta.persistence.*;

@Entity
@Table(name = "departments")
public class Department {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name;

    // 1. HATAYI ÇÖZECEK OLAN BOŞ CONSTRUCTOR (DataLoader bunu arıyor)
    public Department() {
    }

    // 2. İsimli Constructor
    public Department(String name) {
        this.name = name;
    }

    // 3. Setter
    public void setName(String name) {
        this.name = name;
    }

    // 4. Getter
    public String getName() {
        return name;
    }

    // 5. id için getter/setter (yoksa JSON yanıtında id hiç görünmüyor!)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
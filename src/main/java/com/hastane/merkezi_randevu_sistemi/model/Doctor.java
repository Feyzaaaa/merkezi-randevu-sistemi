package com.hastane.merkezi_randevu_sistemi.model;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "doctors")
public class Doctor implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    private String title;

    public Doctor() {}

    // GETTER & SETTER - Bunlar Spring'in veriyi okuması için şart!
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    // KRİTİK EKLEME: Hata ayıklama ve sistem sağlığı için
    @Override
    public String toString() {
        return "Doctor{" +
                "id=" + id +
                ", user=" + (user != null ? user.getEmail() : "null") +
                ", title='" + title + '\'' +
                '}';
    }
}
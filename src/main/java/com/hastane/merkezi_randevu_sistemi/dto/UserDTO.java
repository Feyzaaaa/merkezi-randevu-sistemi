package com.hastane.merkezi_randevu_sistemi.dto;
import com.hastane.merkezi_randevu_sistemi.model.Role;
import lombok.Data;

@Data
public class UserDTO {
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
}
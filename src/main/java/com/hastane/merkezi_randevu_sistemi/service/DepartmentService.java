package com.hastane.merkezi_randevu_sistemi.service;
import com.hastane.merkezi_randevu_sistemi.model.Department;
import com.hastane.merkezi_randevu_sistemi.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DepartmentService {

    @Autowired
    private DepartmentRepository departmentRepository;

    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

}
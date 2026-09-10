package com.hastane.merkezi_randevu_sistemi.controller;

import com.hastane.merkezi_randevu_sistemi.model.Department;
import com.hastane.merkezi_randevu_sistemi.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    @GetMapping
    public List<Department> getAll() {
        return departmentService.getAllDepartments();
    }

    @PostMapping
    public Department create(@RequestBody Department department) {
        return departmentService.saveDepartment(department);
    }
}
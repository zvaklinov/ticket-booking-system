package com.hari.eventservice.category;

import com.hari.eventservice.category.dto.CategoryResponse;
import com.hari.eventservice.category.dto.CreateCategoryRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = categoryService.create(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<CategoryResponse> getAll() {
        return categoryService.getAll();
    }
}
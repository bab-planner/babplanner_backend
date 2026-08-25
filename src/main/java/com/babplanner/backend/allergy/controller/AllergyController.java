package com.babplanner.backend.allergy.controller;

import com.babplanner.backend.allergy.dto.AllergyResponse;
import com.babplanner.backend.allergy.service.AllergyService;
import com.babplanner.backend.common.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 알레르기 master catalog. OpenAPI에 security가 없는 공개 API다. */
@RestController
@RequestMapping("/api/v1/allergies")
public class AllergyController {

    private final AllergyService allergyService;

    public AllergyController(AllergyService allergyService) {
        this.allergyService = allergyService;
    }

    @GetMapping
    public ApiResponse<List<AllergyResponse>> getAllergies() {
        return ApiResponse.success(allergyService.getCatalog());
    }
}

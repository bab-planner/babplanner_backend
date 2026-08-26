package com.babplanner.backend.admin.controller;

import com.babplanner.backend.admin.dto.SurveyResultResponse;
import com.babplanner.backend.admin.dto.SurveyWriteRequest;
import com.babplanner.backend.admin.service.AdminSurveyService;
import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.survey.dto.SurveyResponseView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 설문 관리. */
@RestController
@RequestMapping("/api/v1/admin/surveys")
public class AdminSurveyController {

    private final AdminSurveyService adminSurveyService;

    public AdminSurveyController(AdminSurveyService adminSurveyService) {
        this.adminSurveyService = adminSurveyService;
    }

    @GetMapping
    public ApiResponse<List<SurveyResponseView>> getSurveys() {
        return ApiResponse.success(adminSurveyService.getSurveys());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SurveyResponseView>> createSurvey(
            @Valid @RequestBody SurveyWriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminSurveyService.create(request)));
    }

    @GetMapping("/{surveyId}/results")
    public ApiResponse<SurveyResultResponse> getResults(@PathVariable Long surveyId) {
        return ApiResponse.success(adminSurveyService.getResults(surveyId));
    }
}

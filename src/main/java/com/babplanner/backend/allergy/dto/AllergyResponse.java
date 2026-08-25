package com.babplanner.backend.allergy.dto;

import com.babplanner.backend.allergy.domain.Allergy;

/** OpenAPI Allergy. */
public record AllergyResponse(Long id, String name) {

    public static AllergyResponse from(Allergy allergy) {
        return new AllergyResponse(allergy.getId(), allergy.getName());
    }
}

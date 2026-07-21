package com.opsclear.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateLinkRequest {

    @NotBlank(message = "URL is required")
    private String url;

    @Size(max = 100, message = "Label must be at most 100 characters")
    private String label;
}

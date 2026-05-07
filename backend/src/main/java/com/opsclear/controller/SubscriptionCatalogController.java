package com.opsclear.controller;

import com.opsclear.dto.CatalogResponse;
import com.opsclear.service.SubscriptionCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionCatalogController {

    private final SubscriptionCatalogService catalogService;

    @GetMapping("/catalog")
    public ResponseEntity<CatalogResponse> getCatalog() {
        return ResponseEntity.ok(catalogService.getCatalog());
    }
}

package com.opsclear.dto;

import com.opsclear.model.PaddleCatalogSyncResult;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CatalogSyncResponse {

    private int tiersSynced;
    private int addonsSynced;

    public static CatalogSyncResponse from(PaddleCatalogSyncResult result) {
        return CatalogSyncResponse.builder()
                .tiersSynced(result.tiersSynced())
                .addonsSynced(result.addonsSynced())
                .build();
    }
}

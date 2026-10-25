package com.aiinterior.factory.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Everything the user tells the factory about one room. Kept in memory only. */
public record DesignBrief(
        @NotBlank @Size(max = 120) String projectName,
        @NotBlank @Size(max = 120) String roomName,
        @NotNull RoomType roomType,
        @Valid LengthInput length,
        @Valid LengthInput width,
        @Valid LengthInput height,
        List<@Valid OpeningInput> openings,
        List<@Valid KeepItemInput> keepItems,
        List<String> furnitureRequests,
        String style,
        String customStyle,
        List<String> palette,
        List<String> materialPreferences,
        @Valid Budget budget,
        List<String> functionalRequirements,
        List<String> lightingRequirements,
        @Size(max = 4000) String instructions) {

    public List<OpeningInput> openingsOrEmpty() {
        return openings == null ? List.of() : openings;
    }

    public List<KeepItemInput> keepItemsOrEmpty() {
        return keepItems == null ? List.of() : keepItems;
    }

    public List<String> furnitureOrEmpty() {
        return furnitureRequests == null ? List.of() : furnitureRequests;
    }

    public List<String> paletteOrEmpty() {
        return palette == null ? List.of() : palette;
    }
}

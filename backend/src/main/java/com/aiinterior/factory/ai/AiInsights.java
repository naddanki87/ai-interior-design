package com.aiinterior.factory.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Response contracts for each {@link AiPurpose}. Responses that don't match are rejected. */
public final class AiInsights {

    private AiInsights() {
    }

    public record RequirementsInsights(@NotBlank String summary, @NotNull List<String> extractedConstraints,
                                       @NotNull List<String> conflicts, @NotNull List<String> assumptions) {
    }

    public record StructuralSuggestion(@NotBlank String description, String reason) {
    }

    public record DesignNarrative(@NotBlank String conceptTitle, @NotBlank String concept,
                                  @NotNull List<String> featureWallIdeas, @NotNull List<String> tradeOffs,
                                  @NotNull List<@Valid StructuralSuggestion> structuralSuggestions) {
    }

    public record MaterialPick(@NotBlank String surface, @NotBlank String finish, String rationale) {
    }

    public record MaterialAdvice(@NotNull List<@Valid MaterialPick> picks, String notes) {
    }

    public static String schemaHint(AiPurpose purpose) {
        return switch (purpose) {
            case REQUIREMENTS_INSIGHTS -> "{\"summary\":string,\"extractedConstraints\":[string],\"conflicts\":[string],\"assumptions\":[string]}";
            case DESIGN_NARRATIVE -> "{\"conceptTitle\":string,\"concept\":string,\"featureWallIdeas\":[string],\"tradeOffs\":[string],"
                    + "\"structuralSuggestions\":[{\"description\":string,\"reason\":string}]}";
            case MATERIAL_ADVICE -> "{\"picks\":[{\"surface\":string,\"finish\":string,\"rationale\":string}],\"notes\":string}";
        };
    }

    public static Class<?> responseType(AiPurpose purpose) {
        return switch (purpose) {
            case REQUIREMENTS_INSIGHTS -> RequirementsInsights.class;
            case DESIGN_NARRATIVE -> DesignNarrative.class;
            case MATERIAL_ADVICE -> MaterialAdvice.class;
        };
    }
}

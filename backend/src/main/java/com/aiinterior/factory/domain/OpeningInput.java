package com.aiinterior.factory.domain;

import jakarta.validation.constraints.NotNull;

public record OpeningInput(@NotNull OpeningType type, @NotNull WallSide wall, LengthInput offset, LengthInput width,
                           LengthInput height, LengthInput sillHeight, String label) {
}

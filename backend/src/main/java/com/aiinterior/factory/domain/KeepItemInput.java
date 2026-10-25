package com.aiinterior.factory.domain;

import jakarta.validation.constraints.NotBlank;

/** An existing object that must stay exactly where it is. x/y are its minimum corner. */
public record KeepItemInput(@NotBlank String name, LengthInput width, LengthInput depth, LengthInput height,
                            LengthInput x, LengthInput y) {
}

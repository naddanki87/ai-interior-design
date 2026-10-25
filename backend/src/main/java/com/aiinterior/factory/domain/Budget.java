package com.aiinterior.factory.domain;

import jakarta.validation.constraints.PositiveOrZero;

public record Budget(@PositiveOrZero Double amount, String currency) {
}

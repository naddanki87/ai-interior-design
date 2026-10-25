package com.aiinterior.factory.geometry;

/** How strongly an element was asked for. MUST_NOT_CHANGE items are fixed by the user. */
public enum Priority {
    REQUIRED, PREFERRED, OPTIONAL, MUST_NOT_CHANGE
}

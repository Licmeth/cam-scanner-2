package com.licmeth.camscanner.model

enum class ColorProfile(val value: Int) {
    COLOR(1),
    GRAYSCALE(2),
    BLACK_AND_WHITE(3);

    companion object {
        private val VALUE_MAP: Map<Int, ColorProfile> = entries.associateBy { it.value }

        /** Returns matching ColorProfile or throws if not found */
        fun of(value: Int): ColorProfile = VALUE_MAP[value]
                ?: throw IllegalArgumentException("Unsupported color profile value: $value")
    }
}
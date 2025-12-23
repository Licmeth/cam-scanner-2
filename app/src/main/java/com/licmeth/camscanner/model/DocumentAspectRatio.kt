package com.licmeth.camscanner.model

enum class DocumentAspectRatio(val value: Int, val ratio: Float) {
    DIN_476_2(1, 1.4142F),  // 1/sqrt(2) for all DIN 476-2 / EN ISO 216 paper formats, e.g. A4, A5
    ANSI_LETTER(2, 1.2941F);   // ANSI A letter format, 8.5 x 11 inches

    companion object {
        private val VALUE_MAP: Map<Int, DocumentAspectRatio> = entries.associateBy { it.value }

        /** Returns matching RotationType or throws if not found */
        fun of(value: Int): DocumentAspectRatio = VALUE_MAP[value]
                ?: throw IllegalArgumentException("Unsupported aspect ratio value: $value")
    }
}
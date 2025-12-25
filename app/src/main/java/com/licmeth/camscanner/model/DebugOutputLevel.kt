package com.licmeth.camscanner.model

enum class DebugOutputLevel(val value: Int) {
    PREPROCESSED(0),
    CONTENT_REMOVED(1),
    EDGES_DETECTED(2);

    companion object {
        private val VALUE_MAP: Map<Int, DebugOutputLevel> = DebugOutputLevel.entries.associateBy { it.value }

        /** Returns matching RotationType or throws if not found */
        fun of(value: Int): DebugOutputLevel = VALUE_MAP[value]
            ?: throw IllegalArgumentException("Unsupported aspect ratio value: $value")
    }
}
package com.example.myapplicationmusicsharing.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Null-safe JsonElement navigation for hand-parsing API responses. */

operator fun JsonElement?.get(key: String): JsonElement? =
    (this as? JsonObject)?.get(key)

operator fun JsonElement?.get(index: Int): JsonElement? =
    (this as? JsonArray)?.getOrNull(index)

val JsonElement?.array: List<JsonElement>
    get() = (this as? JsonArray) ?: emptyList()

val JsonElement?.str: String?
    get() = (this as? JsonPrimitive)?.contentOrNull

val JsonElement?.int: Int?
    get() = (this as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

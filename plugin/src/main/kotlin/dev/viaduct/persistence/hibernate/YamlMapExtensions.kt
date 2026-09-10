package dev.viaduct.persistence.hibernate

/** Small typed accessors over the plain `Map<String, Any?>` structures SnakeYAML produces. */
internal fun Map<String, Any?>.yamlString(key: String): String = this[key] as String

internal fun Map<String, Any?>.yamlBoolean(key: String): Boolean = this[key] as Boolean

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.yamlStringList(key: String): List<String> = (this[key] as List<String>?).orEmpty()

@Suppress("UNCHECKED_CAST", "MaxLineLength")
internal fun Map<String, Any?>.yamlStringMap(key: String): Map<String, String> = (this[key] as Map<String, String>?).orEmpty()

@Suppress("UNCHECKED_CAST", "MaxLineLength")
internal fun Map<String, Any?>.yamlMapList(key: String): List<Map<String, Any?>> = (this[key] as List<Map<String, Any?>>?).orEmpty()

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.yamlMapOrNull(key: String): Map<String, Any?>? = this[key] as Map<String, Any?>?

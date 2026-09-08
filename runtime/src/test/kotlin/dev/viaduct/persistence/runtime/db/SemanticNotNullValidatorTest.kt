package dev.viaduct.persistence.runtime.db

import dev.viaduct.persistence.pggraphql.translation.PgGraphqlFieldCoordinate
import dev.viaduct.persistence.pggraphql.translation.PgGraphqlTranslationSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticNotNullValidatorTest {
    private val validator = SemanticNotNullValidator(setOf("Group.name"))
    private val schema =
        PgGraphqlTranslationSchema(
            collectionElementTypes = emptyMap(),
            fieldTypes = mapOf(PgGraphqlFieldCoordinate("Group", "name") to "String"),
        )

    @Test
    fun `accepts a semantic non-null field containing a value`() {
        val errors = validate("""{"name":"Ada"}""")

        assertEquals(emptyList(), errors)
    }

    @Test
    fun `accepts null when an upstream error explains the field path`() {
        val upstream =
            UpstreamGraphqlError(
                message = "name failed",
                path = listOf(JsonPrimitive("group"), JsonPrimitive("name")),
            )

        val errors = validate("""{"name":null}""", listOf(upstream))

        assertEquals(listOf(upstream), errors)
    }

    @Test
    fun `adds an error when semantic non-null is null without an upstream error`() {
        val errors = validate("""{"name":null}""")

        assertEquals(1, errors.size)
        assertEquals("Semantic non-null field 'Group.name' returned null without an error", errors.single().message)
        assertEquals(
            listOf("group", "name"),
            errors.single().path.map { it.toString().trim('"') },
        )
        assertEquals("\"SEMANTIC_NON_NULL_VIOLATION\"", errors.single().extensions["code"].toString())
    }

    private fun validate(
        data: String,
        errors: List<UpstreamGraphqlError> = emptyList(),
    ): List<UpstreamGraphqlError> =
        validator.validate(
            SemanticValidationRequest(
                data = Json.parseToJsonElement(data).jsonObject,
                errors = errors,
                document = "fragment Main on Group { name }",
                rootType = "Group",
                rootResponseKey = "group",
                schema = schema,
            ),
        )
}

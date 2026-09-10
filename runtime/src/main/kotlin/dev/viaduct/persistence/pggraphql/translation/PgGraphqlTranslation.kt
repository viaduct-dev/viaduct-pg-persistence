package dev.viaduct.persistence.pggraphql.translation

import kotlinx.serialization.json.JsonElement

object PgGraphqlTranslation {
    private val documentTranslator = SelectionDocumentTranslator()
    private val responseShapeRestorer = ResponseShapeRestorer()

    fun translateSelectionDocument(
        document: String,
        schema: PgGraphqlTranslationSchema,
        rewriteCollectionTypes: Boolean = true,
        allowInternalResponseAlias: Boolean = false,
    ): String =
        documentTranslator.translate(
            document,
            schema,
            rewriteCollectionTypes,
            allowInternalResponseAlias,
        )

    fun restoreViaductResponseShape(response: JsonElement): JsonElement = responseShapeRestorer.restore(response)

    fun restoreViaductResponsePath(path: List<JsonElement>): List<JsonElement> = responseShapeRestorer.restorePath(path)

    fun buildRootQuery(
        field: String,
        arguments: String,
        variableDefinitions: String,
        fragmentDocument: String,
        singleViaFilteredCollection: Boolean,
    ): String =
        RootQueryBuilder.build(
            field,
            arguments,
            variableDefinitions,
            fragmentDocument,
            singleViaFilteredCollection,
        )
}

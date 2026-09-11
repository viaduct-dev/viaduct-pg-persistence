package dev.viaduct.persistence.runtime.db

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DbTransactionTest {
    @Test
    fun `operations remain buffered until commit`() =
        runBlocking {
            val fixture = DbTransactionTestFixture()
            fixture.addMixedOperations()

            assertThat(fixture.requests()).isEmpty()
            fixture.commit()
            assertThat(fixture.requests()).hasSize(1)
        }

    @Test
    fun `commit returns payloads by operation handle`() =
        runBlocking {
            val fixture = DbTransactionTestFixture()
            val handles = fixture.addMixedOperations()

            val result = fixture.commit()

            assertThat(handles.map { result[it]?.recordIds() })
                .isEqualTo(listOf(listOf("group-1"), listOf("member-1")))
        }

    @Test
    fun `abort discards operations and closes the transaction`() {
        val fixture = DbTransactionTestFixture()
        fixture.entity("Group").insert(PgGraphqlObject.of("name" to "Chess"))

        fixture.abort()
        fixture.abort()

        assertThat(fixture.requests()).isEmpty()
        assertFailsWith<IllegalStateException> {
            fixture.entity("Group").delete(PgGraphqlDelete(PgGraphqlFilter.empty()))
        }
    }

    @Test
    fun `empty commit fails without a request`() =
        runBlocking {
            val fixture = DbTransactionTestFixture()

            assertFailsWith<IllegalArgumentException> { fixture.commit() }

            assertThat(fixture.requests()).isEmpty()
        }

    @Test
    fun `committed transaction cannot be committed again`() =
        runBlocking {
            val fixture = DbTransactionTestFixture()
            fixture.entity("Group").insert(PgGraphqlObject.of("name" to "Chess"))
            fixture.commit()

            assertFailsWith<IllegalStateException> { fixture.commit() }
            assertThat(fixture.requests()).hasSize(1)
        }

    @Test
    fun `result form preserves partial data and errors`() =
        runBlocking {
            val fixture = DbTransactionTestFixture(DbTransactionTestFixture.PARTIAL_RESPONSE)
            val first = fixture.entity("Group").insert(PgGraphqlObject.of("name" to "Chess"))
            val second = fixture.entity("Group").insert(PgGraphqlObject.of("name" to "Go"))

            val result = fixture.commitResult()

            assertThat(
                PartialResult(
                    result.data?.get(first)?.recordIds(),
                    result.data?.get(second)?.recordIds(),
                    result.errors.map(UpstreamGraphqlError::message),
                ),
            ).isEqualTo(PartialResult(listOf("group-1"), null, listOf("member failed")))
        }

    private data class PartialResult(
        val firstRecordIds: List<String>?,
        val secondRecordIds: List<String>?,
        val errors: List<String>,
    )
}

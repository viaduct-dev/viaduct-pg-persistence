package dev.viaduct.persistence.liquibase

import dev.viaduct.persistence.hibernate.HibernateMetadataConfiguration
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.ext.hibernate.database.HibernateDatabase
import liquibase.resource.ClassLoaderResourceAccessor
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HibernateMetadataReferenceTest {
    @Test
    fun `closing a reference removes its configuration`() {
        val mappingFile = temporaryMapping("not used")
        try {
            val configuration = configuration(mappingFile)
            val reference = ViaductHibernateDatabase.reference(configuration)
            val token = reference.url.removePrefix(HIBERNATE_VIADUCT_URL_PREFIX)

            try {
                assertSame(configuration, HibernateMetadataReferenceRegistry.resolve(token))
            } finally {
                reference.close()
                reference.close()
            }

            assertFailsWith<IllegalArgumentException> {
                HibernateMetadataReferenceRegistry.resolve(token)
            }
        } finally {
            delete(mappingFile)
        }
    }

    @Test
    fun `database close releases a reference`() {
        val mappingFile = temporaryMapping(validMapping())
        val configuration = validConfiguration(mappingFile)
        val reference = ViaductHibernateDatabase.reference(configuration)
        val token = reference.url.removePrefix(HIBERNATE_VIADUCT_URL_PREFIX)
        val resourceAccessor = ClassLoaderResourceAccessor(javaClass.classLoader)
        var database: Database? = null
        try {
            try {
                database =
                    DatabaseFactory.getInstance().openDatabase(
                        reference.url,
                        null,
                        null,
                        null,
                        resourceAccessor,
                    )
                assertNotNull((database as HibernateDatabase).metadata)
            } finally {
                database?.close()
                resourceAccessor.close()
            }
            assertFailsWith<IllegalArgumentException> {
                HibernateMetadataReferenceRegistry.resolve(token)
            }
        } finally {
            reference.close()
            delete(mappingFile)
        }
    }

    @Test
    fun `reference scope releases configuration when Liquibase initialization fails`() {
        val mappingFile = temporaryMapping("not valid Hibernate XML")
        val configuration = configuration(mappingFile)
        val token: String
        try {
            ViaductHibernateDatabase.reference(configuration).use { reference ->
                token = reference.url.removePrefix(HIBERNATE_VIADUCT_URL_PREFIX)
                val failure = metadataFailure(reference.url)
                assertTrue(failure.message.orEmpty().contains("Hibernate metadata"))
            }
        } finally {
            delete(mappingFile)
        }

        assertFailsWith<IllegalArgumentException> {
            HibernateMetadataReferenceRegistry.resolve(token)
        }
    }

    @Test
    fun `unknown reference tokens fail when Liquibase requests metadata`() {
        val failure = metadataFailure(HIBERNATE_VIADUCT_URL_PREFIX + "unknown-token")
        assertTrue(failure.hasCauseMessage("No Hibernate metadata configuration"))
    }

    @Test
    fun `empty reference tokens fail when Liquibase requests metadata`() {
        metadataFailure(HIBERNATE_VIADUCT_URL_PREFIX)
    }

    private fun metadataFailure(url: String): Throwable {
        val resourceAccessor = ClassLoaderResourceAccessor(javaClass.classLoader)
        var database: Database? = null
        return try {
            assertFailsWith<Exception> {
                database =
                    DatabaseFactory.getInstance().openDatabase(
                        url,
                        null,
                        null,
                        null,
                        resourceAccessor,
                    )
                assertNotNull((database as HibernateDatabase).metadata)
            }
        } finally {
            database?.close()
            resourceAccessor.close()
        }
    }

    private fun Throwable.hasCauseMessage(fragment: String): Boolean =
        generateSequence(this) { it.cause }
            .any { it.message?.contains(fragment) == true }

    private fun temporaryMapping(contents: String): File =
        File.createTempFile("hibernate-metadata-reference", ".xml").also {
            it.writeText(contents)
        }

    private fun delete(file: File) {
        check(!file.exists() || file.delete()) {
            "Could not delete ${file.absolutePath}"
        }
    }

    private fun configuration(mappingFile: File): HibernateMetadataConfiguration =
        HibernateMetadataConfiguration(
            mappingFile = mappingFile,
            classpath = emptyList(),
            managedClassNames = listOf("example.MissingEntity"),
        )

    private fun validConfiguration(mappingFile: File): HibernateMetadataConfiguration =
        HibernateMetadataConfiguration(
            mappingFile = mappingFile,
            classpath = classpath(),
            managedClassNames = listOf(TestReferenceEntity::class.java.name),
        )

    private fun classpath(): List<File> =
        System
            .getProperty("java.class.path")
            .split(File.pathSeparator)
            .map(::File)
            .filter(File::exists)

    private fun validMapping(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <entity-mappings xmlns="https://jakarta.ee/xml/ns/persistence/orm" version="3.2">
          <entity class="${TestReferenceEntity::class.java.name}" access="FIELD">
            <table name="reference_entities"/>
            <attributes>
              <id name="id">
                <column name="id" nullable="false"/>
              </id>
            </attributes>
          </entity>
        </entity-mappings>
        """.trimIndent()
}

class TestReferenceEntity {
    var id: String? = null
}

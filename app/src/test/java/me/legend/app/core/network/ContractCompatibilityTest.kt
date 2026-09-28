package me.legend.app.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun sharedExamplesDecodeIntoKotlinWireTypes() {
        val understanding = json.decodeFromString<UnderstandingJobRequest>(fixture("understanding-job.request.json"))
        val knowledge = json.decodeFromString<KnowledgePatchDto>(fixture("knowledge-patch.response.json"))
        val creation = json.decodeFromString<CreationJobRequest>(fixture("creation-job.request.json"))
        val embedding = json.decodeFromString<EmbeddingResponse>(fixture("embedding.response.json"))

        assertEquals("session-linzhou-001", understanding.sourceSessionId)
        assertEquals("1.0", knowledge.schemaVersion)
        assertTrue(creation.corpus.isNotEmpty())
        assertEquals(3, embedding.dimensions)
    }

    private fun fixture(name: String): String {
        val resource = requireNotNull(javaClass.classLoader?.getResource(name)) {
            "Cannot locate shared contract fixture: $name"
        }
        return resource.readText()
    }
}

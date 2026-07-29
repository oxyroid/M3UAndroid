package com.m3u.extension.conformance

import kotlin.test.Test
import kotlin.test.assertNotNull

class GoldenFixturePackagingTest {
    @Test
    fun `published conformance resources include wire examples`() {
        REPRESENTATIVE_FIXTURES.forEach { resourcePath ->
            assertNotNull(
                javaClass.classLoader.getResource(resourcePath),
                "Missing conformance resource $resourcePath",
            )
        }
    }

    private companion object {
        val REPRESENTATIVE_FIXTURES = listOf(
            "golden-wire/v1/README.md",
            "golden-wire/v1/README.zh-CN.md",
            "golden-wire/v1/envelopes/invocation-current.json",
            "golden-wire/v1/envelopes/result-success.json",
            "golden-wire/v1/manifests/complete.json",
            "golden-wire/v1/hooks/background.task.run/schema-2/request.json",
            "golden-wire/v1/hooks/background.task.run/schema-2/result.json",
        )
    }
}

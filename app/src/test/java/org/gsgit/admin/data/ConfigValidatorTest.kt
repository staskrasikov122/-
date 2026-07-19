package org.gsgit.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigValidatorTest {
    @Test
    fun acceptsSemanticTriplesAndHttpsUrl() {
        val result = ConfigValidator.validate(
            AppConfig(
                latestVersion = "1.0.85",
                minVersion = "1.0.80",
                downloadUrl = "https://gsgit.org/download",
            ),
        )

        assertNull(result)
    }

    @Test
    fun rejectsIncompleteVersion() {
        val result = ConfigValidator.validate(
            AppConfig(latestVersion = "1.85", minVersion = "1.0.0"),
        )

        assertEquals("Latest version must use x.y.z", result)
    }

    @Test
    fun rejectsNonHttpDownloadUrl() {
        val result = ConfigValidator.validate(
            AppConfig(
                latestVersion = "1.0.85",
                minVersion = "1.0.80",
                downloadUrl = "file:///tmp/admin.apk",
            ),
        )

        assertEquals("Download URL must start with https:// or http://", result)
    }
}

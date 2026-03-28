package com.novaterm.feature.oemcompat

import com.novaterm.feature.oemcompat.detection.OemBrand
import com.novaterm.feature.oemcompat.detection.OemDetector
import com.novaterm.feature.oemcompat.detection.OemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemDetectorTest {

    @Test
    fun `all OEM brands have non-empty display names`() {
        OemBrand.entries.forEach { brand ->
            assertTrue(
                "Brand $brand has empty displayName",
                brand.displayName.isNotBlank()
            )
        }
    }

    @Test
    fun `aggressiveness ranges from 0 to 5`() {
        OemBrand.entries.forEach { brand ->
            assertTrue(
                "Brand $brand aggressiveness ${brand.aggressiveness} out of range",
                brand.aggressiveness in 0..5
            )
        }
    }

    @Test
    fun `Google has lowest aggressiveness`() {
        assertEquals(0, OemBrand.GOOGLE.aggressiveness)
    }

    @Test
    fun `Xiaomi and Huawei have highest aggressiveness`() {
        assertEquals(5, OemBrand.XIAOMI.aggressiveness)
        assertEquals(5, OemBrand.HUAWEI.aggressiveness)
    }

    @Test
    fun `battery whitelist needed for aggressive OEMs when optimized`() {
        val xiaomiOptimized = OemInfo(
            brand = OemBrand.XIAOMI,
            manufacturer = "Xiaomi",
            model = "15T Pro",
            androidVersion = 35,
            isBatteryOptimized = true,
        )
        assertTrue(OemDetector.needsBatteryWhitelist(xiaomiOptimized))
    }

    @Test
    fun `battery whitelist not needed when already exempted`() {
        val xiaomiExempted = OemInfo(
            brand = OemBrand.XIAOMI,
            manufacturer = "Xiaomi",
            model = "15T Pro",
            androidVersion = 35,
            isBatteryOptimized = false,
        )
        assertFalse(OemDetector.needsBatteryWhitelist(xiaomiExempted))
    }

    @Test
    fun `battery whitelist not needed for non-aggressive OEMs`() {
        val googleOptimized = OemInfo(
            brand = OemBrand.GOOGLE,
            manufacturer = "Google",
            model = "Pixel 9",
            androidVersion = 35,
            isBatteryOptimized = true,
        )
        assertFalse(OemDetector.needsBatteryWhitelist(googleOptimized))
    }

    @Test
    fun `all brands have non-empty instructions`() {
        OemBrand.entries.forEach { brand ->
            val instructions = OemDetector.getInstructions(brand)
            assertTrue(
                "Brand $brand has empty instructions",
                instructions.isNotEmpty()
            )
            instructions.forEach { step ->
                assertTrue(
                    "Brand $brand has blank instruction step",
                    step.isNotBlank()
                )
            }
        }
    }

    @Test
    fun `Xiaomi instructions mention autostart`() {
        val instructions = OemDetector.getInstructions(OemBrand.XIAOMI)
        assertTrue(
            "Xiaomi instructions should mention Autostart",
            instructions.any { it.contains("Autostart", ignoreCase = true) }
        )
    }

    @Test
    fun `Samsung instructions mention never sleeping`() {
        val instructions = OemDetector.getInstructions(OemBrand.SAMSUNG)
        assertTrue(
            "Samsung instructions should mention Never sleeping",
            instructions.any { it.contains("Never sleeping", ignoreCase = true) }
        )
    }
}

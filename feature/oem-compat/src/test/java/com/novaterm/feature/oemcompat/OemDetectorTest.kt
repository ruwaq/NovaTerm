package com.novaterm.feature.oemcompat

import com.novaterm.feature.oemcompat.detection.OemBrand
import com.novaterm.feature.oemcompat.detection.OemDetector
import com.novaterm.feature.oemcompat.detection.OemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemDetectorTest {

    private fun fakeOemInfo(
        brand: OemBrand,
        isBatteryOptimized: Boolean = true,
    ) = OemInfo(
        brand = brand,
        manufacturer = brand.displayName,
        model = "TestModel",
        androidVersion = 35,
        androidVersionName = "15",
        isBatteryOptimized = isBatteryOptimized,
    )

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
    fun `Tecno and Infinix have high aggressiveness`() {
        assertEquals(4, OemBrand.TECNO.aggressiveness)
        assertEquals(4, OemBrand.INFINIX.aggressiveness)
    }

    @Test
    fun `Nothing has moderate aggressiveness`() {
        assertEquals(3, OemBrand.NOTHING.aggressiveness)
    }

    @Test
    fun `battery whitelist needed for aggressive OEMs when optimized`() {
        assertTrue(OemDetector.needsBatteryWhitelist(fakeOemInfo(OemBrand.XIAOMI)))
        assertTrue(OemDetector.needsBatteryWhitelist(fakeOemInfo(OemBrand.TECNO)))
        assertTrue(OemDetector.needsBatteryWhitelist(fakeOemInfo(OemBrand.INFINIX)))
        assertTrue(OemDetector.needsBatteryWhitelist(fakeOemInfo(OemBrand.NOTHING)))
    }

    @Test
    fun `battery whitelist not needed when already exempted`() {
        assertFalse(
            OemDetector.needsBatteryWhitelist(
                fakeOemInfo(OemBrand.XIAOMI, isBatteryOptimized = false)
            )
        )
    }

    @Test
    fun `battery whitelist not needed for non-aggressive OEMs`() {
        assertFalse(OemDetector.needsBatteryWhitelist(fakeOemInfo(OemBrand.GOOGLE)))
        assertFalse(OemDetector.needsBatteryWhitelist(fakeOemInfo(OemBrand.MOTOROLA)))
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

    @Test
    fun `Tecno instructions mention phone manager`() {
        val instructions = OemDetector.getInstructions(OemBrand.TECNO)
        assertTrue(
            "Tecno instructions should mention Phone Manager",
            instructions.any { it.contains("Phone Manager", ignoreCase = true) }
        )
    }

    @Test
    fun `Infinix shares Tecno instructions`() {
        assertEquals(
            OemDetector.getInstructions(OemBrand.TECNO),
            OemDetector.getInstructions(OemBrand.INFINIX),
        )
    }
}

package com.forgerom.app.sources

import com.forgerom.app.device.PhoneLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneLogicTest {

    @Test
    fun flag_parsesCommonValues() {
        assertEquals(true, PhoneLogic.flag("1"))
        assertEquals(false, PhoneLogic.flag(" 0 "))
        assertEquals(true, PhoneLogic.flag("TRUE"))
        assertNull(PhoneLogic.flag("maybe"))
        assertNull(PhoneLogic.flag(null))
    }

    @Test
    fun bootloaderLocked_usesFlashLockedFirstThenVbmetaThenVerifiedBoot() {
        assertEquals(true, PhoneLogic.bootloaderLocked("1", "unlocked", "orange"))
        assertEquals(false, PhoneLogic.bootloaderLocked(null, "unlocked", "green"))
        assertEquals(true, PhoneLogic.bootloaderLocked(null, null, "green"))
        assertEquals(false, PhoneLogic.bootloaderLocked(null, null, "orange"))
        assertNull(PhoneLogic.bootloaderLocked(null, null, null))
    }

    @Test
    fun percent_handlesInvalidInput() {
        assertEquals(83, PhoneLogic.percent(83, 100))
        assertEquals(50, PhoneLogic.percent(127, 254))
        assertNull(PhoneLogic.percent(-1, 100))
        assertNull(PhoneLogic.percent(10, 0))
    }
}

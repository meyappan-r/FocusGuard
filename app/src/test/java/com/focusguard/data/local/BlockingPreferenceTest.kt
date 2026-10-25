package com.focusguard.data.local

import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class BlockingPreferenceTest {

    /** Minimal SharedPreferences whose only supported call is getBoolean. */
    private fun prefsWith(values: Map<String, Boolean>): SharedPreferences =
        Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getBoolean" -> values[args[0] as String] ?: (args[1] as Boolean)
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences

    @Test fun enabledByDefault_whenNothingStored() {
        assertTrue(BlockingPreference.isEnabled(prefsWith(emptyMap())))
    }

    @Test fun reflectsStoredValue() {
        assertFalse(BlockingPreference.isEnabled(prefsWith(mapOf(BlockingPreference.KEY_BLOCKING_ENABLED to false))))
        assertTrue(BlockingPreference.isEnabled(prefsWith(mapOf(BlockingPreference.KEY_BLOCKING_ENABLED to true))))
    }

    @Test fun keyAndFileNameMatchWhatSettingsAlreadyPersisted() {
        // Existing installs stored the switch under these names; changing them would reset the user's choice.
        assertTrue(BlockingPreference.PREFS_NAME == "settings_prefs")
        assertTrue(BlockingPreference.KEY_BLOCKING_ENABLED == "blocking_enabled")
    }
}

package com.yikiu.roadcast.core

import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import com.yikiu.roadcast.core.host.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AppDialogsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun clearPreferences() {
        context.getSharedPreferences("roadcast_airplay", 0).edit().clear().commit()
        AppAppearanceRuntime.resetForTest()
    }

    @Test fun dialogThemesUseTheResolvedAppAppearance() {
        AirPlayPersistence.saveAppAppearance(context, AppAppearance.DARK)
        assertDialogColors(context.appDialogContext(), RoadCastPalette.DARK)

        AirPlayPersistence.saveAppAppearance(context, AppAppearance.LIGHT)
        assertDialogColors(context.appDialogContext(), RoadCastPalette.LIGHT)
    }

    @Test fun anExistingDialogContextKeepsItsAppearanceAfterThePreferenceChanges() {
        AirPlayPersistence.saveAppAppearance(context, AppAppearance.LIGHT)
        val existingContext = context.appDialogBuilder().context

        AirPlayPersistence.saveAppAppearance(context, AppAppearance.DARK)

        assertDialogColors(existingContext, RoadCastPalette.LIGHT)
        assertDialogColors(context.appDialogBuilder().context, RoadCastPalette.DARK)
    }

    @Test fun dialogUsesTheOwnersCurrentAppearanceInsteadOfResolvingItAgain() {
        AirPlayPersistence.saveAppAppearance(context, AppAppearance.DARK)
        val lightOwner = object : ContextWrapper(context), AppAppearanceOwner {
            override val currentAppNight = false
        }

        assertDialogColors(lightOwner.appDialogContext(), RoadCastPalette.LIGHT)
    }

    /** Android 9 and older drop a literal colour here, and dialogs showed through (#640, #617). */
    @Test fun windowBackgroundsAreResourcesThatAndroid9CanLoad() {
        for (theme in listOf(
            R.style.Theme_RoadCast, R.style.Theme_RoadCast_Light,
            R.style.Theme_RoadCast_Dialog_Dark, R.style.Theme_RoadCast_Dialog_Light,
        )) {
            val attributes = android.view.ContextThemeWrapper(context, theme).theme
                .obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
            try {
                // PhoneWindow.generateLayout() on API 28 uses exactly this lookup.
                assertNotEquals("theme $theme", 0, attributes.getResourceId(0, 0))
            } finally {
                attributes.recycle()
            }
        }
    }

    private fun assertDialogColors(themedContext: android.content.Context, palette: RoadCastPalette) {
        val attributes = themedContext.theme.obtainStyledAttributes(
            intArrayOf(android.R.attr.colorAccent, android.R.attr.windowBackground),
        )
        try {
            assertEquals(palette.accent, attributes.getColor(0, 0))
            assertEquals(palette.surface, (attributes.getDrawable(1) as ColorDrawable).color)
        } finally {
            attributes.recycle()
        }
    }
}

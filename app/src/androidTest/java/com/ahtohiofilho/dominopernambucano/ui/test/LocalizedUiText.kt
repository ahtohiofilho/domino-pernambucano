package com.ahtohiofilho.dominopernambucano.ui.test

import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Read the active Android language rather than asserting hard-coded Portuguese.
 * The translated string ID is the stable semantic contract.
 */
internal fun localizedUiText(@StringRes resourceId: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

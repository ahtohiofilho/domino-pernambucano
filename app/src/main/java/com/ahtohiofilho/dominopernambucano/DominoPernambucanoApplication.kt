package com.ahtohiofilho.dominopernambucano

import android.app.Application
import com.ahtohiofilho.dominopernambucano.online.PlayGamesSignInEnvironment
import com.google.android.gms.games.PlayGamesSdk

class DominoPernambucanoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (PlayGamesSignInEnvironment.Current.isConfigured) {
            PlayGamesSdk.initialize(this)
        }
    }
}

package com.houseofmmminq.macaco

import android.app.Application
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.ktx.Firebase
import com.houseofmmminq.macaco.data.PreferencesManager
import com.houseofmmminq.macaco.data.auth.AuthRepository
import com.houseofmmminq.macaco.data.auth.FirebaseAuthRepository
import com.houseofmmminq.macaco.data.billing.BillingManager
import com.houseofmmminq.macaco.data.storage.CloudEntrySync
import com.houseofmmminq.macaco.data.sync.DrivePhotoSync

class TravelJournalApp : Application() {
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }
    val authRepository: AuthRepository by lazy { FirebaseAuthRepository(this) }
    val cloudEntrySync: CloudEntrySync by lazy { CloudEntrySync(authRepository) }
    val billingManager: BillingManager by lazy { BillingManager(this, preferencesManager, authRepository) }
    val drivePhotoSync: DrivePhotoSync by lazy { DrivePhotoSync(this) }

    override fun onCreate() {
        super.onCreate()
        // Attests that Firestore/Auth calls come from a genuine, unmodified install. Must run
        // before the first Firestore/Auth call (the lazy repos above are fine — they're not
        // touched until MainActivity starts).
        Firebase.appCheck.installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) debugAppCheckProviderFactory()
            else PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }

    // Reflective lookup: the debug provider class only exists on the debugImplementation
    // classpath, so a direct reference would fail to compile in release builds.
    private fun debugAppCheckProviderFactory(): AppCheckProviderFactory {
        val clazz = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
        return clazz.getMethod("getInstance").invoke(null) as AppCheckProviderFactory
    }
}

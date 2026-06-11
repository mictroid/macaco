package com.houseofmmminq.macaco

import android.app.Application
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
}

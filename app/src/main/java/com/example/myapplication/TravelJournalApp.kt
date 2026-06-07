package com.example.myapplication

import android.app.Application
import com.example.myapplication.data.PreferencesManager
import com.example.myapplication.data.auth.AuthRepository
import com.example.myapplication.data.auth.FirebaseAuthRepository
import com.example.myapplication.data.billing.BillingManager
import com.example.myapplication.data.storage.CloudEntrySync
import com.example.myapplication.data.sync.DrivePhotoSync

class TravelJournalApp : Application() {
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }
    val authRepository: AuthRepository by lazy { FirebaseAuthRepository(this) }
    val cloudEntrySync: CloudEntrySync by lazy { CloudEntrySync(authRepository) }
    val billingManager: BillingManager by lazy { BillingManager(this, preferencesManager, authRepository) }
    val drivePhotoSync: DrivePhotoSync by lazy { DrivePhotoSync(this) }
}

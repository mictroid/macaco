package com.houseofmmminq.macaco.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/** Thin funnel-event wrapper. Params must never contain user content or PII. */
object Analytics {
    private var fa: FirebaseAnalytics? = null

    fun init(context: Context) {
        fa = FirebaseAnalytics.getInstance(context.applicationContext)
    }

    fun log(event: String, vararg params: Pair<String, Any?>) {
        val b = Bundle()
        params.forEach { (k, v) ->
            when (v) {
                null -> Unit
                is String -> b.putString(k, v.take(100))
                is Int -> b.putLong(k, v.toLong())
                is Long -> b.putLong(k, v)
                is Boolean -> b.putString(k, v.toString())
                else -> b.putString(k, v.toString().take(100))
            }
        }
        fa?.logEvent(event, b)
    }

    fun setPremium(isPremium: Boolean) {
        fa?.setUserProperty("is_premium", isPremium.toString())
    }
}

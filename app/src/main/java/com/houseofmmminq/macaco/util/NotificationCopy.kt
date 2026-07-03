package com.houseofmmminq.macaco.util

import android.content.Context
import com.houseofmmminq.macaco.R

/**
 * Builds personalized copy for the journal reminder notification from the user's own data.
 *
 * Priority order (most personal first): last location → days since last entry → entry-count
 * milestone → brand fallback for users with no entries yet.
 */
fun buildNotificationCopy(
    context: Context,
    entryCount: Int,
    daysSinceLast: Int?,
    lastLocation: String?
): Pair<String, String> {

    // Location-based (highest priority — most personal)
    if (lastLocation != null) {
        return Pair(
            context.getString(R.string.reminder_copy_location_title, lastLocation),
            context.getString(R.string.reminder_copy_location_body)
        )
    }

    // Days-since (guilt-free, curiosity-driven)
    if (daysSinceLast != null && daysSinceLast >= 3) {
        return Pair(
            context.getString(R.string.reminder_copy_days_title, daysSinceLast),
            context.getString(R.string.reminder_copy_days_body)
        )
    }

    // Entry count milestone
    if (entryCount > 0) {
        return Pair(
            context.getString(R.string.reminder_copy_count_title, entryCount),
            context.getString(R.string.reminder_copy_count_body)
        )
    }

    // Brand fallback (new users with no entries)
    return Pair(
        context.getString(R.string.reminder_copy_new_title),
        context.getString(R.string.reminder_copy_new_body)
    )
}

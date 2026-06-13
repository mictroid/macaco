package com.houseofmmminq.macaco.util

/**
 * Builds personalized copy for the journal reminder notification from the user's own data.
 *
 * Priority order (most personal first): last location → days since last entry → entry-count
 * milestone → brand fallback for users with no entries yet.
 */
fun buildNotificationCopy(
    entryCount: Int,
    daysSinceLast: Int?,
    lastLocation: String?
): Pair<String, String> {

    // Location-based (highest priority — most personal)
    if (lastLocation != null) {
        return Pair(
            "Still thinking about $lastLocation? 🌏",
            "Roam Freely. Forget Nothing. Log today."
        )
    }

    // Days-since (guilt-free, curiosity-driven)
    if (daysSinceLast != null && daysSinceLast >= 3) {
        return Pair(
            "Your last memory was $daysSinceLast days ago…",
            "Don't let today become a blur. What's your story?"
        )
    }

    // Entry count milestone
    if (entryCount > 0) {
        return Pair(
            "$entryCount memories and counting 📖",
            "You've been somewhere great. Macaco's waiting."
        )
    }

    // Brand fallback (new users with no entries)
    return Pair(
        "Roam Freely. Forget Nothing. 🐒",
        "Where did Macaco take you today?"
    )
}

package com.houseofmmminq.macaco.ui.navigation

sealed class Screen(val route: String) {
    object JournalList : Screen("journal_list")
    object NewEntry : Screen("new_entry")
    object EntryDetail : Screen("entry_detail/{entryId}") {
        fun createRoute(id: String) = "entry_detail/$id"
    }
    object EditEntry : Screen("edit_entry/{entryId}") {
        fun createRoute(id: String) = "edit_entry/$id"
    }
    object Login : Screen("login")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object Subscription : Screen("subscription")
    object Paywall : Screen("paywall")
    object HelpAbout : Screen("help_about")
    object Onboarding : Screen("onboarding")
    object Adventures : Screen("adventures")
    object Search : Screen("search")
}

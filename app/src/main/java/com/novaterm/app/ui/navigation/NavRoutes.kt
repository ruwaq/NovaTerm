package com.novaterm.app.ui.navigation

import kotlinx.serialization.Serializable

sealed interface NavRoute {
    @Serializable data object Terminal : NavRoute
    @Serializable data object Settings : NavRoute
    @Serializable data object OemGuide : NavRoute
}

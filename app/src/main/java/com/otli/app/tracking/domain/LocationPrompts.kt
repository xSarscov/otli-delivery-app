package com.otli.app.tracking.domain

/** What the courier home should ask of the user next; the UI knows how (system dialogs). */
enum class LocationAction {
    /** Show the system location permission dialog. */
    REQUEST_PERMISSION,

    /** Show the "Turn on location?" dialog of Google Play Services. */
    TURN_ON_SERVICES,
}

/**
 * When to ask the courier for location (F.9). Android apps cannot turn the device location on, only
 * ask, so the decisions are: on going online, ask for the permission and then for the location
 * services; during a delivery, ask once each time the services go off (the status line keeps
 * warning, with a button, so the courier is never nagged repeatedly). Pure state; not thread safe, the
 * caller serialises the events.
 */
class LocationPrompts {
    /** The permission was asked for as part of going online, so its answer continues with the services check. */
    private var setupPending = false
    private var promptedForCurrentEpisode = false

    fun wentOnline(permitted: Boolean, servicesOn: Boolean): LocationAction? = when {
        !permitted -> {
            setupPending = true
            LocationAction.REQUEST_PERMISSION
        }
        !servicesOn -> LocationAction.TURN_ON_SERVICES
        else -> null
    }

    fun permissionChanged(permitted: Boolean, servicesOn: Boolean): LocationAction? {
        if (!setupPending || !permitted) return null
        setupPending = false
        return if (servicesOn) null else LocationAction.TURN_ON_SERVICES
    }

    /** One prompt per episode of [TrackingPlan.ServicesOff]; any other plan ends the episode. */
    fun planChanged(plan: TrackingPlan): LocationAction? {
        val off = plan is TrackingPlan.ServicesOff
        val prompt = off && !promptedForCurrentEpisode
        promptedForCurrentEpisode = off
        return if (prompt) LocationAction.TURN_ON_SERVICES else null
    }
}

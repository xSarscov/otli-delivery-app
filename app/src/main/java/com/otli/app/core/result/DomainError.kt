package com.otli.app.core.result

/** Root of the typed business errors shared by every feature package. */
sealed interface DomainError {
    /** The acting user's role or ownership does not permit the action. */
    data class Unauthorized(val reason: String) : DomainError

    /** The requested state change is not in the allowed transition table. */
    data class InvalidTransition(val from: String, val to: String) : DomainError

    /** The referenced entity does not exist. */
    data class NotFound(val what: String) : DomainError
}

/** Carrier for a [DomainError] inside a failed `Result`, so callers can map it back. */
class DomainException(val error: DomainError) : Exception(error.toString())

package com.clw.aivideotranslator

/**
 * Versioned non-secret HTTP semantics for the durable NVIDIA translation request.
 * Any behavioral change here requires a new ID so persisted request signatures do not
 * silently claim equivalence across different submission semantics.
 */
internal object NvidiaTranslationWireContract {
    const val ID = "nvidia-chat-http-v1"
    const val METHOD = "POST"
    const val ACCEPT_MEDIA_TYPE = "application/json"
    const val REQUEST_MEDIA_TYPE = "application/json; charset=utf-8"
    const val FOLLOW_REDIRECTS = false
    const val RETRY_ON_CONNECTION_FAILURE = false
}

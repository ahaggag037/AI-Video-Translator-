package com.clw.aivideotranslator.provider

import org.json.JSONArray
import org.json.JSONObject

object TranslationResponseClassifier {
    const val MAX_BODY_BYTES = 65_536

    fun classify(
        httpStatus: Int,
        body: String,
        retryAfterMs: Long? = null,
    ): TranslationProviderOutcome {
        if (body.toByteArray(Charsets.UTF_8).size > MAX_BODY_BYTES) {
            return response(httpStatus, ProtocolOutcome.MALFORMED, diagnostic = "BODY_TOO_LARGE")
        }
        when (httpStatus) {
            401 -> return response(httpStatus, ProtocolOutcome.NO_RESPONSE, PolicyOutcome.AUTH_FAILURE)
            403 -> return response(httpStatus, ProtocolOutcome.NO_RESPONSE, PolicyOutcome.PERMISSION_DENIED)
            429 -> return response(
                httpStatus,
                ProtocolOutcome.NO_RESPONSE,
                PolicyOutcome.RATE_LIMIT,
                retryAfterMs = retryAfterMs,
            )
            202 -> {
                val root = parseObjectOrNull(body)
                val requestId = root?.optionalString("requestId") ?: root?.optionalString("id")
                return response(
                    httpStatus,
                    ProtocolOutcome.PENDING,
                    requestId = requestId,
                    diagnostic = if (requestId == null) "PENDING_WITHOUT_REQUEST_ID" else null,
                )
            }
            400, 422 -> return response(httpStatus, ProtocolOutcome.REQUEST_INVALID)
            413 -> return response(httpStatus, ProtocolOutcome.REQUEST_TOO_LARGE)
        }
        if (httpStatus !in 200..299) {
            return TranslationProviderOutcome(
                transport = TransportOutcome.UNKNOWN_AFTER_SUBMISSION,
                protocol = ProtocolOutcome.NO_RESPONSE,
                httpStatus = httpStatus,
                diagnosticCode = "REMOTE_OUTCOME_UNCERTAIN",
            )
        }
        val root = parseObjectOrNull(body)
            ?: return response(httpStatus, ProtocolOutcome.MALFORMED, diagnostic = "INVALID_JSON")
        val choices = root.optJSONArray("choices")
            ?: return response(httpStatus, ProtocolOutcome.MALFORMED, diagnostic = "MISSING_CHOICES")
        if (choices.length() == 0) {
            return response(httpStatus, ProtocolOutcome.MALFORMED, diagnostic = "EMPTY_CHOICES")
        }
        val choice = (0 until choices.length())
            .mapNotNull { choices.optJSONObject(it) }
            .firstOrNull { it.optJSONObject("message") != null }
            ?: return response(httpStatus, ProtocolOutcome.MALFORMED, diagnostic = "MISSING_MESSAGE")
        val message = choice.getJSONObject("message")
        val requestId = root.optionalString("requestId") ?: root.optionalString("id")
        val resolvedModel = root.optionalString("model")
        val finishReason = choice.optionalString("finish_reason")

        val role = message.optionalString("role")
        if (role != null && role != "assistant") {
            return response(httpStatus, ProtocolOutcome.MALFORMED, requestId = requestId, resolvedModel = resolvedModel,
                finishReason = finishReason, diagnostic = "UNEXPECTED_ROLE")
        }
        if (message.has("tool_calls") && !message.isNull("tool_calls")) {
            val toolCalls = message.opt("tool_calls")
            if (toolCalls !is JSONArray || toolCalls.length() > 0) {
                return response(httpStatus, ProtocolOutcome.MALFORMED, requestId = requestId, resolvedModel = resolvedModel,
                    finishReason = finishReason, diagnostic = "UNEXPECTED_TOOL_CALL")
            }
        }
        if (!message.optionalString("refusal").isNullOrBlank()) {
            return response(httpStatus, ProtocolOutcome.NO_RESPONSE, PolicyOutcome.PROVIDER_REFUSAL,
                requestId = requestId, resolvedModel = resolvedModel, finishReason = finishReason)
        }
        if (finishReason == "content_filter") {
            return response(httpStatus, ProtocolOutcome.NO_RESPONSE, PolicyOutcome.CONTENT_FILTER,
                requestId = requestId, resolvedModel = resolvedModel, finishReason = finishReason)
        }
        if (finishReason != null && finishReason !in setOf("stop", "length")) {
            return response(httpStatus, ProtocolOutcome.MALFORMED, requestId = requestId, resolvedModel = resolvedModel,
                finishReason = finishReason, diagnostic = "UNKNOWN_FINISH_REASON")
        }

        val extracted = extractText(message.opt("content"))
        if (extracted.refusal) {
            return response(httpStatus, ProtocolOutcome.NO_RESPONSE, PolicyOutcome.PROVIDER_REFUSAL,
                requestId = requestId, resolvedModel = resolvedModel, finishReason = finishReason)
        }
        if (extracted.unsupportedPart) {
            return response(httpStatus, ProtocolOutcome.MALFORMED, requestId = requestId, resolvedModel = resolvedModel,
                finishReason = finishReason, diagnostic = "UNSUPPORTED_CONTENT_PART")
        }
        val text = extracted.text
        if (text.isBlank()) {
            return response(httpStatus, ProtocolOutcome.EMPTY, requestId = requestId, resolvedModel = resolvedModel,
                finishReason = finishReason, diagnostic = "BLANK_TEXT")
        }
        if (finishReason == "length") {
            return TranslationProviderOutcome(
                transport = TransportOutcome.RESPONSE_RECEIVED,
                protocol = ProtocolOutcome.TRUNCATED,
                contentValidation = ContentValidationOutcome.STRUCTURAL_FAILURE,
                candidateText = text,
                httpStatus = httpStatus,
                requestId = requestId,
                resolvedModel = resolvedModel,
                finishReason = finishReason,
                diagnosticCode = "TRUNCATED",
            )
        }
        return TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.CANDIDATE,
            contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
            candidateText = text,
            httpStatus = httpStatus,
            requestId = requestId,
            resolvedModel = resolvedModel,
            finishReason = finishReason,
        )
    }

    fun transportFailure(mayHaveBeenSubmitted: Boolean): TranslationProviderOutcome =
        TranslationProviderOutcome(
            transport = if (mayHaveBeenSubmitted) TransportOutcome.UNKNOWN_AFTER_SUBMISSION
                else TransportOutcome.DEFINITELY_NOT_SUBMITTED,
            protocol = ProtocolOutcome.NO_RESPONSE,
            diagnosticCode = if (mayHaveBeenSubmitted) "REMOTE_OUTCOME_UNCERTAIN" else "TRANSPORT_BEFORE_SUBMISSION",
        )

    private fun response(
        httpStatus: Int,
        protocol: ProtocolOutcome,
        policy: PolicyOutcome = PolicyOutcome.ALLOWED,
        requestId: String? = null,
        resolvedModel: String? = null,
        finishReason: String? = null,
        retryAfterMs: Long? = null,
        diagnostic: String? = null,
    ) = TranslationProviderOutcome(
        transport = TransportOutcome.RESPONSE_RECEIVED,
        protocol = protocol,
        policy = policy,
        httpStatus = httpStatus,
        requestId = requestId,
        resolvedModel = resolvedModel,
        finishReason = finishReason,
        retryAfterMs = retryAfterMs,
        diagnosticCode = diagnostic,
    )

    private data class ExtractedText(
        val text: String,
        val refusal: Boolean = false,
        val unsupportedPart: Boolean = false,
    )

    private fun extractText(content: Any?): ExtractedText = when (content) {
        is String -> ExtractedText(content)
        is JSONObject -> {
            val type = content.optString("type")
            if (type == "refusal") ExtractedText("", refusal = true)
            else {
                val text = content.opt("text")
                if (text is String && (type.isBlank() || type == "text" || type == "output_text")) {
                    ExtractedText(text)
                } else ExtractedText("", unsupportedPart = true)
            }
        }
        is JSONArray -> {
            val text = StringBuilder()
            var refusal = false
            var unsupported = false
            for (index in 0 until content.length()) {
                when (val part = content.opt(index)) {
                    is String -> text.append(part)
                    is JSONObject -> {
                        val type = part.optString("type")
                        if (type == "refusal") refusal = true
                        else {
                            val value = part.opt("text")
                            if (value is String && (type.isBlank() || type == "text" || type == "output_text")) {
                                text.append(value)
                            } else unsupported = true
                        }
                    }
                    else -> unsupported = true
                }
            }
            ExtractedText(text.toString(), refusal, unsupported)
        }
        else -> ExtractedText("", unsupportedPart = true)
    }

    private fun parseObjectOrNull(body: String): JSONObject? = runCatching { JSONObject(body) }.getOrNull()

    private fun JSONObject.optionalString(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).trim().ifEmpty { null }
}

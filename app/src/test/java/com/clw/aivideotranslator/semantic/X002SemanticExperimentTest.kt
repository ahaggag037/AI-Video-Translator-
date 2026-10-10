package com.clw.aivideotranslator.semantic

import com.clw.aivideotranslator.provider.ContentValidationOutcome
import com.clw.aivideotranslator.provider.PolicyOutcome
import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.provider.TransportOutcome
import org.junit.Assert.*
import org.junit.Test

class X002SemanticExperimentTest {
    private fun source(id: String, text: String) = X002SourceContextUnit(id, text)

    private fun candidate(text: String) = TranslationProviderOutcome(
        transport = TransportOutcome.RESPONSE_RECEIVED,
        protocol = ProtocolOutcome.CANDIDATE,
        candidateText = text,
    )

    @Test fun unitOnlyContainsNoHiddenContextAndIsDeterministic() {
        val target = source("u1", "Hello world.")
        val a = X002SemanticRequestPlanner.plan(target)
        val b = X002SemanticRequestPlanner.plan(target)
        assertEquals(target.sourceText, a.providerUserContent)
        assertTrue(a.beforeContext.isEmpty())
        assertTrue(a.afterContext.isEmpty())
        assertEquals(a.providerRequestSignature, b.providerRequestSignature)
        assertEquals(a.bindingSignature, b.bindingSignature)
    }

    @Test fun boundedSourceContextIsExplicitBoundedAndOutputAffecting() {
        val target = source("u2", "She approved it.")
        val first = X002SemanticRequestPlanner.plan(
            target = target,
            beforeContext = listOf(source("u1", "Maya reviewed the schema.")),
            mode = X002SemanticRequestMode.BOUNDED_SOURCE_CONTEXT,
        )
        val changedText = X002SemanticRequestPlanner.plan(
            target = target,
            beforeContext = listOf(source("u1", "Omar reviewed the schema.")),
            mode = X002SemanticRequestMode.BOUNDED_SOURCE_CONTEXT,
        )
        val changedIdOnly = X002SemanticRequestPlanner.plan(
            target = target,
            beforeContext = listOf(source("other-id", "Maya reviewed the schema.")),
            mode = X002SemanticRequestMode.BOUNDED_SOURCE_CONTEXT,
        )
        assertTrue(first.providerUserContent.contains("SOURCE_CONTEXT_BEFORE_1_UTF8_BYTES="))
        assertTrue(first.providerUserContent.contains("TARGET_UTF8_BYTES="))
        assertNotEquals(first.providerRequestSignature, changedText.providerRequestSignature)
        assertEquals(first.providerRequestSignature, changedIdOnly.providerRequestSignature)
        assertNotEquals(first.bindingSignature, changedIdOnly.bindingSignature)
    }

    @Test fun unitOnlyRejectsHiddenContextAndBoundedModeRejectsOverflow() {
        val target = source("u2", "She approved it.")
        assertThrows(IllegalArgumentException::class.java) {
            X002SemanticRequestPlanner.plan(
                target = target,
                beforeContext = listOf(source("u1", "Context.")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            X002SemanticRequestPlanner.plan(
                target = target,
                beforeContext = listOf(source("u0", "A"), source("u1", "B")),
                mode = X002SemanticRequestMode.BOUNDED_SOURCE_CONTEXT,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            X002SemanticRequestPlanner.plan(
                target = target,
                beforeContext = listOf(source("u1", "x".repeat(X002SemanticRequestPlanner.MAX_CONTEXT_SCALARS_TOTAL + 1))),
                mode = X002SemanticRequestMode.BOUNDED_SOURCE_CONTEXT,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            X002SemanticRequestPlanner.plan(
                target = source("u2", "t".repeat(500)),
                beforeContext = listOf(source("u1", "c".repeat(400))),
                mode = X002SemanticRequestMode.BOUNDED_SOURCE_CONTEXT,
            )
        }
    }

    @Test fun textRequestAndResultContractsContainNoTimingOwnership() {
        val forbidden = listOf("time", "start", "end", "duration", "interval")
        listOf(
            X002SemanticRequest::class.java,
            X002SemanticResultEnvelope::class.java,
            X002SemanticResult::class.java,
        ).forEach { type ->
            type.declaredFields.forEach { field ->
                assertTrue("timing-like field ${type.simpleName}.${field.name}", forbidden.none { field.name.lowercase().contains(it) })
            }
        }
    }

    @Test fun staleResultCannotBeMixedIntoChangedRequest() {
        val target = source("u2", "She approved it.")
        val oldRequest = X002SemanticRequestPlanner.plan(
            target,
            beforeContext = listOf(source("u1", "Maya reviewed it.")),
            mode = X002SemanticRequestMode.BOUNDED_SOURCE_CONTEXT,
        )
        val newRequest = X002SemanticRequestPlanner.plan(
            target,
            beforeContext = listOf(source("u1", "Omar reviewed it.")),
            mode = X002SemanticRequestMode.BOUNDED_SOURCE_CONTEXT,
        )
        val stale = X002SemanticResponseValidator.validate(
            newRequest,
            X002SemanticResultEnvelope("u2", oldRequest.bindingSignature, candidate("وافقت عليه.")),
        )
        assertEquals(X002SemanticResultState.STALE_RESULT, stale.state)
    }

    @Test fun candidateValidationUsesTargetTextNotNeighborContext() {
        val request = X002SemanticRequestPlanner.plan(
            target = source("u2", "Revenue fell by 12.5%, not 125%."),
            beforeContext = listOf(source("u1", "Last year the value was 900.")),
            mode = X002SemanticRequestMode.BOUNDED_SOURCE_CONTEXT,
        )
        val result = X002SemanticResponseValidator.validate(
            request,
            X002SemanticResultEnvelope(
                "u2",
                request.bindingSignature,
                candidate("انخفضت الإيرادات بنسبة 12.5%، وليس 125%.")
            ),
        )
        assertEquals(X002SemanticResultState.ACCEPTABLE, result.state)
        assertFalse(requireNotNull(result.validation).warnings.contains("NUMBER_FACT_MISMATCH"))
    }

    @Test fun malformedTruncatedPendingAndUnknownTransportNeverAdopt() {
        val request = X002SemanticRequestPlanner.plan(source("u1", "Hello world."))
        fun state(outcome: TranslationProviderOutcome) = X002SemanticResponseValidator.validate(
            request,
            X002SemanticResultEnvelope("u1", request.bindingSignature, outcome),
        ).state

        assertEquals(X002SemanticResultState.NO_CANDIDATE, state(TranslationProviderOutcome(TransportOutcome.RESPONSE_RECEIVED, ProtocolOutcome.MALFORMED)))
        assertEquals(X002SemanticResultState.NO_CANDIDATE, state(TranslationProviderOutcome(TransportOutcome.RESPONSE_RECEIVED, ProtocolOutcome.TRUNCATED, candidateText = "partial")))
        assertEquals(X002SemanticResultState.PENDING, state(TranslationProviderOutcome(TransportOutcome.RESPONSE_RECEIVED, ProtocolOutcome.PENDING)))
        assertEquals(X002SemanticResultState.NO_CANDIDATE, state(TranslationProviderOutcome(TransportOutcome.UNKNOWN_AFTER_SUBMISSION, ProtocolOutcome.NO_RESPONSE)))
    }

    @Test fun providerPolicyStructuralFailureAndProfileChangesFailClosedOrChangeIdentity() {
        val target = source("u1", "Hello world.")
        val request = X002SemanticRequestPlanner.plan(target)
        val policyBlocked = X002SemanticResponseValidator.validate(
            request,
            X002SemanticResultEnvelope(
                "u1",
                request.bindingSignature,
                candidate("مرحبًا بالعالم.").copy(policy = PolicyOutcome.PROVIDER_REFUSAL),
            ),
        )
        val structural = X002SemanticResponseValidator.validate(
            request,
            X002SemanticResultEnvelope(
                "u1",
                request.bindingSignature,
                candidate("مرحبًا بالعالم.").copy(contentValidation = ContentValidationOutcome.STRUCTURAL_FAILURE),
            ),
        )
        assertEquals(X002SemanticResultState.NO_CANDIDATE, policyBlocked.state)
        assertEquals(X002SemanticResultState.NO_CANDIDATE, structural.state)

        val changedModel = X002SemanticRequestPlanner.plan(
            target,
            profile = X002SemanticRequestPlanner.SHADOW_PROFILE.copy(model = "synthetic/model-v2"),
        )
        val changedTemperature = X002SemanticRequestPlanner.plan(
            target,
            profile = X002SemanticRequestPlanner.SHADOW_PROFILE.copy(temperature = 1),
        )
        assertNotEquals(request.providerRequestSignature, changedModel.providerRequestSignature)
        assertNotEquals(request.providerRequestSignature, changedTemperature.providerRequestSignature)
    }

    @Test fun batchMapperReportsMissingExtraDuplicatesAndPreservesRequestOrder() {
        val r1 = X002SemanticRequestPlanner.plan(source("u1", "Hello."))
        val r2 = X002SemanticRequestPlanner.plan(source("u2", "Thanks."))
        val mapped = X002SemanticBatchMapper.map(
            requests = listOf(r1, r2),
            envelopes = listOf(
                X002SemanticResultEnvelope("u1", r1.bindingSignature, candidate("مرحبًا.")),
                X002SemanticResultEnvelope("u1", r1.bindingSignature, candidate("أهلًا.")),
                X002SemanticResultEnvelope("u3", "foreign", candidate("زائد.")),
            ),
        )
        assertTrue("DUPLICATE_RESULT_UNIT:u1" in mapped.errors)
        assertTrue("MISSING_RESULT:u2" in mapped.errors)
        assertTrue("EXTRA_RESULT:u3" in mapped.errors)
        assertFalse(mapped.complete)
        assertTrue(mapped.orderedResults.isEmpty())

        val complete = X002SemanticBatchMapper.map(
            requests = listOf(r2, r1),
            envelopes = listOf(
                X002SemanticResultEnvelope("u1", r1.bindingSignature, candidate("مرحبًا.")),
                X002SemanticResultEnvelope("u2", r2.bindingSignature, candidate("شكرًا.")),
            ),
        )
        assertEquals(listOf("u2", "u1"), complete.orderedResults.map { it.unitId })
    }

    @Test fun sameWireTextForDifferentUnitsStillHasDistinctBindingFence() {
        val a = X002SemanticRequestPlanner.plan(source("u1", "Same text."))
        val b = X002SemanticRequestPlanner.plan(source("u2", "Same text."))
        assertEquals(a.providerRequestSignature, b.providerRequestSignature)
        assertNotEquals(a.bindingSignature, b.bindingSignature)
        val result = X002SemanticResponseValidator.validate(
            b,
            X002SemanticResultEnvelope("u2", a.bindingSignature, candidate("النص نفسه.")),
        )
        assertEquals(X002SemanticResultState.STALE_RESULT, result.state)
    }
}

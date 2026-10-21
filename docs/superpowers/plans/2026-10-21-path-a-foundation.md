# Path A Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the safe productization foundation for the final Android app without rewriting the existing durable engine.

**Architecture:** Preserve the current session/provider/media engine, harden STT transport against hidden replay, then add a pure route/reopen layer and a thin final app shell over the same durable truth. Source replacement is converted later in this plan from immediate durable activation to Candidate → Probe → explicit activation.

**Tech Stack:** Kotlin, Android 29–36, Jetpack Compose/Material 3, OkHttp 5.3.2, existing durable session store and Android instrumentation suite.

**Spec:** `docs/contracts/PATH_A_DECISIONS.md`

## Global Constraints

- Working branch: `build/path-a-productize`.
- Preserve current durable session/provider semantics unless a failing test proves a required change.
- No provider call from Composables.
- No blind retry after durable SENT.
- No new bottom navigation.
- Do not rewrite session persistence: `TranslationSessionStore` and `ActiveSessionRegistry` already use atomic writes and durable schema/version boundaries.
- Existing Android CI (`testDebugUnitTest`, `lintDebug`, `assembleDebug`) must stay green on every completed task.
- Do not claim X001/X002/X003/X004/X006 PASS from CI.

## Review Focus

1. STT transport must not replay a POST internally after the app has already durably marked it SENT.
2. Reopen routing must never auto-submit provider work and must prioritize unresolved SENT over source/credential convenience routing.
3. Navigation must not become a second semantic source of truth beside the durable session.
4. The old engineering screen must remain reachable until its functionality has been replaced by product routes; do not delete it prematurely.
5. Picking a second video while a first candidate is probing must not activate stale probe results or replace current durable work.

---

### Task 1: Harden STT HTTP replay policy

**Files:**
- Modify: `app/src/main/java/com/clw/aivideotranslator/NvidiaSttWireContract.kt`
- Modify: `app/src/main/java/com/clw/aivideotranslator/NvidiaSttClient.kt`
- Modify/Test: `app/src/test/java/com/clw/aivideotranslator/NvidiaSttWireContractTest.kt`

**Interfaces:**
- Produces: a single testable STT `OkHttpClient` construction path whose redirect and connection-retry behavior is fixed by versioned wire-contract constants.

- [ ] **Step 1: Write failing unit assertions**

Extend `NvidiaSttWireContractTest` with a test named `httpClientDisablesRedirectsAndConnectionRetry` asserting the client used by `NvidiaSttClient` has `followRedirects == false` and `retryOnConnectionFailure == false`.

- [ ] **Step 2: Run unit CI and verify RED**

Push only the test change. Android CI must fail because the client policy is not yet exposed/configured as required.

- [ ] **Step 3: Implement minimal production change**

Add versioned constants `FOLLOW_REDIRECTS=false` and `RETRY_ON_CONNECTION_FAILURE=false` to `NvidiaSttWireContract`; make `NvidiaSttClient` build its private client through an `internal fun buildHttpClient(): OkHttpClient` using the existing timeouts plus those two constants.

Do not change endpoint, model, language, request body, timeout values, parser, durable SENT timing, or result classification in this task.

- [ ] **Step 4: Run Android CI and verify GREEN**

Expected: unit tests, lint, and `assembleDebug` pass.

- [ ] **Step 5: Commit**

`provider: disable hidden STT transport retries and redirects`

---

### Task 2: Add pure route and reopen resolver

**Files:**
- Create: `app/src/main/java/com/clw/aivideotranslator/app/nav/Route.kt`
- Create: `app/src/main/java/com/clw/aivideotranslator/app/resume/ReopenFacts.kt`
- Create: `app/src/main/java/com/clw/aivideotranslator/app/resume/ReopenResolver.kt`
- Create/Test: `app/src/test/java/com/clw/aivideotranslator/app/resume/ReopenResolverTest.kt`

**Interfaces:**
- Consumes: durable facts only; no Android classes.
- Produces: `fun ReopenResolver.resolve(facts: ReopenFacts): Route`.

- [ ] **Step 1: Write failing table tests**

Cover at least: unreadable session; no session; unresolved provider; interrupted export; active local processing; credential required; source missing; matching validated result; edit-ready text; active source with no accepted text.

Each case must assert the precedence frozen in `PATH_A_DECISIONS.md`.

- [ ] **Step 2: Run unit CI and verify RED**

Expected: resolver/types missing.

- [ ] **Step 3: Implement the minimal pure model/resolver**

`Route` is a sealed interface containing only route identities needed by the resolver. `ReopenFacts` contains booleans/enum facts, not session-store objects. Resolver is deterministic and side-effect free.

- [ ] **Step 4: Run CI and verify GREEN**

- [ ] **Step 5: Commit**

`app: add deterministic reopen route resolver`

---

### Task 3: Add final app root, theme and Home without deleting engineering UI

**Files:**
- Create: `app/src/main/java/com/clw/aivideotranslator/app/AppRoot.kt`
- Create: `app/src/main/java/com/clw/aivideotranslator/ui/theme/QuietProTheme.kt`
- Create: `app/src/main/java/com/clw/aivideotranslator/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/clw/aivideotranslator/ui/devconsole/EngineeringConsole.kt`
- Modify: `app/src/main/java/com/clw/aivideotranslator/MainActivity.kt`
- Add/modify tests as required for pure mapping logic.

**Interfaces:**
- `MainActivity` remains the Activity owner for the existing `TranslationSessionViewModel` during migration.
- `AppRoot` receives durable UI state and renders Home or the temporary engineering console route.

- [ ] **Step 1: Extract pure Home-state mapping test first**

Create a JVM test for mapping current durable source/STT/translation facts to Home state: Empty, ActiveReady, ActiveProcessing, ActionUnknownProvider, ActionSourceMissing.

- [ ] **Step 2: Verify RED in CI**

- [ ] **Step 3: Implement theme tokens + Home + thin routing shell**

Use the Quiet Pro Media light palette and RTL root. Keep the engineering UI as a temporary internal destination so no current capability disappears during migration.

Do not alter provider/session/export behavior.

- [ ] **Step 4: Verify GREEN in CI**

- [ ] **Step 5: Commit**

`ui: introduce Quiet Pro app shell and Home`

---

### Task 4: Convert source selection to Candidate → Probe → Activate

**Files:**
- Create: `app/src/main/java/com/clw/aivideotranslator/source/CandidateSourceState.kt`
- Create: `app/src/main/java/com/clw/aivideotranslator/source/CandidateSourceCoordinator.kt`
- Modify: source-selection entry points in `MainActivity`/migrated Home/Source Review flow
- Reuse: existing `VideoProbe`, `SourceAttachmentBuilder`, `TranslationSessionViewModel`/session coordinator until replacement boundaries are explicit
- Test: JVM state-machine tests plus existing source instrumentation tests

**Interfaces:**
- Produces typed states: Idle, Probing(generation), Ready(generation,...), NoAudio, AccessLimited, Invalid, Activating, Failed.
- Durable session replacement occurs only from a current Ready candidate after explicit activation/replace confirmation.

- [ ] **Step 1: Write failing generation-fencing tests**

Test that candidate B supersedes candidate A while A is probing; late A result is ignored; activation failure leaves previous active session unchanged.

- [ ] **Step 2: Verify RED**

- [ ] **Step 3: Implement minimal candidate coordinator**

Move persistable permission acquisition and durable session activation to activation time. Probe must not mutate durable session truth.

- [ ] **Step 4: Run unit CI + existing source/session instrumentation workflow where available**

- [ ] **Step 5: Commit**

`source: probe candidates before atomic durable activation`

---

## Self-review rulings

- The external review's proposed first task (adding AtomicFile/schema/active pointer) is skipped because repository inspection proves those mechanisms already exist. Duplicating them would add risk without closing a real gap.
- The external review's EventListener-defined SENT boundary is not in this foundation plan. The current SENT-before-transport behavior is deliberately conservative. Task 1 closes the actual hidden-replay risk without weakening duplicate safety.
- Full Processing/Editor/SRT/Presentation/MP4/Style/Recovery productization will be separate plans after this foundation is green, preserving vertical-slice reviewability.

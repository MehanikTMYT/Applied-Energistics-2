# COLONIAL & AE FLEET — Round 12 Preliminary Report

Date: **19 AUG 2026**  
Status before post-review: **IMPLEMENTED / VALIDATED / ADVERSARIAL REVIEW PENDING**

## A. Baseline

- Repository: `/root/home/forks/ae-stack/core/Applied-Energistics-2`
- Branch/HEAD: `main` / `45f315517ea346efc0babd02c85c6b9d32dc8acf`
- Minecraft / NeoForge: 26.1.2 / 26.1.2.21-beta
- Initial worktree: 650 entries (48 modified, 602 untracked), staged 0, QNB status empty, diff-check clean.
- Forced baseline: 837 JUnit cases, 0 failures/errors, 1 existing skip; 79/79 GameTests; build PASS.

## B. Recovered Round 11 State

The canonical manifest, report, raw/synthesis artifacts and job references were recovered from this directory. Round 11 has one persisted topology authority, runtime-only presence and status, an existing-intent-only Router repair action, no exact coordinates client-side and no Router/player requirement for reconstruction. The multiple-controller proof was the one concrete deferred adversarial item.

## C. Multiple Controller Pre-Gate

The new production GameTest `routing_controller_multiple_same_profile` proves two real Controllers resolve one secured Profile, see the same derived status, repair the same intent idempotently, survive removal/re-placement of either Controller, and never own or tear down the healthy transport. This gate passed before HUB implementation.

## D–H. Archaeology, Reviews and Chosen Architecture

Archaeology found that `QuantumLane` and the registry/reconciler already support arbitrary undirected sparse edges. PAIR assumptions were confined to fresh pair provisioning and the old pair DTO. The Round 12 Codex pre-review compared one-shot exact edges, persistent policy and UI-template models. Aggregator job `2cb2734e-a39e-479e-9ca1-90109ceeb6ec` produced valid Qwen/Gemini reviews and a contaminated stale ChatGPT Free artifact, which was excluded rather than replaced.

Chosen model: HUB is an explicit, incremental **join this fresh member to this explicit anchor** Q4 operation. Each successful request atomically commits one endpoint plus one exact anchor-member edge. `QuantumTopologyRegistry` exact edges remain the only persisted topology truth. There is no HUB mode, member registry, policy graph or authoritative anchor field. A complete star is the set of individually accepted user mutations, not a falsely advertised all-members batch.

## I–K. Authority, Shape and Permissions

- Identity and exact intent: `QuantumTopologyRegistry`.
- Credential/binding/revision/state: `QuantumCredentialRegistry` / `QuantumCredentialService`.
- Security: existing security registry/service.
- Presence: runtime-only `QuantumEndpointOnlineTable`.
- Materialization: live `GridConnection` through the existing materializer/pathing stack.
- Status: derived runtime projection only.
- Shape: explicit star, N endpoints and N-1 exact edges; no alternative/member-member edge.
- Authoring: physical endpoint activation with member credential in main hand and already-bound explicit anchor credential in offhand; server validates SECURED Profile and BUILD. Cards identify requests but never become authoritative.
- Router: QUANTUM_VIEW observability and QUANTUM_ROUTE repair of existing intent only; no CREATE/DELETE authority.

## L–M. Mk II and Mixed Capacity

The Mk II endpoint is a parameterized variant of the existing physical endpoint, not a copied transport framework. Its managed node uses existing `CapacityTier.QUANTUM` (64). Mk I remains STANDARD (32). Real `PathingService` tests prove Mk II↔Mk II 64, Mk I↔Mk II 32, Standard P2P-like upstream 32, downstream Standard Dense 32 and an already-used 47/64 upstream leaving 17. HUB creates neither a channel pool nor capacity.

## N. Production Changes

Notable Round 12 production files:

- `QuantumCredentialService`: validated/rollback-safe `activateFreshHubMember`.
- `QuantumEndpointBlock` / `QuantumEndpointBlockEntity`: explicit two-card member join and per-block CapacityTier.
- `AEBlockIds`, `AEBlocks`, `AEBlockEntities`: Mk II registration.
- `QuantumRoutingService`: generic endpoint/edge/lane runtime projection and arbitrary-lane existing-intent repair.
- Routing Controller BE/menu/status packet/screen: multipoint counts and per-edge summaries without BlockPos.
- Model/recipe/lang/generated resources: Mk II block/item, loot, recipe and EN/RU names. Visual currently reuses the quantum-ring model: **HUMAN VISUAL DESIGN PENDING**.

## O–R. Security, Credential, Restart and Router Lifecycle

All HUB authoring validation precedes mutation. If delegated credential activation fails after edge insertion, the newly added edge and fresh endpoint are rolled back. Wrong profile and cloned/bound credential tests leave no orphan topology. Suspend/revoke dematerializes only incident live arms; unrelated arms and exact intent survive. Resume materializes the same intent. Cross-domain materialization rejects only the bad arm and never merges grids. Anchor/member absence preserves exact intent and never rewires. SavedData disk round-trip retains the exact star with no member-member alternatives. Reconstruction needs neither Router nor actor.

The Router now reports endpoint count, intended/materialized edge counts and edge-local states for an arbitrary lane. It persists nothing, exposes no exact coordinate, and repeated repair remains idempotent.

## S. Complexity

Registry membership is map-backed; HUB authoring adds one edge per join; full status and reconciliation are O(V+E). A structural test proves 100 endpoints produce 99 edges, not 4,950. There is no per-tick world scan or pair matrix.

## T–V. Tests, Build and Smoke

- Forced JUnit after post-review hardening: **852 cases; 851 passed, 1 existing skipped, 0 failures/errors** (baseline 837 cases; +15 Round 12 cases).
- GameTests: **85/85 PASS** on final retry (baseline 79; +6: multiple-controller plus five HUB production cases).
- Earlier 85-test run: unrelated `fe_charger` timeout, 84/85; Round 12 tests passed in both runs. Recorded as 1/2 observed flake, not hidden.
- Build including Spotless/resource validation: **PASS**.
- `git diff --check`: clean.
- Dedicated server: reached `Done (0.714s)`; Gradle did not forward `stop`, so terminated with SIGINT after Done.
- Client: loaded a live NeoForge/Minecraft window and completed a second F3+T resource reload; graceful Alt-F4 exit. Environmental OpenAL device failure disabled audio. Root-session XScreenSaver blocked framebuffer inspection, so world/GUI interaction and human visual confirmation are not claimed.
- Known nonfatal baseline warning: Jade loot-table registry collection during bootstrap.

## W. Git Hygiene

No stage, commit, push, reset, clean, restore, checkout or stash was performed. The one formatter invocation was checksum-audited and changed exactly five Round 12 Java files named by Spotless. QNB source/status remains untouched. Final staged/status audit is pending after artifacts.

## X. Deferred

MESH, automatic failover/anchor replacement, alternate routes, Mk III, Mk IV, final power economy, movement/rebind, graphical/global topology UI and final Mk II visual design remain out of scope.

Post-review hardening added an anchor-revoke regression: both incident live arms disappear, while all member identities, exact edges and healthy member credential states remain unchanged.

## Y. Preliminary Verdict

**ROUND 12 PRODUCTION HUB / MK II — PRELIMINARILY PROVEN WITH LIMITATIONS:** server, deterministic production GameTests and client resource reload are proven; interactive client-world and human visual validation are pending. Independent post-review completed with no architecture issue and one narrow hardening regression applied.

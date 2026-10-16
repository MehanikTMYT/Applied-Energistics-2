# COLONIAL & AE FLEET — MANIFEST

Current as of: **19 AUG 2026**  
Repository: `/root/home/forks/ae-stack/core/Applied-Energistics-2`  
Branch / HEAD: `main` / `45f315517ea346efc0babd02c85c6b9d32dc8acf`  
Minecraft / NeoForge: `26.1.2` / `26.1.2.21-beta`

This is the canonical local Markdown reconstructed from the owner-provided current context plus the prior aggregator task artifacts. The previous canonical manifest was available only as Claude remote artifact `4b312c92-f403-4f50-b673-b19a4af3e07b`; no local source or authenticated remote update mechanism was present. Historical claims are retained as history and corrected explicitly below.

## Current evidence summary

- JUnit: **852 cases: 851 PASS, 1 existing skipped, 0 failures/errors** (forced `cleanTest test`).
- GameTests: **85 / 85 PASS**.
- Build / Spotless: **PASS**.
- `git diff --check`: clean at final check.
- Staged / commits / pushes across this work: **0 / 0 / 0**.
- Dirty tree is intentional and preserved; Round 12 did not clean, reset, stash, stage, commit, or push.
- Vanilla AE2 Quantum Network Bridge (QNB) is a separate system and remains untouched.

## Security foundation

Core permissions remain INJECT, EXTRACT, CRAFT, BUILD and SECURITY, with SECURITY as super-admin. Quantum permissions are QUANTUM_VIEW, QUANTUM_ISSUE, QUANTUM_REWRITE, QUANTUM_REVOKE and QUANTUM_ROUTE. SECURED networks default-deny; owner has full access; EVERYONE is explicit; DEVICE never inherits owner. LEGACY remains vanilla-open for existing AE2 behavior.

SecurityDomainId and NetworkProfileId are distinct. Two independently SECURED grids with different domains are rejected before merge in GridConnection; CONFLICT remains defense-in-depth. SECURED + LEGACY may merge transiently without permanently infecting the legacy fragment. BUILD means AE2 network construction/configuration, not world claims or anti-grief.

## Network slots and Quantum foundations

- Network Slot co-location isolation: proven for storage, crafting, security, channels, removal/re-add and restart.
- Q1 remote lane: proven using existing `GridHelper.createConnection`; no parallel Quantum IGrid.
- Q2 sparse multipoint primitive: proven (e.g. A↔B↔C with N−1 edges). MESH never implies O(N²) physical connections.
- Q3 identity/intent: typed NetworkProfileId, QuantumLaneId, QuantumEndpointId; BlockPos is not identity. Exact sparse edges persist in QuantumTopologyRegistry. Intent is not GridConnection.
- Q4 credentials: separate QuantumCredentialId, registry-authoritative revision/state/binding, card non-authoritative, clone conflict, explicit authorized rebind only. Restart reconstruction is trusted internal and actor-independent.

## Channel and P2P invariant

Quantum and P2P transport never generate channel capacity. Effective capacity is the minimum capacity-bearing element along the full path. CapacityTier is the authoritative tier vocabulary:

| Tier | Smart | Dense | Controller face | ME P2P |
|---|---:|---:|---:|---:|
| Standard | 8 | 32 | 32 | 32 |
| Quantum | 16 | 64 | 64 | 64 |
| Singularity | 32 | 128 | 128 | 128 |
| Cosmic | 64 | 256 | 256 | 256 |

Glass remains 4 by intentional Round 9 rebalance. Mixed tiers bottleneck correctly. Controller multiblocks are tier-homogeneous. Recursive ME P2P remains allowed with owner-identity cycle detection and no arbitrary nesting cap.

## Round 7 — historical foundation

Security/domain and physical network-slot foundations established the authority and isolation rules used by later Quantum work. Historical GUI polish and older machine BUILD-hook backlog remain separate from Quantum routing.

## Round 8 — high-capacity infrastructure

CapacityTier and mixed-path minimum semantics were introduced and proven across cables/controllers. No second tier enum is permitted without new evidence.

## Round 9 — Q3/Q4 identity and credential foundation

Persistent exact-edge topology intent, typed IDs, credential authority, Key Imprinter vertical slice, and recursive P2P adversarial coverage were established. The Imprinter is profile-local, not a global browser or identity owner.

## Post-Round-9 correction

Round 9 descriptions that implied all physical actor-aware topology creation was already closed were too optimistic. Round 10 added the real BUILD-gated physical endpoint activation path.

## Round 10 — Production Quantum PAIR Mk I

Round 10 added `QuantumEndpointBlock` / `QuantumEndpointBlockEntity`, one managed node with `CapacityTier.STANDARD`, and the physical chain credential → endpoint identity → lane/profile → physical node → exact edge intent → reconciled GridConnection. Mk I transport ceiling is 32. Storage/crafting, restart, reverse load order, missing peer return, cross-domain rejection, credential failures, recursive P2P interop and full-path saturation were proven. Round 10 was the first production PAIR vertical slice, **not a finished Quantum Backbone**.

Historical validation after initial Round 10: 793 JUnit, 74 GameTests.

## Post-Round-10 correction — orphan lifecycle CLOSED

Any older summary saying orphaned QuantumEndpointRecord is DEFERRED is obsolete.

`QuantumTopologyRegistry#discardUnactivatedEndpoint()` rolls back a fresh incomplete registration and frees its lane slot. `QuantumCredentialService.activateFreshPhysicalEndpoint()` invokes it for every non-OK delegated activation result. The strengthened clone test proves no second endpoint, no orphan record, and legitimate slot reuse. Status: **CLOSED**.

## Round 11 — Routing Controller Foundation (PAIR-only)

Round 11 is proven within a deliberately narrow control-plane scope:

- Production Routing Controller block/BE/menu/screen/assets.
- One SECURED profile binding from the controller's physical network/domain; LEGACY/disconnected/ambiguous is UNBOUND.
- Profile-local lane/endpoint view with short IDs, dimensions, online flags, exact intent presence, live materialization, derived state/reason.
- No exact BlockPos is sent to the client.
- `QuantumEndpointOnlineTable` remains the single runtime-only presence index.
- `QuantumRoutingService` derives operational state; it persists nothing.
- `QuantumRouteMaterializer` bridges exact intent to the exact current endpoint-node virtual connection and supports router-independent security teardown.
- QUANTUM_VIEW gates status. QUANTUM_ROUTE gates **repair of already-persisted intent only**.
- Router unload/destruction does not delete identity, credential or intent and does not tear down a healthy PAIR.
- Credential suspend/revoke immediately dematerializes live transport without Router/player; resume/restart only reuses existing intent through trusted reconciliation.

### Round 11 narrowing correction

The pre-existing Round 11 tree exposed Controller CREATE/DELETE operations under QUANTUM_ROUTE. Codex archaeology found this contradicted the approved Round 11 rule and bypassed the existing BUILD/Q4 topology-creation boundary. The Controller BE/menu/screen create/delete surfaces were removed; service test helpers were reduced to package-private with no production caller. The production Controller now observes and repairs existing intent; it does not author topology.

The replacement production GameTest proves: exact intent exists → runtime connection is deliberately absent → status reports intent but not materialization → authorized repair restores the same edge and remote storage → repeated repair leaves one connection.

Final Round 11 validation: **837 JUnit / 79 GameTests / build PASS**.

## External review — Round 11

Pre-review job `76871e7d-3a25-4814-ad18-fa3180d67559`: Qwen/Gemini/Codex agreed on one topology authority, runtime-only status/presence, no exact-coordinate VIEW, non-SPOF Router, and existing-intent-only repair. LEGACY disagreement was resolved in favor of the narrower UNBOUND behavior. ChatGPT Free produced a relevant GUI response but its stored artifact was contaminated by stale Round-10 conversation state.

Post-review job `cc7844c7-45c8-4ee3-a6ca-20af051bb7ba`: Qwen and Gemini returned ACCEPT WITH HARDENING and no architecture issue. Requested 100-cycle proofs already exist and pass; static scan confirms no production create/remove caller. A multiple-controller production GameTest remains a useful future proof, not a demonstrated defect. ChatGPT artifact contamination repeated and is not represented as consensus.

## Round 12 — Production HUB / Mk II

Round 12 proves a bounded production multipoint vertical slice:

- HUB is an explicit user-facing/Q4 authoring template, not a persisted topology mode or policy. Each fresh member join names an already-bound explicit anchor and atomically commits exactly one new endpoint plus one exact anchor-member edge.
- Exact `QuantumTopologyRegistry` edges remain the only persisted topology truth. No HUB member registry, authoritative anchor field, second graph, automatic anchor election or alternate edge exists.
- Three real endpoints produce exactly two star edges; N endpoints produce N-1, including 100→99. Anchor/member absence never creates member-member edges. Restart restores only the exact persisted star.
- Server-side SECURED Profile and BUILD/Q4 validation gates authoring. Cards are selectors, not authority. Router remains QUANTUM_VIEW observability plus QUANTUM_ROUTE existing-intent repair and never authors topology.
- Multiple same-profile Controllers converge on the same derived state and idempotent repair; Router unload/destruction remains irrelevant to data-plane/restart authority.
- Mk II reuses the existing endpoint architecture with authoritative `CapacityTier.QUANTUM` and a real 64-channel ceiling. Mk I/Mk II paths bottleneck at 32; upstream/downstream/P2P-style bottlenecks and remaining-capacity 17 remain native PathingService facts. Capacity and HUB semantics are independent axes.
- Status is a runtime-only O(V+E) projection with endpoint/intended/materialized counts and edge-local reasons; no BlockPos disclosure or per-tick world scan.
- Suspend/revoke and cross-domain failures are edge-local. Post-review hardening proves revoking the anchor closes every incident live arm while preserving member identity, exact intent and healthy member credential state.

Final Round 12 validation: **852 JUnit cases (851 pass, 1 existing skip), 85/85 GameTests, build/Spotless/resources PASS**. One earlier unrelated `fe_charger` GameTest timeout passed on retry; Round 12 tests passed in both runs. Dedicated server reached Done; client loaded and completed F3+T resource reload. Interactive client-world and human visual verification remain pending; Mk II currently reuses placeholder quantum-ring art.

### External review — Round 12

Pre-review job `2cb2734e-a39e-479e-9ca1-90109ceeb6ec` selected exact-edge-only template semantics, explicit anchor, BUILD/Q4 authoring and native pathing. Post-review job `0a4884a8-2d02-46d1-a1e2-ebe54265f6c9` returned Qwen/Gemini ACCEPT WITH HARDENING and no architecture issue; the shared anchor-revoke proof request was implemented and fully revalidated. ChatGPT Free produced contaminated stale Round-10 artifacts in both jobs and is explicitly excluded rather than counted as consensus.

## Current limitations / not started

The following are **NOT STARTED** as production features:

- MESH topology;
- automatic failover, alternate-path routing or dynamic optimization;
- Mk III and Mk IV Quantum Endpoints;
- endpoint movement/cross-dimensional rebind workflow;
- final Quantum power balance;
- global topology browser;
- Wireless, Storage and Expansion Suites.

Also deferred independently: Security Station human visual validation, group membership GUI, and the historical machine-config BUILD hooks. None is silently folded into Round 11.

## Final verdict

**ROUND 12 PRODUCTION HUB / MK II — PROVEN WITH LIMITATIONS: deterministic sparse exact-edge HUB and native 64-channel Mk II are proven; interactive client-world/human visual confirmation, MESH, failover and Mk III-IV are not.**

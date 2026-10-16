# Round 12 independent architecture review — production sparse HUB + Mk II

You are an independent architecture reviewer. You do not have repository access. Do not claim you inspected code. Mark every material assertion as **PROVIDED FACT**, **INFERENCE**, **DESIGN RECOMMENDATION**, or **NEEDS REPO VERIFICATION**.

## Provided facts

- Applied Energistics 2 fork, Minecraft 26.1.2 / NeoForge 26.1.2.21-beta.
- Current baseline: 837/837 JUnit, 79/79 GameTests, build PASS. A new pre-gate production GameTest with two Routing Controllers on one SECURED profile passes, taking GameTests to 80/80.
- Separate IDs: NetworkProfileId, QuantumLaneId, QuantumEndpointId, QuantumCredentialId, SecurityDomainId. BlockPos is not identity; cards are not authoritative.
- QuantumTopologyRegistry SavedData is the sole authority for profile/lane/endpoint identity and exact undirected sparse edge intent. A lane already supports arbitrary endpoint count and exact edge list. Reconciler is stateless O(E), load-order tolerant, idempotent, and calls existing GridHelper.createConnection. Runtime presence, materialized GridConnections and operational status are not persistent authority.
- Current production Mk I provisioning is PAIR-specific: BUILD-gated fresh activation finds/creates a lane with fewer than 2 endpoints and auto-adds its one edge when the second endpoint activates. Failed activation rolls back the fresh endpoint.
- Current Routing Controller is SECURED-profile-local, stateless VIEW plus QUANTUM_ROUTE repair of existing intent only. Round 11 deliberately removed Controller topology CREATE/DELETE because QUANTUM_ROUTE alone bypassed the BUILD/Q4 authoring boundary. Router is not data plane and is unnecessary for restart.
- Q3 registry/reconciler can already represent a 3+ endpoint star. Round 11 status/repair validation is pair-hardcoded.
- No current production API atomically authors an explicit N-member HUB. Current credential-card data has no lane/anchor selector.
- CapacityTier is authoritative: STANDARD dense=32, QUANTUM dense=64, SINGULARITY=128, COSMIC=256. Mk I endpoint has one DENSE_CAPACITY STANDARD node. Candidate Mk II reuses the same BE architecture with QUANTUM. Native pathing uses per-node bottlenecks. P2P and Quantum never generate capacity.
- SECURED cross-domain merge is rejected before Grid merge. LEGACY Routing Controller is UNBOUND. Recursive ME P2P remains allowed. Vanilla QNB is separate and must not change.
- Round 12 excludes MESH, automatic failover/anchor replacement, alternate routing, Mk III/IV, optimizer, global browser and final power economy.

## Candidate HUB models to compare

A. One-shot authorized operation validates explicit anchor + members, then atomically persists only N-1 exact anchor-member Q3 edges. No persistent HUB policy survives.

B. Persist logical HUB policy (mode, anchor, members) plus exact edge realization. You must state which is authoritative and how conflicts cannot create split brain.

C. HUB is a user-facing UI/provisioning template implementing A: after the operation only exact edges persist.

D. Any better model you can justify.

Do not assume first activation, registration order, UUID sort, load order or BlockPos may implicitly select the anchor. An anchor must be explicit user intent. Anchor unload/destruction must degrade exact edges without silent rewire. A failed multi-edge authoring operation must leave no partial/orphan topology.

## Questions

1. Which model should Round 12 use, and why?
2. How is the Q3 exact-edge invariant preserved? Can policy be added without duplicated truth? If policy exists, what wins on conflict?
3. Must anchor identity be persisted separately, or are exact star edges sufficient? What happens on anchor unload/destruction? Should replacement ever be automatic now?
4. Who may author HUB topology? How can production UX preserve BUILD/Q4 authorization without restoring QUANTUM_ROUTE-only Controller CREATE?
5. Given current cards lack lane/anchor selection, evaluate narrow production flows: (i) explicit interaction that annotates each issued member credential card with a non-authoritative target anchor/lane hint, revalidated on activation; (ii) a Controller action requiring BOTH BUILD and routing permission and invoking Q4; (iii) another safer minimal flow. The Router must not become authority.
6. Should HUB remain SECURED-only for this round? Is LEGACY HUB necessary?
7. How do topology semantics and Mk II capacity remain independent axes? May Mk I participate in HUB and may tiers mix?
8. For Mk I↔Mk II, should effective edge transport be min of both endpoint capabilities through native pathing, or require a separate transport object? Explain channel-generation risks.
9. How should a star avoid a magical 64×edges capacity pool and preserve upstream/controller/cable/P2P/downstream bottlenecks?
10. Define atomic validation/rollback for N-1 authoring, including duplicate member, anchor-not-member, wrong profile/lane, credential conflict and a failure after some edge writes.
11. Define edge-local failure for one unloaded/revoked/suspended/cross-domain member while healthy edges remain.
12. Show O(N)/O(E), including 100 endpoints → 99 exact edges, not 4950.
13. Name mandatory adversarial 3–5 endpoint and restart/load-order tests.
14. List blocking STOP conditions and what must remain out of scope.

Finish with a verdict: **PROCEED**, **PROCEED WITH CONDITIONS**, or **BLOCK**, and list the exact conditions.

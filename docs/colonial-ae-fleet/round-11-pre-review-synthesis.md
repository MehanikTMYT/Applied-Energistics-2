# COLONIAL & AE FLEET — Round 11 Pre-Review Synthesis

Date: 19 AUG 2026

Primary job: `76871e7d-3a25-4814-ad18-fa3180d67559` (qwen, gemini, chatgpt-free). Gemini required one retry after a truncated extraction. ChatGPT Free required GUI inspection and retry/continue; a correct response was visible in the browser, but the artifact extractor repeatedly saved stale Round-10 conversation text. Supplemental artifact-retry job: `0ddab3db-d555-43f8-ac71-f69d0c3950d6` (failed on a transient/detached send button). This provider artifact defect is retained as evidence rather than hidden.

| Topic | Codex pre-review | Qwen | Gemini | ChatGPT Free (GUI) | Decision |
|---|---|---|---|---|---|
| Router role | Passive control plane | Proceed only if passive/non-SPOF | Same | Same | Passive, stateless forwarder only |
| Edge authority | QuantumTopologyRegistry | Sole authority | Sole authority | Sole authority | No RouteRegistry |
| Presence/status | Runtime projection | Runtime only | Runtime only | Runtime only | Reuse server-scoped online table; derive status |
| Location | Persist last registered location; live presence separate | Persist identity metadata, redact exact coordinates | Persist location, runtime presence | Persist identity/location; do not conflate unload | Existing registry representation, no relocation work |
| QUANTUM_VIEW | No exact BlockPos | No coordinates absent explicit high privilege | No coordinates; suggested REWRITE/owner | No new remote disclosure | Round 11 packet exposes no BlockPos at all |
| LEGACY | Controller UNBOUND | Unbound/degraded; no remote enumeration | UNBOUND | Limited legacy-open diagnostics could be consistent | Choose UNBOUND: narrow privacy-safe rule, explicitly tested |
| Manual repair | Existing intent only | Existing intent only | Existing intent only | Existing intent only | QUANTUM_ROUTE may only re-materialize persisted intent |
| Create/delete intent from Router | Out of scope | Mutations require authoritative actor gate; review emphasizes repair | Describes repair as non-mutating intent | Reconcile must not create edge | Controller route create/delete is outside requested Round 11 and must not be exposed |
| Suspend/revoke live pair | Must dematerialize | Explicit required proof | Explicit required proof | Explicit required proof | Repository must prove immediate teardown without Router |

## Consensus

- Architecture may proceed only with one persisted topology source, no parallel grid/pathing/security/credential authority, router-independent data plane, actor-free restart reconstruction, and server-side permission enforcement.
- Exact coordinates are sensitive and are unnecessary for this Round 11 vertical slice.
- Presence/materialization/status are separate runtime facts; none is persistent authority.
- Suspend/revoke of an already-live pair and repeated idempotent reconstruction are mandatory proofs.

## Disagreements

- LEGACY behavior: ChatGPT Free argued for limited legacy-open diagnostics; Codex, Qwen and Gemini preferred secured binding/unbound behavior. The decision is UNBOUND because it introduces no new remote-information semantics and is the smaller Round 11 scope.
- Exact-coordinate privilege: reviewers suggested owner/full, QUANTUM_REWRITE, or an explicit disclosure grant. The implementation avoids the unresolved distinction by transmitting no BlockPos at all.

## Blind spots and repo-only facts

- Reviewers could not verify endpoint lifecycle hooks, actual GridConnection discrimination, client packet fields, duplicated controllers, QNB isolation, or test quality.
- Repository archaeology found a pre-existing Round 11 implementation and baseline of 837 JUnit / 79 GameTests. It also found controller-visible CREATE/DELETE route actions. That contradicts the requested Round 11 rule that manual reconcile must not create arbitrary intent and topology creation must remain on the existing BUILD/Q4 path. This is a narrow scope/authorization defect, not a need for new architecture.

## Gate decision

`PROCEED WITH NARROWING CONDITION`: the authority model passes. Accept the existing implementation only after removing Routing Controller create/delete intent mutation surfaces (or otherwise proving they are the pre-existing BUILD/Q4 path, which current QUANTUM_ROUTE-only code is not). Repair existing intent remains allowed. No owner decision is required for coordinates because they are not exposed; no owner decision is required for LEGACY because UNBOUND is the privacy-preserving minimal feature.


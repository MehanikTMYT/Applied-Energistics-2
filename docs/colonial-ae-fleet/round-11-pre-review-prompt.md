# Round 11 pre-implementation architecture review

You are one of three independent reviewers. The identical prompt is sent concurrently to Qwen, Gemini, and ChatGPT Free. You cannot see the other answers and have no repository access. Label important claims as FACT FROM PROVIDED MANIFEST, INFERENCE, DESIGN RECOMMENDATION, or NEEDS REPO VERIFICATION. Never claim you inspected code.

## Snapshot

- AE2/NeoForge fork. Q1 remote lane and Q2 sparse multipoint primitive are proven using existing GridHelper/GridConnection, not a parallel grid.
- Q3 persists typed NetworkProfileId, QuantumLaneId, QuantumEndpointId and exact sparse edge intent in QuantumTopologyRegistry. Intent is not a materialized GridConnection.
- Q4 uses separate QuantumCredentialId; registry/service are authoritative, cards are not. Clone cannot bind twice. Post-Round-10 rollback of failed fresh activation is CLOSED (no orphan; slot reuse proven).
- Existing Security Foundation is LEGACY-open, SECURED-default-deny, owner/full, explicit EVERYONE, with QUANTUM_VIEW/ISSUE/REWRITE/REVOKE/ROUTE. Cross-domain secured merge is rejected before GridConnection merge.
- Round 10 physical PAIR Mk I uses a production QuantumEndpoint block, existing CapacityTier.STANDARD ceiling 32, actor-gated activation, trusted actor-free restart reconciliation. 793 JUnit and 74 GameTests were reported after hardening.
- Quantum and P2P never generate channels; full-path capacity is the minimum capacity-bearing element. Recursive ME P2P remains allowed.
- Vanilla AE2 QNB is a separate system and must not be changed.
- Proposed Round 11: PAIR-only Routing Controller foundation, endpoint presence/location/route status, and proof of identity vs intent vs materialized vs operational state. No HUB, MESH, failover, Mk II-IV, relocation, global browser, or final power balance.
- Controller should be control plane, not data-plane hop or authority. Router unload/destroy must preserve identity, credential, intent, and healthy pair; restart remains router/player independent.

## Review questions

A. Is control-plane/data-plane separation sound, and what would make Router a hidden SPOF?
B. Must exact edge intent remain solely in QuantumTopologyRegistry?
C. Is any new persistent RouteRegistry justified, or would it be split-brain?
D. Who owns endpoint presence and operational status; should either persist?
E. Should QuantumEndpointLocation/exact BlockPos persist, and how should unload differ from move/delete?
F. Exact router unload/destroy/reload semantics?
G. Should QUANTUM_VIEW reveal exact coordinates? Is QUANTUM_ROUTE sufficient? Could Controller leak profile data?
H. Should advanced Routing Controller work in LEGACY networks, or remain unbound until a Security Domain exists? Discuss consistency vs remote-information privacy.
I. Define manual reconcile/repair semantics without creating intent, failover, or authority bypass.
J. What must remain out of Round 11?
K. Any blocking flaw in the proposal?
L. Give an exact automated proof matrix (JUnit vs production GameTest), including credential suspend/revoke on an already materialized pair, multiple controllers, 100 repeats, restart without actor, cross-domain, recursive P2P, and capacity bottlenecks.
M. Give explicit STOP conditions.

Also provide a concise ownership table and verdict: PROCEED, PROCEED WITH CONDITIONS, or BLOCK.

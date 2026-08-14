# N5 AI — Technical Review Decisions & Next Revision (Rev 2)

> Project record: decisions document produced by the product owner on 2026-08-14,
> responding to the technical review in `N5-AI-Review-and-Suggestions.md`.
> Stored verbatim for traceability. The reviewer's response is in
> `N5-AI-Response-to-Decisions.md`.

## 1. Executive Verdict

The submitted technical review is strong and identifies the real engineering risks in the N5 AI concept.

Decision: approximately 90% of the review is accepted and should be merged into the next specification revision.

The product direction remains:

> **N5 AI — On-Prem AI Operations Platform for Niagara**

The key principle is:

> **Discover → Understand → Detect → Diagnose → Explain → Recommend → Act**

However, the first commercial proof should remain narrow and evidence-driven.

## 2. Decisions on the Review

| Proposal | Decision | Rationale |
|---|---|---|
| Human-in-the-loop Discovery | **ACCEPT** | Discovery must not blindly auto-classify a messy BMS |
| Relationship inference | **ACCEPT** | Essential for meaningful Root Cause Analysis |
| Weather-normalized baseline | **ACCEPT** | Explainable and defensible for V1 |
| Local LLM | **ACCEPT WITH CHANGE** | Keep backend pluggable; do not mandate expensive GPU hardware |
| Confidence methodology | **ACCEPT** | Avoid false precision |
| Explainable Health Score | **ACCEPT** | Every score must be traceable |
| Tridium developer program | **ACCEPT — HIGH PRIORITY** | Must be addressed before production module development |
| MCP | **ACCEPT LATER** | Build internal Tool API first; add MCP adapter later |
| PostgreSQL + TimescaleDB + pgvector | **ACCEPT** | Good single-server architecture |
| Arabic + English | **ACCEPT** | Important regional differentiator |
| Simulated station | **ACCEPT — DAY 1** | Required for reproducible development and regression testing |
| Pilot ground truth | **ACCEPT** | Required for credible product validation |
| Degradation behavior | **ACCEPT** | Essential for operational reliability |
| Model governance | **ACCEPT** | Required for traceability and rollback |
| Three-capability PoC | **ACCEPT** | Keeps the first proof focused |
| Discovery = 50% of product | **REJECT AS A LITERAL RATIO** | Discovery is a moat/foundation, but RCA and Energy Intelligence are equally important to product value |
| 48–96 GB VRAM requirement | **REJECT AS A FIXED REQUIREMENT** | Hardware must be determined by benchmark, latency and concurrent-user requirements |
| MCP as Phase 1 dependency | **REJECT** | It is an interface enhancement, not a prerequisite for proving product value |

## 3. Core Product Architecture

```text
                    N5 AI
                      │
        ┌─────────────┴─────────────┐
        │                           │
   FOUNDATION                  INTELLIGENCE
        │                           │
 Discovery + Model          Alarm RCA + Energy
        │                           │
        └─────────────┬─────────────┘
                      │
                   ASK AI
```

Recommended engineering allocation (planning guidance, not fixed budgets):

- 25% Discovery + Asset Model
- 30% Alarm Intelligence + Root Cause
- 20% Energy Intelligence
- 10% AI / LLM
- 10% UI
- 5% Infrastructure / Security

## 4. Discovery Strategy

Do NOT use fully automatic discovery. Real BMS stations may contain inconsistent naming,
missing/wrong tags, duplicate points, orphan points, abandoned commissioning points, and
mixed contractor conventions.

```text
Station Scan
     ↓
AI Proposes Equipment / Tags
     ↓
Engineer Reviews
     ↓
Engineer Confirms / Corrects
     ↓
Canonical Asset Model
     ↓
Site-specific learning
```

The engineer remains the authority.

## 5. Canonical Asset Model

N5 AI maintains its own internal canonical model, with import/mapping from Niagara Tags,
Project Haystack, and Brick Schema — without becoming dependent on one external schema.

```text
Building
 └── Site
      └── System
           └── Equipment
                ├── Points
                ├── Relationships
                ├── Histories
                ├── Alarms
                └── Operating Context
```

## 6. Relationship Inference

The dependency graph is constructed from three evidence sources:

1. **Naming heuristics** — useful but weak
2. **Historical correlation** — e.g., CHWP-03 trips → differential pressure changes → AHU SAT
   increases → alarm cascade: evidence of probable dependency
3. **Engineer confirmation** — the system proposes (`CHWP-03 → AHU-03, Probability: High`),
   the engineer confirms or rejects

## 7. Confidence Engine

Never display artificial precision. Initial system supports **High / Medium / Low**.
Advanced mode may display a numerical score once mathematically justified:

```text
Confidence = Evidence Quality × Rule Reliability × Data Completeness × Historical Validation
```

Every confidence result must answer: **Why this confidence?**

## 8. Evidence Engine (new core component)

Every AI finding must be traceable:

```text
Finding → Evidence → Calculation → Source Points → Time Window → Rule / Model Version
```

Example:

```text
Finding: Cooling performance degradation
Evidence: SAT = 15.8°C, Expected SAT = 13.2°C, Cooling Valve = 96%
Period: 09:42–14:32
Data completeness: 97%
Detection rule: FDD-AHU-004 v1.3
```

## 9. Energy Baseline

V1 uses an explainable baseline: Outdoor Temperature + Day Type + Schedule + Occupancy
(if available) = Expected Energy; Actual vs Expected = Anomaly.

The baseline must account for Gulf-region operating patterns: Ramadan, Eid, public holidays,
Friday, weekend, and differing operating schedules. ML is introduced only after the regression
baseline is measured and validated.

## 10. Local LLM Strategy

No specific GPU configuration as a product requirement. Benchmark 8B / 14B / 32B / larger
models on: tool-calling quality, Arabic quality, English quality, latency, memory, concurrent
users, and structured-query accuracy. The LLM backend is pluggable (Local LLM / Approved
Cloud LLM) over one internal tool interface.

## 11. MCP Strategy

MCP supported eventually, not a Phase 1 dependency. Build the internal Tool API first:

```text
query_points(), query_history(), get_alarm_group(), get_asset(),
get_relationships(), get_baseline(), get_finding(), get_recommendation()
```

Then expose the same capabilities through an MCP adapter, keeping the core independent of
any specific agent protocol.

## 12. Data Architecture

PostgreSQL + TimescaleDB + pgvector; one primary database. Stores timeseries, asset model,
relationships, findings, recommendations, audit data, and document/SOP embeddings.
Retention: raw ~13 months initially, aggregates long-term, documents per customer policy —
all configurable.

## 13. Arabic / English

First-class bilingual product: Arabic and English UI, RTL layout, questions and answers in both
languages, bilingual reports, Arabic-capable local LLM. Example:

> لماذا ارتفع استهلاك الطاقة في P501 اليوم؟

Answered with the same evidence and engineering reasoning as the English query.

## 14. Simulated Niagara Station (Day 1)

Simulated station/dataset with known ground truth:

1. CHWP failure → alarm cascade
2. Cooling valve stuck
3. Bad SAT sensor
4. Schedule anomaly
5. Energy consumption anomaly

```text
Scenario → Known Fault → Generated Data → N5 AI → Expected Finding → Automated Regression Test
```

## 15. Replay Engine

Historical incident replay (timeline reconstruction of a fault and N5's response), valuable for
development, regression testing, demonstrations, customer training, and sales proof-of-value.

## 16. Revised Proof of Concept

Three killer capabilities only:

1. **Alarm Grouping → Root Cause** (1,284 alarms → 3 major events → 1 probable root cause)
2. **Energy Anomaly + Contributors**
3. **Ask AI** over validated engine results with evidence

Explicitly deferred from first PoC: Building Health Score, autonomous control, automatic
Maximo work orders, cloud dependency, large mandatory LLM, full fleet management.

## 17. Pilot Ground Truth Protocol

Site engineer reviews every finding: **VALID / INVALID / ALREADY-KNOWN**, plus severity
(Critical/High/Medium/Low). Metrics: precision, false-positive rate, novel finding rate,
engineer acceptance rate, and time saved (investigation time with vs without N5 AI).
"Already-known" is not a failure — correctly rediscovering a known problem demonstrates the
detection method works.

## 18. Finding → Diagnosis → Recommendation → Action

Strict separation, preventing the AI from jumping from an anomaly to a control action:

```text
FINDING (SAT abnormal)
   ↓
DIAGNOSIS (Cooling performance degradation)
   ↓
RECOMMENDATION (Inspect cooling valve)
   ↓
ACTION (Engineer-approved work order)
```

## 19. Health Score

Kept on the roadmap; not a first-PoC success criterion. Every score explainable and
expandable to its formula and inputs.

## 20. Degradation Behavior

- **Station unavailable**: "Data unavailable — last update 14:32"; never present stale data as live
- **LLM unavailable**: FDD, RCA, Energy, Findings, Dashboard continue; only conversational AI degrades
- **History gaps**: show data completeness, missing intervals, baseline confidence impact;
  never silently calculate a misleading anomaly

## 21. Model Governance

Every finding records engine version, rule version, ML model version, configuration version,
timestamp, and input data window — making findings reproducible and rollback safe.

## 22. Updated Technology Architecture

```text
Niagara Module
      │  Secure outbound connection
      ↓
AI Gateway
      ├── Data Normalization
      ├── Asset Model
      ├── Discovery Engine
      ├── Relationship Engine
      ├── Rules Engine
      ├── FDD Engine
      ├── Energy Baseline
      ├── Root Cause Engine
      ├── Evidence Engine
      ├── Internal Tool API
      └── Model Governance
              ├── Local LLM
              └── Optional Cloud LLM
                      ↓
                  AI Agent
                      ↓
                    UI
```

MCP exposed later as an adapter over the Internal Tool API.

## 23. Security Architecture

Initial integration read-only. Station initiates the outbound connection (mTLS) where practical
— no unnecessary inbound firewall exposure. Plus: role-based access, encryption, API
authentication, audit logging, network segmentation, configuration backup, version tracking,
explicit write permissions, human approval for actions.

## 24. Revised Development Priority — First 90 Days

1. Tridium developer-program / module-signing preparation
2. Simulated station + automated test harness
3. Define collector API contract, canonical asset model, evidence model, finding schema, relationship schema
4. AI-assisted Discovery + Engineer Confirmation UI
5. Alarm Correlation + Root Cause against known simulated faults
6. Weather-normalized Energy Baseline
7. Ask AI over validated findings via the internal Tool API
8. Connect to the first real Niagara station under the formal pilot protocol

## 25. Final Architecture Decision

Five foundations: **Asset Model, Relationship Graph, Evidence Engine, Deterministic
Intelligence, AI Agent.** The LLM is not the product.

## 26. Final Verdict

Specification approved with the above changes.

**Accepted as core principles**: on-premise first; offline-capable; thin Niagara module; external
AI Engine; human-in-the-loop discovery; evidence-backed findings; deterministic engineering
intelligence; LLM as explainer/agent; read-only V1; approval-based actions; Maximo later;
Arabic + English; formal pilot validation.

**Rejected / modified**: discovery is not literally 50% of the product; no fixed 48–96 GB GPU
requirement; MCP is not a Phase 1 dependency; no premature graph DB; no Health Score in the
first PoC.

**Product north star**:

> N5 AI should behave like a BMS engineer who has instant access to every point, history,
> alarm, equipment relationship, energy trend, and maintenance record — while showing the
> evidence behind every conclusion.

**The first proof**:

> Can N5 AI find a real, useful problem in a Niagara station, explain why it believes the
> problem exists, and save an experienced engineer measurable investigation time?

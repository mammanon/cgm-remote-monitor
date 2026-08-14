# N5 AI — Specification Review & Suggested Additions

**Reviewed document:** *N5 AI — On-Prem AI Operations Platform for Niagara* (sections 1–34)
**Review date:** 2026-08-14
**Status of this document:** Technical review + concrete proposals to merge into the next revision of the spec.

---

## 1. Executive Verdict

The specification is strong, coherent, and correctly sequenced. It avoids every trap that kills
products in this category: it is not a chatbot wrapper, not cloud-only, does not treat the LLM as
a source of engineering truth, and does not attempt autonomous control in v1. Section 29
("What N5 AI Should NOT Be") alone puts it ahead of most attempts in this market.

**The spec is right about *what* to build.** The risk is concentrated in two areas the current
document treats as solved steps rather than as the core engineering challenge:

1. **Automatic discovery & equipment modeling** (Sections 10–11)
2. **Baseline quality for energy intelligence** (Section 18)

Everything downstream — root cause analysis, energy contributors, Ask AI credibility — is only
as good as those two layers. They should be treated as the product's moat and receive the
largest share of engineering effort.

---

## 2. What the Spec Gets Right (Keep As-Is)

| Decision | Why it matters |
|---|---|
| Read-only + recommendations in V1 (Section 22) | Removes the liability blocker that stalls BMS AI sales; builds trust before requesting write access |
| LLM as explainer/translator, not truth source (Sections 8.4, 9) | Engineering conclusions come from deterministic engines; the LLM cannot hallucinate a diagnosis |
| On-premise first, offline-capable core (Sections 5, 24) | Matches real procurement constraints of government, defense, healthcare, and Gulf-region enterprise sites |
| AI Engine outside the Niagara runtime (Section 8) | Station JVM stability is sacred; heavy compute does not belong in it |
| Thin Niagara module, intelligence external (Section 7) | Correct separation; the module is a collector + API client + UI host |
| Approval-based Maximo workflow (Section 21) | Work orders carry cost; human approval is non-negotiable initially |
| MVP scoping and pilot metrics (Sections 26–27) | "The product should not be considered successful merely because the AI can answer questions" is exactly the right bar |
| Multi-layer intelligence model (Section 9) | Rules + statistics + ML + knowledge graph + LLM is the architecture that survives engineer scrutiny |

---

## 3. The Four Hard Problems (Ranked by Risk)

### 3.1 Discovery is ~50% of the product

Section 10 shows a clean discovered tree. Real stations do not look like that. Expect:

- Point names like `B1_P501_AHU3_SF_ST` with no tags at all
- Inconsistent naming conventions across floors/phases installed by different contractors
- Orphan points, duplicate points, abandoned test points
- Partial or wrong Haystack tagging where it exists

**"Discover Building" cannot be a fully automatic scan.** It must be an **AI-assisted workflow
with human confirmation**:

```text
Station scan
     ↓
AI proposes equipment groupings + canonical tags
(point-name pattern recognition is a genuinely strong LLM use case)
     ↓
Engineer reviews / corrects in a dedicated review UI
     ↓
Corrections feed back into the classifier (site-specific learning)
     ↓
Confirmed asset model becomes the AI's foundation
```

Internally, standardize on **Project Haystack (or Brick Schema) as the canonical model**
regardless of what the station uses. If discovery is done well, it becomes the competitive moat —
every competitor's demo dies on the customer's messy station.

### 3.2 Relationship inference

"CHWP-03 feeds AHU-01" often exists **nowhere** in the station — not in tags, not in naming,
not in the wire sheets accessible via the API. The root-cause engine (Section 17) is only as good
as this dependency graph. Build it from three combined sources:

1. **Naming heuristics** — weak but free
2. **Correlation mining from histories** — powerful and under-exploited: a pump trip is a natural
   experiment; when P-03 stopped, which SATs rose within minutes? That reveals hydraulic
   topology from data alone
3. **Engineer confirmation UI** — the graph is proposed, then confirmed, like discovery

### 3.3 Baselines that survive reality

"+18.2% above baseline" (Section 18) is only credible if the baseline handles weather,
day-of-week, occupancy, and — for the likely target market — **Ramadan, Eid, and holiday
schedules**. Recommendation:

- Start with **weather-normalized regression per meter** (outdoor temperature + day-type +
  schedule features). Boring, explainable, defensible.
- Avoid opaque ML baselines in V1: they produce anomalies engineers cannot verify, which
  destroys trust in exactly the pilot metric the spec defines (engineer acceptance rate).
- Graduate to ML baselines only once the regression baseline is beaten measurably on the
  site's own data.

### 3.4 Local LLM realism

Section 8.4 is correctly scoped, but the spec should be concrete about what "Local LLM" costs
and what actually matters:

- **Hardware**: a useful on-prem experience needs roughly one high-end GPU server
  (48–96 GB VRAM class) running a quantized 70B-class or strong 30B-class open model.
  Specify this in the sizing guide; do not let sales discover it at deployment time.
- **What matters is tool-use / function-calling quality, not chat quality.** The LLM's job is
  natural language → structured queries against deterministic engines, and engine results →
  readable explanation.
- **Make the LLM backend pluggable from day one**: local model for Mode A (offline),
  frontier cloud model (e.g., Claude API) for Mode B (hybrid). Hybrid sites will get visibly
  better Ask AI, and that contrast is itself the sales lever for Mode B — without ever
  compromising the offline promise.

---

## 4. Suggested Additions to the Spec

These are proposed as **new sections or amendments** to the next revision.

### 4.1 Confidence methodology (amend Sections 14, 15, 17, 20)

The mockups show "Confidence: 91%". If that number is not derived from something real,
engineers will calibrate against it, catch it being wrong, and stop trusting every number in the
product. Either:

- **Define the math** — e.g., `confidence = rule certainty × evidence completeness × historical
  precision of this rule on this site`, documented and inspectable; or
- **Use coarse bands** — High / Medium / Low — until the math exists.

Never display false precision. This deserves its own subsection under the AI Engine.

### 4.2 Health score formulas must be published in the UI (amend Section 19)

An unexplained score is decoration, and the product's entire identity is explainability. Every
score should expand to its formula and inputs, e.g.:

> *Energy 82 = 100 − weighted anomaly-days over trailing 30 days (weights: magnitude × duration)*

### 4.3 Tridium developer program as a Phase 1 prerequisite (amend Section 30)

Niagara 4 module development requires joining the Tridium developer program and obtaining
**module signing certificates**. This is a lead-time item, not a detail — start the commercial
process before writing module code. Add to Phase 1 explicitly, alongside the trademark note in
Section 2 (the "N5" name itself should get a legal check against Tridium/Honeywell marks,
given "Niagara 5" is a real product generation).

### 4.4 Expose the AI Engine as an MCP server (new subsection under Section 31)

Expose the engine's capabilities — query points, histories, alarm groups, asset graph,
baselines, findings — as **Model Context Protocol (MCP) tools**. Cost is near zero, and it:

- Makes "Ask AI" a standard agent-over-tools pattern instead of custom glue
- Lets customers connect their own approved AI clients to N5 later (enterprise appeal)
- Keeps the local-vs-cloud LLM backend swap trivial (both speak the same tool interface)

### 4.5 Data plumbing specification (new section)

The spec never states collection rates, retention, or storage. Add:

- **Collection**: default history intervals (e.g., 5–15 min analog, COV digital), alarm streaming,
  expected point counts per edition (Edge ≈ 5k points, Enterprise ≈ 50k+)
- **Storage**: recommended stack that fits one on-prem box —
  `TimescaleDB/PostgreSQL` (timeseries + asset graph + findings), small vector store for
  documents/SOPs, all under Docker Compose. Do not introduce more infrastructure than a
  single server can run.
- **Retention**: e.g., raw 13 months (year-over-year baselines), aggregates indefinitely.

### 4.6 Bilingual UI — Arabic / English (new requirement, Sections 12–13)

If the Gulf market is the target, first-class Arabic in both the UI and Ask AI
(questions *and* answers, RTL layouts) should be an explicit requirement. It is a genuine
differentiator that regional competitors under-deliver, and it affects LLM model selection
(the chosen local model must be strong in Arabic).

### 4.7 Simulated station & test harness (new item in Phase 1)

Build a **simulated Niagara station dataset** (realistic point names, histories with injected
faults, alarm storms with known root causes) before touching a real Supervisor. It enables:

- Development without a licensed station in the loop
- **Regression testing of the FDD/root-cause engines against known ground truth**
- Reproducible demos with dramatic, safe fault scenarios

### 4.8 Pilot ground-truth protocol (amend Section 27)

Formalize the success question ("would an experienced BMS engineer consider these findings
valid?") into a protocol:

- Site engineer labels every finding for one week: **Valid / Invalid / Already-knew**
- Report precision (valid ÷ total) as the headline pilot result
- **Track "already-knew" separately from false positives — they are not failures.** An AI that
  independently rediscovers what the engineer knows is proof the method works.
- Log time-to-diagnose for a sample of alarms with and without N5, to quantify the
  investigation-time reduction claimed in Section 3.

### 4.9 Degradation & failure behavior (new section)

Section 24 covers Internet loss; also specify behavior when:

- The station is unreachable (show data age everywhere; never present stale data as live)
- The LLM is down (deterministic engines and UI keep working; only Ask AI degrades)
- History collection has gaps (baselines must handle missing data without silently skewing)

### 4.10 Model governance (amend Section 23)

"Model/version tracking" is listed but underspecified. Add: every finding is stamped with the
engine + rule/model version that produced it, so a finding is reproducible and a bad model
release is traceable and revertible.

---

## 5. Revised PoC Cut (Amendment to Sections 26 & 34)

The 7-item MVP is right. For the **first PoC**, cut harder — three capabilities are the killer demo
on a real station:

1. **Alarm grouping → root cause** — the "1,284 alarms → 3 events" screen sells itself to
   operators instantly
2. **Energy anomaly with named contributors** — "+18% and here is who caused it"
3. **Ask AI over both** — natural language over validated findings

**Defer equipment health scores from the PoC.** They need weeks of history to be meaningful,
and a wrong score on day 3 of a pilot does trust damage the pilot never recovers from.

```text
PoC pipeline (revised)

Niagara Station (P501)
     ↓
Collector (read-only)
     ↓
AI-assisted discovery + engineer confirmation   ← human in the loop
     ↓
Asset model + dependency graph
     ↓
Alarm correlation → root cause     Energy baseline → anomaly + contributors
     ↓                                   ↓
     └────────────── Ask AI ─────────────┘
                       ↓
         Engineer validation (Valid / Invalid / Already-knew)
```

---

## 6. Recommended Concrete Stack (Amendment to Section 31)

| Layer | Recommendation |
|---|---|
| Niagara module (`n5ai-rt`) | N4 module (Java), read-only in V1: BQL/history/alarm export, secure outbound HTTPS/WebSocket to gateway. No inbound ports opened on the station. |
| Transport | Station → Gateway push (station initiates), mTLS, so the BMS network needs no inbound firewall rules |
| AI Gateway | Python (FastAPI) or Node — normalization, asset model, rules/FDD, baselines, root cause |
| Storage | PostgreSQL + TimescaleDB (timeseries, asset graph, findings, audit); pgvector for docs/SOP |
| LLM serving | Pluggable: vLLM/Ollama with a strong Arabic-capable open model (Mode A) ↔ Claude API (Mode B) — behind one internal interface |
| Agent interface | MCP server exposing engine tools; Ask AI is an agent over those tools |
| UI | Web app served by the gateway; embedded in Workbench/station UX via `n5ai-ui` where needed |
| Deployment | Docker Compose on a single on-prem server (GPU optional until LLM features enabled) |

---

## 7. Priority Order for the Next 90 Days

1. Start the **Tridium developer program** process (lead time)
2. Build the **simulated station dataset + test harness** (4.7)
3. Build **collector API contract + gateway skeleton + asset model schema** (Haystack-based)
4. Build **AI-assisted discovery with confirmation UI** — the moat (3.1)
5. Implement **alarm correlation + root cause** against the simulated dataset with ground truth
6. Implement **weather-normalized energy baseline** (3.3)
7. Wire **Ask AI** as an MCP agent over the engines (LLM backend pluggable)
8. Then, and only then, connect to the first real station (P501) under the pilot protocol (4.8)

---

## 8. Summary of the Review

| Area | Verdict |
|---|---|
| Vision & positioning (1–3, 28–29, 33) | Sound — keep |
| Architecture (4–9, 31) | Sound — add MCP interface, data plumbing, pluggable LLM |
| Discovery & equipment model (10–11) | **Underestimated — make human-in-the-loop, treat as the moat** |
| Dashboards & Ask AI (12–14) | Sound — add confidence methodology, bilingual requirement |
| FDD / root cause / energy (15–18) | Sound — baseline method must be explainable regression first |
| Health score (19) | Keep, but publish formulas; defer from PoC |
| Recommendations & Maximo (20–21) | Sound — keep approval-based |
| Control strategy (22) | Sound — V1 read-only is correct |
| Security & offline (23–24) | Sound — add degradation behavior, model governance |
| Editions & roadmap (25, 30) | Sound — add developer-program prerequisite to Phase 1 |
| MVP & pilot (26–27, 34) | Right instinct — cut PoC to 3 capabilities, formalize ground-truth protocol |

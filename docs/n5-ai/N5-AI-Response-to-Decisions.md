# N5 AI — Reviewer Response to the Decisions Document (Rev 2)

**Responding to:** `N5-AI-Decisions-Rev2.md` (2026-08-14)
**Verdict:** All three rejections are accepted by the reviewer. The new components introduced
(Evidence Engine, Replay Engine) are strong additions. **Six items from the critical discussion
remain unresolved and should be closed in the next revision — one of them survived into the
new PoC definition and is blocking.**

---

## 1. Position on the Three Rejections — All Accepted

### 1.1 "Discovery = 50%" rejected as a literal ratio — agreed

The original phrasing meant *risk* concentration, not budget allocation: discovery is where
products in this category most often die on real stations, not where most engineering hours go.
The 25% allocation in Section 3 is a sensible planning figure. One caveat to preserve: if
discovery quality turns out poor at the pilot site, RCA and Energy outputs degrade with it —
so treat the 25% as a floor with permission to grow, not a cap.

### 1.2 Fixed 48–96 GB VRAM requirement rejected — agreed

Benchmark-driven sizing is the correct policy; the original figures were illustration, not
specification. One thing the benchmark plan (Section 10) should make explicit: the **gate is
tool-calling / structured-query accuracy, not model size** — set a minimum accuracy bar on a
fixed eval set (bilingual questions → expected tool calls) and take the smallest model that
clears it. Be prepared for the hard combination to be *Arabic quality + tool-calling quality in
the same small model*; that pair, not raw fluency, will decide the hardware floor.

### 1.3 MCP deferred behind an internal Tool API — agreed

Internal Tool API first, MCP as a later adapter over the same capabilities, is the same
architecture with better sequencing. The original proposal never intended MCP as a
prerequisite for proving value. One design note so the adapter stays cheap later: keep the
internal tools **stateless, JSON-in/JSON-out, with per-tool schemas** from day one — that
makes the eventual MCP adapter a thin mechanical wrapper instead of a refactor.

## 2. Endorsements — New Material in the Decisions Document

- **Evidence Engine (Section 8)** is the best addition in the document. It converts
  "explainability" from a principle into a buildable component with a schema, and it is the
  mechanism that makes the pilot's VALID/INVALID labeling workable — the engineer judges
  evidence, not assertions.
- **Replay Engine (Section 15)** earns its place twice: as the regression-test driver over the
  simulated faults, and as the sales demo ("watch N5 catch last month's incident"). Build it as
  the same code path as live processing (feed historical data through the real engines), not a
  separate visualization.
- **Finding → Diagnosis → Recommendation → Action separation (Section 18)** and the
  five-foundations framing (Section 25) are correct and should not be weakened in future
  revisions.
- The **90-day priority order (Section 24)** is agreed as written.

## 3. Unresolved Items — To Close in the Next Revision

### 3.1 BLOCKING: Section 16 still shows contributor percentages

The PoC definition retains:

```text
Energy +18%
AHU-03       47%
Chiller-01   26%
CHWP-03      14%
```

This contradicts the document's own Confidence Engine rule (Section 7: never display
artificial precision), and it is **not computable from the reference building**, which has a
single main meter. Percentage attribution requires sub-metering or calibrated power models
that will not exist at PoC time.

**Proposed resolution** — redefine PoC capability #2 as **ranked correlated suspects with
evidence**, no percentages:

```text
P501 ENERGY  +18% above expected     Confidence: High

CORRELATED SUSPECTS (ranked)

1. AHU-03    valve 94%, SAT 2.4°C below expected — since 09:42
2. Chiller-01 running outside efficiency envelope — since 10:15
3. CHWP-03   continuous operation vs scheduled stop
```

This is honest, achievable with one meter, and still a killer demo. Percentage attribution
becomes a documented **sub-metering-dependent feature**: enabled only where meters exist.
Corollary: **metering availability should be an explicit criterion when selecting the pilot
site** — a site with even partial sub-metering makes the energy story much stronger.

### 3.2 False-positive management workflow — absent from the decisions document

The pilot protocol (Section 17) *measures* false positives but the product still has no
*workflow* for them: suppress / snooze / adjust threshold / mute-per-equipment /
mark-not-relevant, with those actions feeding back into rule tuning. Twenty years of FDD
platforms have died in week four of deployment from exactly this gap — engineers learn to
ignore the findings feed, and the pilot's acceptance metric collapses. This needs a spec
section and a minimal version **in the PoC UI** (at least suppress + mark-invalid, captured
as tuning data).

### 3.3 "N5 AI" trademark / naming review — not addressed

The name reads as "Niagara 5 AI" and the descriptor uses Tridium's mark. Not an engineering
blocker, but it must be on the pre-launch checklist with a real trademark review — cheaper to
resolve before the name is on proposals, modules, and domains.

### 3.4 Offline update mechanism for Mode A — not addressed

Offline operation without an offline update path is a product that decays in the field. The next
revision should specify **signed offline update packages** (rules, models, software) with
version verification on import — consistent with Model Governance (Section 21). Needed
before the first air-gapped customer, so it belongs in the spec now even if built later.

### 3.5 Savings estimates — not addressed

The original spec displayed "Potential saving: 11–14%". The decisions document neither
retains nor retires it. Recommendation: explicitly **exclude savings figures from the product**
until a measurement methodology (IPMVP-style baseline adjustment) exists. One disputed
savings claim costs more credibility than the feature adds.

### 3.6 Offline capability matrix contradiction — not addressed

The original Section 24 matrix lists "BMS Control ✓" as an offline capability while V1 is
read-only. The next revision should correct the matrix so no sales or marketing material
inherits a promise the actuation strategy forbids.

## 4. Summary

| Item | Status |
|---|---|
| Three rejections (discovery ratio, GPU spec, MCP timing) | Accepted — no dispute |
| Evidence Engine, Replay Engine, F→D→R→A separation | Endorsed |
| 90-day priorities | Agreed as written |
| §16 contributor percentages | **Blocking — redefine as ranked suspects with evidence** |
| False-positive workflow | Add to spec + minimal PoC version |
| Naming/trademark review | Pre-launch checklist item |
| Offline update packages | Add to spec (build before first air-gapped customer) |
| Savings estimates | Explicitly exclude until measurement methodology exists |
| Offline matrix "BMS Control ✓" | Correct in next revision |

With 3.1 resolved and 3.2 added, the decisions document is a sound basis to start
Priority 2–3 of the 90-day plan: the simulated station, the test harness, and the core schemas
(asset model, evidence, finding, relationship, collector API contract).

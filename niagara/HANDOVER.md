# GLSAN CBMS → Maximo Integration — Project Handover Brief

> Read this first. It is the full context of the project so far, written so
> work can continue in a fresh session with no prior conversation.

## What this project is

KAFD (client) runs IBM Maximo (CAFM). GLSAN runs the CBMS (Niagara 4.14.2.12
stations). When a BMS alarm needs maintenance, CBMS creates a Maximo
**Service Request (SR)** via KAFD's webMethods middleware (OAuth 2.0,
HTTPS). The pilot scope is Parcel 501; stations are JACEs (P501_J1..J4 seen)
under three zone supervisors (Zone A / B / C, no parcel overlap).

## What is built and WORKING (bench-tested end-to-end on 4.14.2.12)

1. **`MaximoAlarmSender`** — a Niagara Program object (source:
   `MaximoAlarmSender.java`; paste-ready Edit-tab content:
   `EditTab-paste.txt`; install guide: `SETUP.md`). Behavior:
   - Scans the alarm DB every ~15 s (TriggerSchedule → `execute`).
   - Accepts alarm classes `P<parcel>_<SEVERITY>` only (P501_CRITICAL /
     MAJOR / MINOR). Ignores defaultAlarmClass, TEST, legacy classes.
   - **Opt-in send model**: an alarm is sent ONLY when an operator writes a
     console note containing MAXIMO or MAXMO (any case). Ack has no Maximo
     meaning. Per-severity `autoSendCritical/Major/Minor` slots exist,
     ALL DEFAULT FALSE.
   - CBMS tag comes from a **`cbmsTag` metadata facet** on each alarm
     extension (value `%parent.parent.displayName%` = equipment folder
     name). Source Name format stays untouched. Missing facet → alarm
     marked FAILED (visible, never silent).
   - Builds the agreed 13-field MXSR JSON (ticketid `BMS-<n>` from a
     persisted counter; `ticketPrefixChoice` dropdown slot: BMS / BMSZA /
     BMSZB / BMSZC per zone), OAuth client-credentials token cached,
     POSTs with 5 s timeouts, max 5 alarms/cycle (engine safety).
   - 201 → alarm marked SENT + SR number (`maximoSr` facet). 400 → FAILED
     (BMXAA4129E → DUPLICATE), never retried. 5xx/timeout → left unmarked
     = retried next cycle (alarm DB is the store-and-forward queue).
   - Optional **SR status polling**: `statusUrl` slot with `{ticketid}`
     placeholder (empty = off) → writes `maximoSrStatus` facet.
   - ALL configuration is slot-based on the Program's Property Sheet
     (auto-created on first start). No recompile for config changes.
   - Compile notes for 4.14: imports tab needs modules `baja` + `alarm-rt`;
     packages `javax.baja.sys`, `javax.baja.collection`, `javax.baja.alarm`
     (module alarm-rt), `java.io`, `java.net`, `java.util`,
     `java.util.regex`. `BComponent.add` needs BValue; BFacets built by
     chaining single-key `BFacets.make` (no array overload).

2. **`MockMiddleware.java`** — standalone mock of the KAFD middleware
   (compile/run with the Niagara JRE, port 8099). Endpoints: POST
   /oauth/token, POST /maximo/api/os/MXSR, GET ?ticketid= status query
   (NEW→INPRG→COMP). Test triggers: asset containing BADASSET → 400;
   FAIL500 → 500; repeated ticketid → 400 BMXAA4129E; wrong token → 401.
   Demo-formatted output (framed block per SR). Run:
   `"c:\niagara\niagara-4.14.2.12\jre\bin\javac" MockMiddleware.java` then
   `...\java MockMiddleware`.

3. **P501 tag↔asset correlation workbook**
   (`Maximo Correlation Sheets/P501/AFTER - KAFDLF501 CBMS Tag Correlation R01.xlsx`):
   938 CBMS tags extracted from P501_J1..J4 bogs matched against KAFD's
   MEC+ELE asset registers. 325 AUTO (mechanically audited; 22 of them
   AUTO-DESC = weaker evidence, spot-verify), 526 REVIEW (candidates
   given), 87 UNMATCHED (need lifts/fire-loop/plumbing/Parcel-502
   registers from KAFD). Review procedure and class-level questions are in
   the workbook's Summary sheet.

## Agreed decisions (client/GLSAN)

- Opt-in note send model (above); auto-send default OFF for all severities.
- Alarm classes: site convention `P<parcel>_<SEVERITY>` (NOT `_Maximo_`).
- cbmsTag metadata (NOT parsing Source Name).
- ≥30 s timeDelay to be configured on all alarm extensions (bulk edit,
  combined with adding cbmsTag metadata — ~3,167 extensions on P212/P503/
  P109 stations + P501's 4,332; prepared-bog approach agreed, GLSAN reviews).
- Deployment: one sender Program per zone supervisor (A/B/C).
- Ticket prefix per zone via dropdown (BMSZA/BMSZB/BMSZC recommended,
  needs KAFD confirmation; plain BMS + seeded ranges 100000/200000/300000
  is the fallback).
- RTN/normal events never sent. Alarm text (msgText) goes to
  description_longdescription.

## KAFD facts (from their emails)

- Middleware: webMethods; agreed matrix: 5 CBMS IPs → 10.252.130.142:443,
  but latest reply gives hostnames (apigw.scp.prod.kafd.sa,
  um.apps.dev.scp.gcp1.kafd.sa) — **connectivity still 100% loss; firewall
  possibly targeting wrong destination — unresolved**.
- Auth changed to **OAuth 2.0**; credentials come from their developer
  portal (devportal-dev-ui.apps.apps.dev.scp.gcp1.kafd.sa) — account
  needed, not yet obtained.
- Payload/status codes/ticketid format confirmed unchanged through
  middleware. 201=created (returns ticketid), 400=invalid asset/duplicate
  (BMXAA4129E)/missing field, 500=unreachable.
- classstructureid: only sample 1378 confirmed; classifications list +
  GET API exist (.../scp/api/v1/maximo/sr-mgmt/classifications); mapping
  of 3 severities pending CAFM.
- Direct Maximo dev URL (reference):
  https://masdev.manage.masdev.apps.ocpdev.kafd.sa/maximo/api/os/MXSR?lean=1
- KAFD's CBMS-01..18 requirements table exists; CBMS-11 (clear events)
  conflicts with agreed create-only scope — needs written alignment.

## Open items / next steps

1. Bulk-edit prepared bogs: add `cbmsTag` metadata + 30 s timeDelay to all
   alarm extensions (P501 bogs available; GLSAN to review + bench test).
   NOTE: hardcoded-literal vs BFormat in metadata — a bench test showed a
   literal typed value works; whether `%parent.parent.displayName%`
   resolves in metadata facets on 4.14 was NOT yet confirmed — test one
   extension first; if it doesn't resolve, generate literals per extension.
2. Review the 526 REVIEW rows + 87 UNMATCHED in the correlation workbook;
   validate the reviewed file (1:1 both directions) before sending to KAFD.
3. Email to KAFD: firewall/hostname/prod-vs-dev clarification, portal
   account, TLS cert, response contract confirmation, SR status query URL
   (+ WO number in response), classstructureid mapping, ticket prefix
   confirmation, clear-events scope, additional registers (lifts, fire
   loops, plumbing, Parcel 502 extract), confirm "Asset Code" is the
   Maximo asset identifier.
4. Operator training: MAXIMO note = create ticket; note before/independent
   of ack; MAXIMO-SKIP no longer exists.
5. Code signing of the Program object per station; cert import to User
   Trust Store when KAFD delivers the middleware CA.
6. Test alarms/misconfig cleanup on stations (TEST/legacy classes,
   `%alarmClass%` literals).

## Safety rules baked into the code (do not relax)

5 s HTTP timeouts; maxPerCycle bound; per-alarm try/catch; conn.close in
finally; retryable = leave unmarked (never mark on 5xx); ticket sequence
never reused; payload fields are the KAFD contract — no changes without
their sign-off.

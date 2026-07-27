# GLSAN CBMS → Maximo Alarm Sender — Setup Guide

Niagara 4 Program object that creates Maximo Service Requests (MXSR) from
station alarms, with an operator **Accept / Reject** step, through the KAFD
webMethods middleware (OAuth 2.0).

Source: [`MaximoAlarmSender.java`](MaximoAlarmSender.java)

```
Point (Maximo_Alarms ext) ──► Alarm class Parcel501_Maximo_{CRITICAL|MAJOR|MINOR}
                                        │
                                        ▼
                              Station Alarm Database
                                        │  scan every 10–30 s
                                        ▼
                      MaximoAlarmSender (Program object)
                       │ pending  = unacked alarm (waits)
                       │ ACCEPT   = operator acks the alarm
                       │ REJECT   = note "MAXIMO-SKIP"
                       │ CRITICAL = auto-send (configurable)
                       ▼
        OAuth token ──► POST MXSR JSON ──► middleware ──► Maximo
                       │ 201  mark SENT + SR number in console
                       │ 4xx  mark FAILED / DUPLICATE (no retry)
                       └ 5xx/timeout  leave unmarked → retried next scan
```

---

## 1. Prerequisites (collect before starting)

| # | Item | From |
|---|------|------|
| 1 | Final MXSR endpoint URL through the middleware | Middleware team |
| 2 | OAuth 2.0 token endpoint URL + client id/secret (Dev) | KAFD developer portal (`devportal-dev-ui.apps...`) — request the account early |
| 3 | Middleware TLS certificate or issuing CA (PEM/DER) | Middleware team |
| 4 | classstructureid list / mapping per severity | KAFD classifications list or the GET API (`.../sr-mgmt/classifications`) |
| 5 | Firewall open: CBMS server IPs → middleware :443 | KAFD network team (verify the rule targets the **new** middleware host, not only 10.252.130.142) |
| 6 | DNS: the CBMS servers must resolve the middleware hostname | Site IT |
| 7 | Workbench admin access to the station; station clock on NTP (+03:00 correctness) | GLSAN |
| 8 | A code-signing certificate for Program objects (see §6) | GLSAN |

Items 1–5 block **testing**, not configuration — everything below can be
prepared while waiting.

## 2. Alarm classes and extensions (station side)

1. Under `Config → Services → AlarmService`, create three alarm classes per
   parcel, spelled **exactly**:
   `Parcel501_Maximo_CRITICAL`, `Parcel501_Maximo_MAJOR`, `Parcel501_Maximo_MINOR`.
   (The code matches `_Maximo_` anywhere in the class name and reads the last
   `_` token as severity; it tolerates the historical `CRTICAL` typo, but fix
   the spelling if you can.)
2. On each integrated point, add the alarm extension (`Maximo_Alarms`),
   configured as on the existing BacnetPoints alarms, with:
   - **Alarm Class** = one of the three classes above
   - **Source Name** = `%parent.parent.displayName%` *(only if the CBMS tag is
     the name of the folder containing the point — see the check below)*
3. **The one critical check:** raise a test alarm and open it in the alarm
   console. The **Source** column must show the full CBMS tag exactly as in
   the mapping sheet (e.g. `ZB_501_COMN_BF4_SERVCFAN18_HVAC_SEF01`),
   character-for-character. If it shows a point name or "Points" instead, the
   tag lives at a different level — use `%parent.displayName%` instead. Wrong
   Source Name = `400 invalid asset` for every alarm later.

## 3. TLS trust store

`Platform → Certificate Management → User Trust Store → Import`: import the
middleware server certificate (or its issuing CA) **and**, if the OAuth token
endpoint is a different host, its certificate too. The station's Java HTTPS
stack uses this trust store; without the import every send fails with an SSL
handshake error.

## 4. Create the Program object

1. In Workbench, create a folder e.g. `Config → Drivers → MaximoIntegration`.
2. From the `program` palette drag a **Program** into it; name it
   `MaximoAlarmSender`.
3. Open the Program editor:
   - **Imports tab** — module dependencies `baja` and `alarm`; class imports
     as listed at the top of `MaximoAlarmSender.java`
     (`javax.baja.sys.*`, `javax.baja.alarm.*`, `javax.baja.collection.Cursor`,
     `java.io.*`, `java.net.*`, `java.util.*`, `java.util.regex.*`).
   - **Edit tab** — paste everything from the `CONFIG` block down to the end
     of the file (fields + methods, without the comment header).
4. **Save (compile)** — fix any name the compiler flags (see §9). No values
   need editing in the code: all configuration is slot-based.
5. **Start the Program once** (station running, Program started). On first
   start it **creates its own config slots** with placeholder defaults.
6. **Open the Program's Property Sheet** (right-click → Views → Property
   Sheet) and fill in the slots — see the table in §4a. This is the only
   place configuration is ever edited; changing a slot takes effect on the
   next scan cycle, **no recompile needed**. Dev and Production stations run
   identical code with different slot values.
7. **Trigger**: from the `schedule` palette drag a **TriggerSchedule** next to
   the Program, set it to interval mode, **15 seconds**, and link its
   `fire` topic to the Program's `execute` action. (A kitControl interval
   timer linked to `execute` works equally well.)
8. Ticket sequence: on first send the Program adds a `maximoTicketSeq` slot on
   itself. To continue an existing sequence (e.g. last manual ticket was
   BMS-187), set the slot to 187 on the Property Sheet.

## 4a. Configuration slots (Property Sheet reference)

| Slot | Default | Meaning |
|---|---|---|
| `middlewareUrl` | placeholder | Full MXSR endpoint through the middleware (from middleware team) |
| `tokenUrl` | placeholder | OAuth 2.0 token endpoint |
| `clientId` / `clientSecret` | placeholder | OAuth client credentials from the KAFD developer portal |
| `oauthScope` | `""` | OAuth scope, only if the middleware requires one |
| `classIdCritical` / `classIdMajor` / `classIdMinor` | `1378` | Maximo `classstructureid` per severity (replace from CAFM list) |
| `autoSendCritical` | `true` | CRITICAL alarms bypass the ack gate |
| `reportedBy` | `BMS-USER` | MXSR reporter fields |
| `reportedEmail` | `cbms@glsan.co` | |
| `reportPhone`, `affectedPerson`, `affectedEmail`, `affectedPhone` | `""` | Optional MXSR reporter/customer fields |
| `ticketPrefix` | `BMS-` | Application ticket id prefix |
| `utcOffset` | `+03:00` | Offset used in `reportdate` |
| `enabled` | `true` | Master on/off switch for the whole sender |
| `maxPerCycle` | `5` | Max alarms sent per scan cycle (engine safety) |
| `httpTimeoutMs` | `5000` | HTTP connect + read timeout (engine safety — keep small) |
| `maximoTicketSeq` | created on first send | Last used ticket number (persisted) |

**Protect the secret:** `clientSecret` is visible to anyone who can open the
Property Sheet. Set the category/permissions on the `MaximoIntegration`
folder so only admin users can view or edit it, and remember the value is
stored in the station database — treat station backups accordingly.

## 5. Operator workflow (train this — it is the approval feature)

| Operator action in the alarm console | Effect |
|---|---|
| **Acknowledge** a `*_Maximo_*` alarm | **ACCEPT** — SR is created on the next scan |
| Add a note containing **`MAXIMO-SKIP`** *before* acking | **REJECT** — never sent, marked SKIPPED |
| Do nothing | Alarm stays **pending** — nothing is sent |
| (CRITICAL class, if `AUTO_SEND_CRITICAL = true`) | Sent immediately, no ack needed |

After a send, the alarm record's data shows `maximoStatus` (`SENT` /
`FAILED` / `DUPLICATE` / `SKIPPED`) and `maximoSr` (the SR reference) —
visible in the alarm record's detail view.

**Rule that must be signed off with operations:** for these three alarm
classes, *Acknowledge now means "send to Maximo"*. Rejections must be noted
**before** acknowledging.

## 6. Code signing

Niagara 4.8+ requires Program objects to be signed. You do **not** need the
Tridium developer program for this — sign with your own code-signing
certificate imported into the platform's trust store. The exact procedure is
version-dependent (Workbench prompts to sign on save in recent versions);
check the "Code signing" chapter of your Niagara version's docs. On a bench
station you can temporarily allow unsigned program objects via the station's
security policy to iterate faster — never in production.

## 7. Bench test before the live station

Run the full checklist on a test station first — this code shares a JVM with
building control.

1. **Mock middleware**: point `MIDDLEWARE_URL`/`TOKEN_URL` at any HTTPS test
   endpoint you control and verify: token fetched once and cached; payload
   JSON matches the agreed MXSR structure byte-for-byte (13 fields, no
   extras); `reportdate` shows the alarm time with `+03:00`.
2. **Approval flow**: raise a MINOR test alarm → nothing sent while unacked →
   ack → sent on next cycle → SR marked. Raise another → note `MAXIMO-SKIP` →
   ack → marked SKIPPED, nothing sent.
3. **Critical bypass**: CRITICAL alarm sent without ack (if enabled).
4. **RTN safety**: return the point to normal *before* acking — no send.
5. **Failure paths**: unreachable middleware → log shows retryable, alarm
   stays unmarked, station engine stays healthy (watch Engine Hogs in
   spy/Station health); mock a `400` with `BMXAA4129E` → marked DUPLICATE,
   not retried; mock `401` → one token refresh + retry.
6. **Restart**: restart the station mid-queue — pending alarms are picked up
   again, ticket sequence continues (no duplicate `BMS-<n>`).

Then repeat 1–3 against the real Development middleware once firewall +
credentials exist. UAT cases with KAFD: 201 / 400 invalid tag / 400 duplicate
/ middleware down + recovery.

## 8. Troubleshooting

| Symptom | Likely cause |
|---|---|
| `SSLHandshakeException` in log | Middleware/token cert not in User Trust Store (§3) |
| `token HTTP 401/403` | Wrong client id/secret, or portal registration incomplete |
| `UnknownHostException` | DNS for middleware hostname not resolvable from CBMS VLAN |
| `connect timed out` every cycle | Firewall rule not applied / applied to old destination IP |
| `400 invalid asset` | Source Name ≠ mapping-sheet tag (§2 check), or asset missing from the sheet |
| `400 BMXAA4129E` marked DUPLICATE | Ticket id reused — check `maximoTicketSeq` wasn't reset |
| Alarms never send | Not acked (pending), class name lacks `_Maximo_`, or `ENABLED=false` |
| Two SRs for one alarm | A send timed out **after** Maximo created the SR, then retried with a new ticket id — raise `HTTP_TIMEOUT_MS` slightly and confirm middleware timeout behaviour with KAFD |

## 9. Known version-sensitive points (compiler will flag these)

- `javax.baja.collection.Cursor` vs `javax.baja.util.Cursor` — import
  whichever your version resolves.
- `service.getAlarmDb().getDbConnection(null)` — on some versions the
  method/argument differs slightly; the AlarmDbConnection open/close pattern
  is the same.
- `BFacets.make(String[], BObject[])` — if absent, chain
  `BFacets.make(BFacets, String, BObject)` per key.
- `getComponent()` inside Program code returns the hosting Program
  component; if your version names it differently the compiler will say so.

## 10. Scope limits (by design)

- Sends **SR creation only**. Return-to-normal / clear events are not sent
  (CBMS-11 in KAFD's requirements table is pending scope alignment).
- One SR per alarm occurrence; Maximo-side duplicate rules govern repeats.
- The AI-assisted tag-correction option discussed earlier cannot run inside
  the station JVM — that remains a future external service.

/*
 * GLSAN CBMS -> Maximo Alarm Sender (Niagara 4 Program object)
 * ------------------------------------------------------------
 * Scans the station alarm database for alarms in the *_Maximo_* alarm
 * classes and creates Maximo Service Requests (MXSR) through the KAFD
 * webMethods middleware (OAuth 2.0 client-credentials).
 *
 * Approval model ("accept / reject"):
 *   - ACCEPT  = operator ACKNOWLEDGES the alarm in the alarm console.
 *   - REJECT  = operator adds an alarm note containing "MAXIMO-SKIP".
 *   - CRITICAL alarms can bypass approval (AUTO_SEND_CRITICAL flag).
 *
 * Retry model:
 *   - 201  -> alarm is marked SENT with the returned SR ticketid.
 *   - 400  -> alarm is marked FAILED with the Maximo error (no retry;
 *             BMXAA4129E is flagged as DUPLICATE).
 *   - 5xx / timeout / no connection -> alarm is left UNMARKED, so the
 *             next scan cycle picks it up again automatically. The
 *             alarm database itself is the store-and-forward queue.
 *
 * This file is the source for a Niagara 4 Program object. Paste the
 * FIELDS + METHODS below into the Program editor (Edit tab), add the
 * imports on the Imports tab, and follow niagara/SETUP.md.
 *
 * SAFETY RULES (do not relax):
 *   - HTTP timeouts stay small (default 5 s). This code runs in the
 *     station JVM next to building control; it must never hang.
 *   - MAX_PER_CYCLE bounds work per execution.
 *   - Every per-alarm failure is caught; one bad alarm never stops the
 *     cycle or the station.
 *
 * NOTE ON API NAMES: written against the Niagara 4 baja/alarm API.
 * Exact class or method names can differ slightly between N4 versions;
 * the compiler in the Program editor will point at any mismatch —
 * SETUP.md section 9 lists the known variation points.
 */

// ---------------------------------------------------------------------------
// IMPORTS — add these on the Program object's "Imports" tab.
// Module dependencies: baja, alarm.
// ---------------------------------------------------------------------------
// import javax.baja.sys.*;
// import javax.baja.alarm.*;
// import javax.baja.collection.Cursor;   // some versions: javax.baja.util.Cursor
// import java.io.*;
// import java.net.*;
// import java.util.*;
// import java.util.regex.*;

// ===========================================================================
// CONFIG — EDIT THIS BLOCK ONLY (values from KAFD / middleware team)
// ===========================================================================

// Full MXSR endpoint through the middleware (final path from middleware team).
// Direct-Maximo dev endpoint for early tests:
//   https://masdev.manage.masdev.apps.ocpdev.kafd.sa/maximo/api/os/MXSR?lean=1
static final String MIDDLEWARE_URL   = "https://<middleware-host>/<mxsr-path-from-middleware-team>";

// OAuth 2.0 client credentials (from the KAFD developer portal).
static final String TOKEN_URL        = "https://<middleware-host>/<oauth-token-endpoint>";
static final String CLIENT_ID        = "<client-id>";
static final String CLIENT_SECRET    = "<client-secret>";
static final String OAUTH_SCOPE      = "";              // leave "" if not required

// Alarm class filter and severity -> Maximo classification mapping.
// Only sample value 1378 is confirmed; replace from the classifications
// list / GET API once mapped with the CAFM team.
static final String CLASS_MARKER     = "_MAXIMO_";      // matched inside alarm class name (upper-cased)
static final String CLASSID_CRITICAL = "1378";
static final String CLASSID_MAJOR    = "1378";
static final String CLASSID_MINOR    = "1378";

// Approval policy.
static final boolean AUTO_SEND_CRITICAL = true;         // CRITICAL skips the ack gate
static final String  REJECT_MARKER      = "MAXIMO-SKIP";// note text meaning "do not send"

// Reporter defaults for the MXSR payload.
static final String REPORTED_BY      = "BMS-USER";
static final String REPORTED_EMAIL   = "cbms@glsan.co";
static final String REPORT_PHONE     = "";
static final String AFFECTED_PERSON  = "";
static final String AFFECTED_EMAIL   = "";
static final String AFFECTED_PHONE   = "";

// Ticket id and time formatting.
static final String TICKET_PREFIX    = "BMS-";
static final String UTC_OFFSET       = "+03:00";        // KSA

// Engine-safety limits.
static final boolean ENABLED         = true;
static final int MAX_PER_CYCLE       = 5;               // alarms sent per execute()
static final int HTTP_TIMEOUT_MS     = 5000;            // connect AND read timeout

// ===========================================================================
// FIELDS (transient state — survives between executions, not restarts)
// ===========================================================================

String cachedToken = null;
long   tokenExpiryMs = 0;
long   sentCount = 0;
long   failedCount = 0;
String lastResult = "none";

// ===========================================================================
// LIFECYCLE
// ===========================================================================

public void onStart() throws Exception
{
  log("started. endpoint=" + MIDDLEWARE_URL + " autoSendCritical=" + AUTO_SEND_CRITICAL
      + " maxPerCycle=" + MAX_PER_CYCLE);
}

public void onStop() throws Exception
{
  log("stopped. sent=" + sentCount + " failed=" + failedCount);
}

// Called by the linked interval trigger (every 10-30 s). One bounded scan.
public void onExecute() throws Exception
{
  if (!ENABLED) return;

  BAlarmService service = (BAlarmService) Sys.getService(BAlarmService.TYPE);
  AlarmDbConnection conn = service.getAlarmDb().getDbConnection(null);
  try
  {
    int processed = 0;
    Cursor cursor = conn.getOpenAlarms();
    while (cursor.next())
    {
      if (processed >= MAX_PER_CYCLE) break;
      BAlarmRecord rec = (BAlarmRecord) cursor.get();
      try
      {
        if (!isMaximoClass(rec))   continue;   // not ours
        if (!isInAlarm(rec))       continue;   // RTN / normal — never send
        if (isHandled(rec))        continue;   // already SENT / FAILED / SKIPPED
        if (isRejected(rec))                   // operator said no — mark once, stop rechecking
        {
          mark(conn, rec, "SKIPPED", "", "rejected by operator note " + REJECT_MARKER);
          continue;
        }
        if (!isApproved(rec))      continue;   // waiting for operator ack (pending)

        processed++;
        sendOne(conn, rec);
      }
      catch (Exception perAlarm)
      {
        // Never let one alarm kill the cycle.
        log("ERROR on alarm " + rec.getUuid() + ": " + perAlarm);
      }
    }
  }
  finally
  {
    conn.close();
  }
}

// ===========================================================================
// FILTERS
// ===========================================================================

boolean isMaximoClass(BAlarmRecord rec)
{
  String cls = String.valueOf(rec.getAlarmClass());
  return cls.toUpperCase().indexOf(CLASS_MARKER) >= 0;
}

boolean isInAlarm(BAlarmRecord rec)
{
  BSourceState s = rec.getSourceState();
  return s.equals(BSourceState.offnormal) || s.equals(BSourceState.fault);
}

boolean isHandled(BAlarmRecord rec)
{
  // Anything already marked (SENT / FAILED / SKIPPED) is finished.
  return facet(rec, "maximoStatus").length() > 0;
}

boolean isRejected(BAlarmRecord rec)
{
  // Operator reject = alarm note containing REJECT_MARKER. Notes live in
  // alarm data; checking the whole facet string is version-tolerant.
  return String.valueOf(rec.getAlarmData()).indexOf(REJECT_MARKER) >= 0;
}

boolean isApproved(BAlarmRecord rec)
{
  // Accept = acknowledged in the alarm console.
  if (AUTO_SEND_CRITICAL && "CRITICAL".equals(severityOf(rec))) return true;
  return rec.getAckState().equals(BAckState.acked);
}

// ===========================================================================
// SEND ONE ALARM
// ===========================================================================

void sendOne(AlarmDbConnection conn, BAlarmRecord rec) throws Exception
{
  String severity = severityOf(rec);
  if (severity == null)
  {
    mark(conn, rec, "FAILED", "", "alarm class has no CRITICAL/MAJOR/MINOR suffix: " + rec.getAlarmClass());
    return;
  }

  String payload = buildPayload(rec, severity);

  String token;
  try { token = getToken(); }
  catch (Exception e)
  {
    // Token endpoint unreachable -> retryable. Leave alarm unmarked.
    log("token error (will retry): " + e.getMessage());
    return;
  }

  String[] r = httpPost(MIDDLEWARE_URL, payload, "application/json", token);
  int code = Integer.parseInt(r[0]);

  // One retry on 401: token may have been revoked before its expiry.
  if (code == 401)
  {
    cachedToken = null;
    token = getToken();
    r = httpPost(MIDDLEWARE_URL, payload, "application/json", token);
    code = Integer.parseInt(r[0]);
  }

  if (code == 201)
  {
    String sr = jsonString(r[1], "ticketid");
    if (sr == null) sr = jsonString(payload, "ticketid"); // fall back to what we sent
    sentCount++;
    lastResult = "201 SR " + sr;
    mark(conn, rec, "SENT", sr, "");
    log("SENT " + sr + " asset=" + sourceName(rec) + " sev=" + severity);
  }
  else if (code >= 400 && code < 500)
  {
    boolean dup = r[1].indexOf("BMXAA4129E") >= 0;
    failedCount++;
    lastResult = code + (dup ? " DUPLICATE" : " FAILED");
    mark(conn, rec, dup ? "DUPLICATE" : "FAILED", "", "HTTP " + code + ": " + truncate(r[1], 300));
    log("FAILED HTTP " + code + " asset=" + sourceName(rec) + " body=" + truncate(r[1], 200));
  }
  else
  {
    // 5xx or anything unexpected -> retryable, leave unmarked.
    lastResult = code + " retrying";
    log("RETRYABLE HTTP " + code + " asset=" + sourceName(rec) + " — will retry next cycle");
  }
}

// ===========================================================================
// PAYLOAD (exact MXSR structure agreed with KAFD — do not add/remove fields)
// ===========================================================================

String buildPayload(BAlarmRecord rec, String severity)
{
  String source = sourceName(rec);                       // resolved SourceName = CBMS tag
  String msg = facet(rec, "msgText");
  if (msg.length() == 0) msg = "Point is in " + rec.getSourceState() + " state";
  String shortDesc = truncate(severity + " alarm: " + source, 100);
  String ticketid = TICKET_PREFIX + nextTicketSeq();

  StringBuffer b = new StringBuffer();
  b.append("{");
  jsonField(b, "ticketid", ticketid, true);
  jsonField(b, "reportedby", REPORTED_BY, true);
  jsonField(b, "reportdate", reportDate(rec.getTimestamp()), true);
  jsonField(b, "reportedemail", REPORTED_EMAIL, true);
  jsonField(b, "bmsassetcode", source, true);
  jsonField(b, "assetnum", "", true);                    // Maximo maps bmsassetcode internally
  jsonField(b, "reportphone", REPORT_PHONE, true);
  jsonField(b, "affectedperson", AFFECTED_PERSON, true);
  jsonField(b, "affectedemail", AFFECTED_EMAIL, true);
  jsonField(b, "affectedphone", AFFECTED_PHONE, true);
  jsonField(b, "description", shortDesc, true);
  jsonField(b, "classstructureid", classIdOf(severity), true);
  jsonField(b, "description_longdescription", msg, false);
  b.append("}");
  return b.toString();
}

String sourceName(BAlarmRecord rec)
{
  // The alarm extension's Source Name BFormat (%parent.parent.displayName%)
  // is resolved at alarm generation and stored in alarm data.
  String s = facet(rec, "sourceName");
  return s.length() > 0 ? s : String.valueOf(rec.getSource());
}

String severityOf(BAlarmRecord rec)
{
  String cls = String.valueOf(rec.getAlarmClass()).trim();
  String[] parts = cls.split("_");
  String last = parts[parts.length - 1].toUpperCase();
  if (last.equals("CRITICAL") || last.equals("CRTICAL") || last.equals("CRITCAL")) return "CRITICAL";
  if (last.equals("MAJOR")) return "MAJOR";
  if (last.equals("MINOR")) return "MINOR";
  return null;
}

String classIdOf(String severity)
{
  if (severity.equals("CRITICAL")) return CLASSID_CRITICAL;
  if (severity.equals("MAJOR"))    return CLASSID_MAJOR;
  return CLASSID_MINOR;
}

// "2026-07-14T10:30:00+03:00" — CBMS-formatted local time, NOT sysdate.
String reportDate(BAbsTime t)
{
  Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT" + UTC_OFFSET));
  cal.setTimeInMillis(t.getMillis());
  return String.format("%04d-%02d-%02dT%02d:%02d:%02d%s",
      new Object[] {
        new Integer(cal.get(Calendar.YEAR)),
        new Integer(cal.get(Calendar.MONTH) + 1),
        new Integer(cal.get(Calendar.DAY_OF_MONTH)),
        new Integer(cal.get(Calendar.HOUR_OF_DAY)),
        new Integer(cal.get(Calendar.MINUTE)),
        new Integer(cal.get(Calendar.SECOND)),
        UTC_OFFSET });
}

// Persistent ticket sequence, stored as a dynamic slot on this Program
// component so it survives station restarts (saved with the station).
long nextTicketSeq()
{
  BComponent comp = getComponent();
  BObject cur = comp.get("maximoTicketSeq");
  long next = 1;
  if (cur instanceof BNumber) next = (long) ((BNumber) cur).getInt() + 1;
  if (cur == null) comp.add("maximoTicketSeq", BInteger.make((int) next));
  else comp.set("maximoTicketSeq", BInteger.make((int) next));
  return next;
}

// ===========================================================================
// RESULT WRITE-BACK — operator sees the SR number in the alarm console
// ===========================================================================

void mark(AlarmDbConnection conn, BAlarmRecord rec, String status, String sr, String error) throws Exception
{
  BFacets add = BFacets.make(
      new String[]  { "maximoStatus", "maximoSr", "maximoError" },
      new BObject[] { BString.make(status), BString.make(sr), BString.make(truncate(error, 300)) });
  rec.setAlarmData(BFacets.make(rec.getAlarmData(), add));
  conn.update(rec);
}

String facet(BAlarmRecord rec, String key)
{
  BObject o = rec.getAlarmData().get(key);
  return o == null ? "" : o.toString();
}

// ===========================================================================
// OAUTH 2.0 (client credentials, token cached until 60 s before expiry)
// ===========================================================================

String getToken() throws Exception
{
  long now = System.currentTimeMillis();
  if (cachedToken != null && now < tokenExpiryMs - 60000) return cachedToken;

  String form = "grant_type=client_credentials"
      + "&client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8")
      + "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, "UTF-8");
  if (OAUTH_SCOPE.length() > 0) form += "&scope=" + URLEncoder.encode(OAUTH_SCOPE, "UTF-8");

  String[] r = httpPost(TOKEN_URL, form, "application/x-www-form-urlencoded", null);
  if (!r[0].equals("200"))
    throw new Exception("token HTTP " + r[0] + ": " + truncate(r[1], 200));

  String token = jsonString(r[1], "access_token");
  if (token == null) throw new Exception("no access_token in token response");

  long expiresIn = 300;
  String e = jsonNumber(r[1], "expires_in");
  if (e != null) expiresIn = Long.parseLong(e);

  cachedToken = token;
  tokenExpiryMs = now + expiresIn * 1000;
  log("OAuth token refreshed, expires in " + expiresIn + "s");
  return token;
}

// ===========================================================================
// HTTP (small timeouts — never block the station engine)
// ===========================================================================

String[] httpPost(String urlStr, String body, String contentType, String bearer) throws Exception
{
  HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
  c.setConnectTimeout(HTTP_TIMEOUT_MS);
  c.setReadTimeout(HTTP_TIMEOUT_MS);
  c.setRequestMethod("POST");
  c.setDoOutput(true);
  c.setRequestProperty("Content-Type", contentType);
  c.setRequestProperty("Accept", "application/json");
  if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);

  byte[] bytes = body.getBytes("UTF-8");
  OutputStream os = c.getOutputStream();
  try { os.write(bytes); } finally { os.close(); }

  int code = c.getResponseCode();
  InputStream is = (code >= 400) ? c.getErrorStream() : c.getInputStream();
  String resp = readAll(is);
  c.disconnect();
  return new String[] { String.valueOf(code), resp };
}

String readAll(InputStream is) throws Exception
{
  if (is == null) return "";
  BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
  StringBuffer b = new StringBuffer();
  try
  {
    String line;
    while ((line = r.readLine()) != null) { b.append(line); if (b.length() > 8000) break; }
  }
  finally { r.close(); }
  return b.toString();
}

// ===========================================================================
// SMALL HELPERS
// ===========================================================================

void jsonField(StringBuffer b, String key, String value, boolean comma)
{
  b.append('"').append(key).append("\":\"").append(jsonEscape(value)).append('"');
  if (comma) b.append(',');
}

String jsonEscape(String s)
{
  if (s == null) return "";
  StringBuffer b = new StringBuffer();
  for (int i = 0; i < s.length(); i++)
  {
    char ch = s.charAt(i);
    if (ch == '"') b.append("\\\"");
    else if (ch == '\\') b.append("\\\\");
    else if (ch == '\n') b.append("\\n");
    else if (ch == '\r') b.append("\\r");
    else if (ch == '\t') b.append("\\t");
    else if (ch < 0x20) b.append(String.format("\\u%04x", new Object[] { new Integer(ch) }));
    else b.append(ch);
  }
  return b.toString();
}

String jsonString(String json, String key)
{
  Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
  return m.find() ? m.group(1) : null;
}

String jsonNumber(String json, String key)
{
  Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9]+)").matcher(json);
  return m.find() ? m.group(1) : null;
}

String truncate(String s, int max)
{
  if (s == null) return "";
  return s.length() <= max ? s : s.substring(0, max) + "...";
}

void log(String msg)
{
  System.out.println("[MaximoSender] " + BAbsTime.now() + " " + msg);
}

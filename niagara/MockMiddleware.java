import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Mock KAFD middleware for bench-testing the MaximoAlarmSender Program
 * object on a local PC — no external dependencies, plain HTTP.
 *
 * Endpoints:
 *   POST /oauth/token            -> 200 {"access_token":"mock-token","expires_in":300}
 *   POST /maximo/api/os/MXSR     -> 201 {"ticketid":"<echoed>"}    (normal case)
 *
 * Test triggers (based on the bmsassetcode in the payload):
 *   contains "BADASSET"  -> 400 invalid asset
 *   contains "FAIL500"   -> 500 server error (sender must retry)
 *   repeated ticketid    -> 400 BMXAA4129E duplicate
 *   wrong/missing Bearer -> 401 (sender must refresh token and retry once)
 *
 * Compile and run with the Niagara JRE (adjust the path to your version):
 *   "c:\niagara\niagara-4.14.2.12\jre\bin\javac" MockMiddleware.java
 *   "c:\niagara\niagara-4.14.2.12\jre\bin\java"  MockMiddleware
 *
 * Then set the Program's slots to:
 *   middlewareUrl = http://localhost:8099/maximo/api/os/MXSR
 *   tokenUrl      = http://localhost:8099/oauth/token
 *   clientId      = test        clientSecret = test
 *
 * Optional: pass a port as the first argument (default 8099).
 */
public class MockMiddleware
{
  static final String TOKEN = "mock-token-12345";
  static final Set seenTickets = Collections.synchronizedSet(new HashSet());
  static final Map pollCounts = Collections.synchronizedMap(new HashMap());
  static int srCounter = 0;
  static int reqCounter = 0;

  // Payload fields printed one per line, in the agreed MXSR order.
  static final String[] MXSR_FIELDS = {
    "ticketid","reportedby","reportdate","reportedemail","bmsassetcode",
    "assetnum","reportphone","affectedperson","affectedemail","affectedphone",
    "description","classstructureid","description_longdescription" };

  static final String LINE  = "==============================================================";
  static final String THIN  = "--------------------------------------------------------------";

  public static void main(String[] args) throws Exception
  {
    int port = args.length > 0 ? Integer.parseInt(args[0]) : 8099;
    ServerSocket server = new ServerSocket(port);
    System.out.println(LINE);
    System.out.println("   KAFD  MAXIMO  MIDDLEWARE   --   TEST SIMULATOR  (GLSAN CBMS)");
    System.out.println(LINE);
    System.out.println("   Listening on http://localhost:" + port + "          " + now());
    System.out.println("   OAuth 2.0 token endpoint : POST /oauth/token");
    System.out.println("   Create SR (MXSR)         : POST /maximo/api/os/MXSR");
    System.out.println("   SR status query          : GET  /maximo/api/os/MXSR?ticketid=...");
    System.out.println(LINE);
    System.out.println("   Waiting for Service Requests from CBMS ...");
    while (true)
    {
      final Socket sock = server.accept();
      new Thread(new Runnable() { public void run() { handle(sock); } }).start();
    }
  }

  static void handle(Socket sock)
  {
    try
    {
      sock.setSoTimeout(10000);
      BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
      OutputStream out = sock.getOutputStream();

      String requestLine = in.readLine();
      if (requestLine == null) { sock.close(); return; }
      String[] parts = requestLine.split(" ");
      String method = parts[0];
      String path = parts.length > 1 ? parts[1] : "/";

      // headers
      int contentLength = 0;
      String auth = null;
      String line;
      while ((line = in.readLine()) != null && line.length() > 0)
      {
        String lower = line.toLowerCase();
        if (lower.startsWith("content-length:"))
          contentLength = Integer.parseInt(line.substring(15).trim());
        if (lower.startsWith("authorization:"))
          auth = line.substring(14).trim();
      }

      // body
      char[] buf = new char[contentLength];
      int read = 0;
      while (read < contentLength)
      {
        int n = in.read(buf, read, contentLength - read);
        if (n < 0) break;
        read += n;
      }
      String body = new String(buf, 0, read);

      if (method.equals("POST") && path.startsWith("/oauth/token"))
      {
        System.out.println();
        System.out.println("  >> OAuth 2.0 token issued to CBMS  (" + now() + ")");
        respond(out, 200, "{\"access_token\":\"" + TOKEN + "\",\"token_type\":\"Bearer\",\"expires_in\":300}");
      }
      else if (method.equals("POST") && path.startsWith("/maximo/api/os/MXSR"))
      {
        handleMxsr(out, auth, body);
      }
      else if (method.equals("GET") && path.startsWith("/maximo/api/os/MXSR"))
      {
        // status query: GET /maximo/api/os/MXSR?ticketid=BMS-n
        String tid = null;
        int q = path.indexOf("ticketid=");
        if (q >= 0)
        {
          tid = path.substring(q + 9);
          int amp = tid.indexOf('&');
          if (amp >= 0) tid = tid.substring(0, amp);
          tid = URLDecoder.decode(tid, "UTF-8");
        }
        handleStatus(out, auth, tid);
      }
      else
      {
        respond(out, 404, "{\"error\":\"not found\"}");
      }
      sock.close();
    }
    catch (Exception e)
    {
      log("connection error: " + e);
      try { sock.close(); } catch (Exception ignore) {}
    }
  }

  static void handleMxsr(OutputStream out, String auth, String body) throws Exception
  {
    reqCounter++;
    System.out.println();
    System.out.println(LINE);
    System.out.println("  [" + reqCounter + "] SERVICE REQUEST RECEIVED FROM CBMS        " + now());
    System.out.println(THIN);
    for (int i = 0; i < MXSR_FIELDS.length; i++)
    {
      String v = jsonValue(body, MXSR_FIELDS[i]);
      System.out.println("    " + pad(MXSR_FIELDS[i], 28) + ": " + (v == null ? "" : v));
    }
    System.out.println(THIN);

    if (auth == null || !auth.equals("Bearer " + TOKEN))
    {
      System.out.println("    RESULT :  401 UNAUTHORIZED - invalid or expired token");
      System.out.println(LINE);
      respond(out, 401, "{\"Error\":{\"message\":\"invalid or expired token\"}}");
      return;
    }

    String ticketid = jsonValue(body, "ticketid");
    String asset = jsonValue(body, "bmsassetcode");

    if (asset == null || asset.length() == 0)
    {
      System.out.println("    RESULT :  400 REJECTED - required field bmsassetcode missing");
      respond(out, 400, "{\"Error\":{\"message\":\"BMXAA4195E - required field bmsassetcode\"}}");
    }
    else if (asset.indexOf("BADASSET") >= 0)
    {
      System.out.println("    RESULT :  400 REJECTED - no Maximo asset mapping for this CBMS tag");
      respond(out, 400, "{\"Error\":{\"message\":\"Invalid asset - no mapping for " + asset + "\"}}");
    }
    else if (asset.indexOf("FAIL500") >= 0)
    {
      System.out.println("    RESULT :  500 SERVER ERROR - CBMS will retry automatically");
      respond(out, 500, "{\"Error\":{\"message\":\"internal server error\"}}");
    }
    else if (ticketid != null && !seenTickets.add(ticketid))
    {
      System.out.println("    RESULT :  400 DUPLICATE (BMXAA4129E) - ticket " + ticketid + " already exists");
      respond(out, 400, "{\"Error\":{\"message\":\"BMXAA4129E - record already exists for ticketid " + ticketid + "\"}}");
    }
    else
    {
      srCounter++;
      System.out.println("    RESULT :  201 CREATED  ->  Service Request " + ticketid
          + "   (total created: " + srCounter + ")");
      respond(out, 201, "{\"ticketid\":\"" + ticketid + "\",\"status\":\"NEW\"}");
    }
    System.out.println(LINE);
  }

  static String pad(String s, int w)
  {
    StringBuffer b = new StringBuffer(s);
    while (b.length() < w) b.append(' ');
    return b.toString();
  }

  static String now()
  {
    return new java.text.SimpleDateFormat("HH:mm:ss  dd-MMM-yyyy").format(new Date());
  }

  // Returns a status that advances on every poll: NEW -> INPRG -> COMP,
  // so the sender's status refresh can be watched progressing.
  static void handleStatus(OutputStream out, String auth, String tid) throws Exception
  {
    if (auth == null || !auth.equals("Bearer " + TOKEN))
    {
      log("  -> 401 (bad/missing token on status query)");
      respond(out, 401, "{\"Error\":{\"message\":\"invalid or expired token\"}}");
      return;
    }
    if (tid == null || !seenTickets.contains(tid))
    {
      log("  -> 404 (status query for unknown ticketid " + tid + ")");
      respond(out, 404, "{\"Error\":{\"message\":\"no SR found for ticketid " + tid + "\"}}");
      return;
    }
    Integer n = (Integer) pollCounts.get(tid);
    int count = n == null ? 0 : n.intValue();
    pollCounts.put(tid, new Integer(count + 1));
    String status = count == 0 ? "NEW" : count == 1 ? "INPRG" : "COMP";
    System.out.println();
    System.out.println("  >> STATUS QUERY  " + tid + "  ->  " + status + "   (" + now() + ")");
    respond(out, 200, "{\"ticketid\":\"" + tid + "\",\"status\":\"" + status + "\"}");
  }

  static void respond(OutputStream out, int code, String json) throws Exception
  {
    String status = code == 200 ? "OK" : code == 201 ? "Created"
        : code == 400 ? "Bad Request" : code == 401 ? "Unauthorized"
        : code == 404 ? "Not Found" : "Server Error";
    byte[] bytes = json.getBytes("UTF-8");
    String head = "HTTP/1.1 " + code + " " + status + "\r\n"
        + "Content-Type: application/json\r\n"
        + "Content-Length: " + bytes.length + "\r\n"
        + "Connection: close\r\n\r\n";
    out.write(head.getBytes("UTF-8"));
    out.write(bytes);
    out.flush();
  }

  static String jsonValue(String json, String key)
  {
    if (json == null) return null;
    int k = json.indexOf("\"" + key + "\"");
    if (k < 0) return null;
    int colon = json.indexOf(':', k);
    int q1 = json.indexOf('"', colon + 1);
    int q2 = json.indexOf('"', q1 + 1);
    if (q1 < 0 || q2 < 0) return null;
    return json.substring(q1 + 1, q2);
  }

  static String truncate(String s, int max)
  {
    return s.length() <= max ? s : s.substring(0, max) + "...";
  }

  static void log(String msg)
  {
    System.out.println("[mock] " + new Date() + " " + msg);
  }
}

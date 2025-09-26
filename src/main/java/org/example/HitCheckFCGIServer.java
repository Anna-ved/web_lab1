//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.example;

import com.fastcgi.FCGIInterface;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class HitCheckFCGIServer {
    private static final Queue<Map<String, String>> queue = new ConcurrentLinkedQueue();
    private static final int MaxHistory = 10;

    public static void main(String[] args) {
        FCGIInterface fcgi = new FCGIInterface();
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.err.println("FCGI Server started and waiting for connections...");

        while(fcgi.FCGIaccept() >= 0) {
            long startTime = System.nanoTime();

            try {
                processRequest(startTime);
            } catch (Exception e) {
                System.err.println("Error processing request: " + e.getMessage());
                sendError("Internal server error");
            }
        }

    }

    public static void processRequest(long startTime) {
        try {
            String queryString = FCGIInterface.request.params.getProperty("QUERY_STRING");
            if (queryString == null || queryString.isEmpty()) {
                sendError("Not found");
                return;
            }

            Map<String, String> map = parseQuery(queryString);
            String rStr = (String)map.get("r");
            String xStr = (String)map.get("x");
            String yStr = (String)map.get("y");
            if (rStr == null || xStr == null || yStr == null) {
                sendError("Not found coordination");
                return;
            }

            double r = Double.parseDouble(rStr);
            double x = Double.parseDouble(xStr);
            double y = Double.parseDouble(yStr);
            if (r < (double)0.0F) {
                sendError("R are not valid");
                return;
            }

            boolean hit = checkHit(x, y, r);
            Map<String, String> result = new HashMap();
            result.put("r", rStr);
            result.put("x", xStr);
            result.put("y", yStr);
            result.put("hit", Boolean.toString(hit));
            result.put("timestamp", (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(new Date()));
            synchronized(queue) {
                queue.add(result);
                if (queue.size() > 10) {
                    queue.poll();
                }
            }

            sendJsonResponse(result, startTime);
        } catch (NumberFormatException var18) {
            sendError("Invalid number format");
        } catch (Exception var19) {
            sendError("Processing error");
        }

    }

    private static void sendJsonResponse(Map<String, String> currentResult, long startTime) {
        try {
            StringBuilder content = new StringBuilder();
            content.append("{\n");
            content.append("  \"current\": {\n");
            content.append("    \"r\": \"").append((String)currentResult.get("r")).append("\",\n");
            content.append("    \"x\": \"").append((String)currentResult.get("x")).append("\",\n");
            content.append("    \"y\": \"").append((String)currentResult.get("y")).append("\",\n");
            content.append("    \"hit\": ").append((String)currentResult.get("hit")).append(",\n");
            content.append("    \"timestamp\": \"").append((String)currentResult.get("timestamp")).append("\"\n");
            content.append("  },\n");
            content.append("  \"history\": [\n");
            synchronized(queue) {
                for(Iterator<Map<String, String>> iterator = queue.iterator(); iterator.hasNext(); content.append("\n")) {
                    Map<String, String> result = (Map)iterator.next();
                    content.append("    {\n");
                    content.append("      \"r\": \"").append((String)result.get("r")).append("\",\n");
                    content.append("      \"x\": \"").append((String)result.get("x")).append("\",\n");
                    content.append("      \"y\": \"").append((String)result.get("y")).append("\",\n");
                    content.append("      \"hit\": ").append((String)result.get("hit")).append(",\n");
                    content.append("      \"timestamp\": \"").append((String)result.get("timestamp")).append("\"\n");
                    content.append("    }");
                    if (iterator.hasNext()) {
                        content.append(",");
                    }
                }
            }

            content.append("  ],\n");
            content.append("  \"workTime\": \"").append(String.format(Locale.US, "%.2f", (double)(System.nanoTime() - startTime) / (double)1000000.0F)).append("\",\n");
            content.append("  \"error\": \"all ok\"\n");
            content.append("}");
            String contentStr = content.toString();
            byte[] contentBytes = contentStr.getBytes(StandardCharsets.UTF_8);
            System.out.println("Content-Type: application/json; charset=utf-8");
            System.out.println("Content-Length: " + contentBytes.length);
            System.out.println("Status: 200 OK");
            System.out.println();
            System.out.print(contentStr);
            System.out.flush();
        } catch (Exception e) {
            System.err.println("Error sending response: " + e.getMessage());
        }

    }

    public static void sendError(String message) {
        try {
            String content = "{\"error\":\"" + message + "\"}";
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            System.out.println("Content-Type: application/json; charset=utf-8");
            System.out.println("Content-Length: " + contentBytes.length);
            System.out.println("Status: 400 Bad Request");
            System.out.println();
            System.out.print(content);
            System.out.flush();
        } catch (Exception e) {
            System.err.println("Error sending error response: " + e.getMessage());
        }

    }

    public static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap();
        String[] pairs = query.split("&");

        for(String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                map.put(keyValue[0], keyValue[1]);
            }
        }

        return map;
    }

    public static boolean checkHit(double x, double y, double r) {
        return isRect(x, y, r) || isTriangle(x, y, r) || isCircle(x, y, r);
    }

    private static boolean isTriangle(double x, double y, double r) {
        return x >= (double)0.0F && x <= r && y >= (double)0.0F && y <= r / (double)2.0F && y >= x / (double)2.0F;
    }

    private static boolean isRect(double x, double y, double r) {
        return x <= (double)0.0F && y >= (double)0.0F && x >= -r / (double)2.0F && y <= r;
    }

    private static boolean isCircle(double x, double y, double r) {
        return x >= (double)0.0F && x <= r / (double)2.0F && y <= (double)0.0F && y >= -r / (double)2.0F && x * x + y * y <= r / (double)2.0F * r / (double)2.0F;
    }
}

package org.example;

import com.fastcgi.FCGIInterface;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class HitCheckFCGIServer {

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
            if (r < 0.0) {
                sendError("R are not valid");
                return;
            }

            boolean hit = checkHit(x, y, r);

            // Вычисляем время выполнения с полной точностью
            long endTime = System.nanoTime();
            double workTime = (endTime - startTime) / 1000000.0; // в миллисекундах

            Map<String, String> result = new HashMap();
            result.put("r", rStr);
            result.put("x", xStr);
            result.put("y", yStr);
            result.put("hit", Boolean.toString(hit));
            result.put("timestamp", (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(new Date()));

            sendJsonResponse(result, workTime);
        } catch (NumberFormatException var18) {
            sendError("Invalid number format");
        } catch (Exception var19) {
            sendError("Processing error");
        }
    }

    private static void sendJsonResponse(Map<String, String> currentResult, double workTime) {
        try {
            StringBuilder content = new StringBuilder();
            content.append("{\n");
            content.append("  \"current\": {\n");
            content.append("    \"r\": \"").append((String)currentResult.get("r")).append("\",\n");
            content.append("    \"x\": \"").append((String)currentResult.get("x")).append("\",\n");
            content.append("    \"y\": \"").append((String)currentResult.get("y")).append("\",\n");
            content.append("    \"hit\": ").append((String)currentResult.get("hit")).append(",\n");
            content.append("    \"timestamp\": \"").append((String)currentResult.get("timestamp")).append("\",\n");
            // WorkTime с полной точностью (без форматирования)
            content.append("    \"workTime\": ").append(workTime).append("\n");
            content.append("  },\n");
            // Убрана history
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
        return x >= 0.0 && x <= r && y >= 0.0 && y <= r / 2.0 && y >= x / 2.0;
    }

    private static boolean isRect(double x, double y, double r) {
        return x <= 0.0 && y >= 0.0 && x >= -r / 2.0 && y <= r;
    }

    private static boolean isCircle(double x, double y, double r) {
        return x >= 0.0 && y <= 0.0 && x * x + y * y <= r / 2.0 * r / 2.0;
    }
}
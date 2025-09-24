package org.example;

import com.fastcgi.FCGIInterface;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.logging.Logger;

public class HitCheckFCGIServer {
    private static final Set<Double> VALID_X_VALUES = Set.of(-5.0, -4.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0);
    private static final Set<Double> VALID_R_VALUES = Set.of(1.0, 1.5, 2.0, 2.5, 3.0);
    private static final int MIN_Y = -3;
    private static final int MAX_Y = 5;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = Logger.getLogger(HitCheckFCGIServer.class.getName());

    public static void main(String[] args) throws SQLException, ClassNotFoundException {
        ResultManager.connect();
        FCGIInterface fcgi = new FCGIInterface();
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        log.info("FCGI Server started and waiting for connections...");

        while(fcgi.FCGIaccept() >= 0) {
            long startTime = System.nanoTime();
            try {
                processRequest(startTime);
            } catch (Exception e) {
                log.warning("Error processing request: " + e.getMessage());
                sendError("Internal server error");
            }
        }
    }

    public static void processRequest(long startTime) {
        try {
            String requestMethod = FCGIInterface.request.params.getProperty("REQUEST_METHOD");
            if (!"GET".equalsIgnoreCase(requestMethod)) {
                sendError("Method not allowed. Only GET requests are supported.");
                return;
            }

            String queryString = FCGIInterface.request.params.getProperty("QUERY_STRING");
            if (queryString == null || queryString.isEmpty()) {
                sendError("Not found");
                return;
            }

            Map<String, List<String>> map = parseQuery(queryString);
            String action = getSingleParameter(map, "action");
            if ("track_user".equals(action)) {
                processFingerprintRequest(map, startTime);
                return;
            }

            List<String> xValues = map.get("x");
            String rStr = getSingleParameter(map, "r");
            String yStr = getSingleParameter(map, "y");

            if (xValues == null || xValues.isEmpty() || rStr == null || yStr == null) {
                sendError("Not found coordination");
                return;
            }

            double r;
            try {
                r = Double.parseDouble(rStr);
                if (!VALID_R_VALUES.contains(r)) {
                    sendError("Invalid R value. Allowed values: 1, 1.5, 2, 2.5, 3");
                    return;
                }
            } catch (NumberFormatException e) {
                sendError("Invalid R format");
                return;
            }

            int y;
            try {
                y = Integer.parseInt(yStr);
                if (y < MIN_Y || y > MAX_Y) {
                    sendError("Y must be an integer between -3 and 5");
                    return;
                }
            } catch (NumberFormatException e) {
                sendError("Y must be an integer between -3 and 5");
                return;
            }

            List<Double> xList = new ArrayList<>();
            for (String xStr : xValues) {
                try {
                    double x = Double.parseDouble(xStr);
                    if (!VALID_X_VALUES.contains(x)) {
                        sendError("Invalid X value. Allowed values: -5, -4, -3, -2, -1, 0, 1, 2, 3");
                        return;
                    }
                    xList.add(x);
                } catch (NumberFormatException e) {
                    sendError("Invalid X format");
                    return;
                }
            }

            long endTime = System.nanoTime();
            double workTime = (endTime - startTime) / 1000000.0;

            List<Map<String, String>> results = new ArrayList<>();

            for (double x : xList) {
                boolean hit = checkHit(x, y, r);

                Map<String, String> result = new HashMap<>();
                result.put("r", String.valueOf(r));
                result.put("x", String.valueOf(x));
                result.put("y", String.valueOf(y));
                result.put("hit", Boolean.toString(hit));
                result.put("timestamp", (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(new Date()));

                ResultManager.insertResult((int)x, y, (float)r, (float)workTime, hit);
                results.add(result);
            }

            sendJsonResponse(results, workTime);
        } catch (NumberFormatException e) {
            sendError("Invalid number format");
        } catch (Exception e) {
            sendError("Processing error: " + e.getMessage());
        }
    }

    private static void processFingerprintRequest(Map<String, List<String>> map, long startTime) {
        try {
            String fingerprintJson = getSingleParameter(map, "fingerprint");

            if (fingerprintJson != null) {
                fingerprintJson = java.net.URLDecoder.decode(fingerprintJson, StandardCharsets.UTF_8);
                parseAndSaveFingerprint(fingerprintJson);
            }

            long endTime = System.nanoTime();
            double workTime = (endTime - startTime) / 1000000.0;
            Map<String, String> result = new HashMap<>();
            result.put("status", "tracked");
            result.put("message", "Fingerprint received successfully");
            result.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

            List<Map<String, String>> results = new ArrayList<>();
            results.add(result);

            sendJsonResponse(results, workTime);

        } catch (Exception e) {
            log.warning("Error processing fingerprint: " + e.getMessage());
            sendError("Fingerprint processing error");
        }
    }

    private static Map<String, Object> parseJson(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return new HashMap<>();
            }

            String cleanJson = json.trim();
            if (cleanJson.startsWith("\"") && cleanJson.endsWith("\"")) {
                cleanJson = cleanJson.substring(1, cleanJson.length() - 1);
            }

            return objectMapper.readValue(cleanJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warning("Error parsing JSON: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private static void parseAndSaveFingerprint(String fingerprintJson) {
        try {
            Map<String, Object> fingerprintData = parseJson(fingerprintJson);
            String userAgent = (String) fingerprintData.get("userAgent");
            String language = (String) fingerprintData.get("language");
            String platform = (String) fingerprintData.get("platform");
            Integer hardwareConcurrency = getIntegerValue(fingerprintData, "hardwareConcurrency");
            Integer deviceMemory = getIntegerValue(fingerprintData, "deviceMemory");
            String timezone = (String) fingerprintData.get("timezone");
            String canvasFingerprint = (String) fingerprintData.get("canvasFingerprint");

            Integer screenWidth = null;
            Integer screenHeight = null;
            @SuppressWarnings("unchecked")
            Map<String, Object> screenData = (Map<String, Object>) fingerprintData.get("screen");
            if (screenData != null) {
                screenWidth = getIntegerValue(screenData, "width");
                screenHeight = getIntegerValue(screenData, "height");
            }

            String fonts = null;
            @SuppressWarnings("unchecked")
            List<String> fontsList = (List<String>) fingerprintData.get("fonts");
            if (fontsList != null && !fontsList.isEmpty()) {
                fonts = String.join(",", fontsList);
            }

            String webRTCInfo = null;
            @SuppressWarnings("unchecked")
            Map<String, Object> webRTCData = (Map<String, Object>) fingerprintData.get("webRTC");
            if (webRTCData != null) {
                webRTCInfo = objectMapper.writeValueAsString(webRTCData);
            }

            String clientHints = null;
            @SuppressWarnings("unchecked")
            Map<String, Object> clientHintsData = (Map<String, Object>) fingerprintData.get("clientHints");
            if (clientHintsData != null) {
                clientHints = objectMapper.writeValueAsString(clientHintsData);
            }

            String http2Info = null;
            @SuppressWarnings("unchecked")
            Map<String, Object> http2Data = (Map<String, Object>) fingerprintData.get("http2");
            if (http2Data != null) {
                http2Info = objectMapper.writeValueAsString(http2Data);
            }

            String ipAddress = FCGIInterface.request.params.getProperty("REMOTE_ADDR");

            ResultManager.insertDetailedFingerprint(
                    userAgent, language, platform, hardwareConcurrency, deviceMemory,
                    screenWidth, screenHeight, timezone, canvasFingerprint, fonts,
                    webRTCInfo, clientHints, http2Info, ipAddress
            );

            log.info("Fingerprint saved to database");

        } catch (Exception e) {
            log.warning("Error parsing fingerprint: " + e.getMessage());
        }
    }

    private static Integer getIntegerValue(Map<String, Object> data, String key) {
        try {
            Object value = data.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                return Integer.parseInt((String) value);
            }
        } catch (Exception e) {
            log.warning("Error parsing fingerprint: " + e.getMessage());
        }
        return null;
    }

    private static String getSingleParameter(Map<String, List<String>> map, String paramName) {
        List<String> values = map.get(paramName);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return null;
    }

    private static void sendJsonResponse(List<Map<String, String>> results, double workTime) {
        try {
            StringBuilder content = new StringBuilder();
            content.append("{\n");
            content.append("  \"current\": [\n");

            for (int i = 0; i < results.size(); i++) {
                Map<String, String> result = results.get(i);
                content.append("    {\n");
                content.append("      \"r\": \"").append(result.get("r")).append("\",\n");
                content.append("      \"x\": \"").append(result.get("x")).append("\",\n");
                content.append("      \"y\": \"").append(result.get("y")).append("\",\n");
                content.append("      \"hit\": ").append(result.get("hit")).append(",\n");
                content.append("      \"timestamp\": \"").append(result.get("timestamp")).append("\",\n");
                content.append("      \"workTime\": ").append(workTime).append("\n");
                content.append("    }");
                if (i < results.size() - 1) {
                    content.append(",");
                }
                content.append("\n");
            }

            content.append("  ],\n");
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

    public static Map<String, List<String>> parseQuery(String query) {
        Map<String, List<String>> map = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return map;
        }

        String[] pairs = query.split("&");

        for(String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                String key = keyValue[0];
                String value = keyValue[1];

                map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
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
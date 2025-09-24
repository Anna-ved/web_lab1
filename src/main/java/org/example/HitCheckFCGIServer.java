package org.example;

import com.fastcgi.FCGIInterface;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.nio.charset.StandardCharsets;

public class HitCheckFCGIServer {
    private static final Queue<Map<String, String>> queue = new ConcurrentLinkedQueue<>();
    private static final int MaxHistory = 10;
    private static boolean isFCGIMode;

    public static void main(String[] args) {
        isFCGIMode = System.getProperty("FCGIMode") != null;
        if (isFCGIMode) {
            FCGIInterface fcgi = new FCGIInterface();
            while (fcgi.FCGIaccept() >= 0) {
                long startTime = System.nanoTime(); // Начальное время
                try {
                    processRequest(startTime);
                } catch (Exception e) {
                    sendError("Error in processing request");
                }
            }
        }
    }

    public static void processRequest(long startTime){
        String string = System.getProperty("QUERY_STRING");
        if (string == null || string.isEmpty()){
            sendError("Not found");
            return;
        }
        Map<String, String> map = parseQuery(string);
        String rStr = map.get("r");
        String xStr = map.get("x");
        String yStr = map.get("y");
        if (rStr == null || xStr == null || yStr == null){
            sendError("Not found coordination");
            return;
        }
        try{
            double r = Double.parseDouble(rStr);
            double x = Double.parseDouble(xStr);
            double y = Double.parseDouble(yStr);
            if (r < 0){
                sendError("R are not valid");
                return;
            }
            boolean hit = checkHit(x,y,r);

            // Вычисляем время выполнения ДО создания результата
            long endTime = System.nanoTime();
            double workTime = (endTime - startTime) / 1_000_000.0; // в миллисекундах

            Map<String,String> result = new HashMap<>();
            result.put("r", rStr);
            result.put("x", xStr);
            result.put("y", yStr);
            result.put("hit", Boolean.toString(hit));
            result.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            result.put("workTime", String.format(Locale.US, "%.2f", workTime)); // Добавляем в current

            synchronized (queue){
                queue.add(result);
                if (queue.size() > MaxHistory){
                    queue.poll();
                }
            }

            sendJsonResponse(result);

        } catch (NumberFormatException e){
            sendError("Not found coordination");
        }
    }

    private static void sendJsonResponse(Map<String, String> currentResult) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"current\": {\n");
        json.append("    \"r\": \"").append(currentResult.get("r")).append("\",\n");
        json.append("    \"x\": \"").append(currentResult.get("x")).append("\",\n");
        json.append("    \"y\": \"").append(currentResult.get("y")).append("\",\n");
        json.append("    \"hit\": ").append(currentResult.get("hit")).append(",\n");
        json.append("    \"timestamp\": \"").append(currentResult.get("timestamp")).append("\",\n"); // запятая добавлена
        json.append("    \"workTime\": \"").append(currentResult.get("workTime")).append("\"\n"); // workTime в current
        json.append("  },\n");
        json.append("  \"history\": [\n");

        synchronized (queue) {
            Iterator<Map<String, String>> iterator = queue.iterator();
            while (iterator.hasNext()) {
                Map<String, String> result = iterator.next();
                json.append("    {\n");
                json.append("      \"r\": \"").append(result.get("r")).append("\",\n");
                json.append("      \"x\": \"").append(result.get("x")).append("\",\n");
                json.append("      \"y\": \"").append(result.get("y")).append("\",\n");
                json.append("      \"hit\": ").append(result.get("hit")).append(",\n");
                json.append("      \"timestamp\": \"").append(result.get("timestamp")).append("\",\n"); // запятая
                json.append("      \"workTime\": \"").append(result.get("workTime")).append("\"\n"); // workTime в истории
                json.append("    }");
                if (iterator.hasNext()) {
                    json.append(",");
                }
                json.append("\n");
            }
        }

        json.append("  ],\n");
        json.append("  \"error\": \"all ok\"\n"); // workTime удален из корневого уровня
        json.append("}");

        // Правильный вывод с заголовками
        byte[] contentBytes = json.toString().getBytes(StandardCharsets.UTF_8);
        System.out.println("Content-Type: application/json; charset=utf-8");
        System.out.println("Content-Length: " + contentBytes.length);
        System.out.println(); // Пустая строка между заголовками и телом
        System.out.println(json.toString());
    }

    public static void sendError(String message){
        String content = "{\"error\": \"" + message + "\"}";
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        System.out.println("Content-Type: application/json; charset=utf-8");
        System.out.println("Content-Length: " + contentBytes.length);
        System.out.println(); // Пустая строка между заголовками и телом
        System.out.println(content);
    }

    public static Map<String, String> parseQuery(String query){
        Map<String, String> map = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs){
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2){
                map.put(keyValue[0], keyValue[1]);
            }
        }
        return map;
    }

    public static boolean checkHit(double x, double y, double r){
        return isRect(x,y,r) || isTriangle(x, y, r) || isCircle(x, y, r);
    }

    private static boolean isTriangle(double x, double y, double r){
        return x >= 0 && x <= r && y >= 0 && y <= r/2 && y >= x/2;
    }

    private static boolean isRect(double x, double y, double r){
        return x <=0 && y >= 0 && x >= -r/2 && y <= r;
    }

    private static boolean isCircle(double x, double y, double r){
        return x >= 0 && x <= r/2 && y <= 0 && y >= -r/2 &&
                (x * x + y * y) <= r/2 * r/2;
    }
}
package org.example;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.logging.Logger;

public class ResultManager {
    private static final String URL = "jdbc:postgresql://pg:5432/studs";
    private static final String USER = "username";
    private static final String PASSWORD = "password";
    private static Connection conn;
    private static final Logger logger = Logger.getLogger(ResultManager.class.getName());

    public static void connect() throws SQLException, ClassNotFoundException {
        conn = DriverManager.getConnection(URL, USER, PASSWORD);
        logger.info("Connected to PostgreSQL database");
    }

    public static void insertResult(int x, int y, float r, float timeToWork, boolean result) throws SQLException {
        String insertResult = "INSERT INTO results (x, y, r, time_to_work, time_on_server, result) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(insertResult)){
            ps.setInt(1, x);
            ps.setInt(2, y);
            ps.setFloat(3, r);
            ps.setFloat(4, timeToWork);
            ps.setString(5, LocalDateTime.now().toString());
            ps.setBoolean(6, result);
            ps.executeUpdate();
        } catch (SQLException ex) {
            if (conn != null) {
                conn.rollback();
            }
            throw new SQLException(ex.getMessage());
        }
    }

    public static void insertDetailedFingerprint(String userAgent, String language, String platform,
                                                 Integer hardwareConcurrency, Integer deviceMemory,
                                                 Integer screenWidth, Integer screenHeight,
                                                 String timezone, String canvasFingerprint,
                                                 String fonts, String webRTCInfo,
                                                 String clientHints, String http2Info,
                                                 String ipAddress) throws SQLException {
        String insertUser = "INSERT INTO user_fingerprints (user_agent, language, platform, hardware_concurrency, " +
                "device_memory, screen_width, screen_height, timezone, canvas_fingerprint, fonts, " +
                "web_rtc_info, client_hints, http2_info, ip_address, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps = conn.prepareStatement(insertUser)){
            ps.setString(1, userAgent);
            ps.setString(2, language);
            ps.setString(3, platform);
            ps.setObject(4, hardwareConcurrency);
            ps.setObject(5, deviceMemory);
            ps.setObject(6, screenWidth);
            ps.setObject(7, screenHeight);
            ps.setString(8, timezone);
            ps.setString(9, canvasFingerprint);
            ps.setString(10, fonts);
            ps.setString(11, webRTCInfo);
            ps.setString(12, clientHints);
            ps.setString(13, http2Info);
            ps.setString(14, ipAddress);
            ps.setTimestamp(15, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException ex) {
            if (conn != null) {
                conn.rollback();
            }
            throw new SQLException(ex.getMessage());
        }
    }
}

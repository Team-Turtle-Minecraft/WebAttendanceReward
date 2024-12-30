package com.dohyeonlee.webattendance.webAttendanceReward;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class WebAttendanceReward extends JavaPlugin {
    private HttpServer server;
    private String apiKey;
    private List<String> allowedIps;

    @Override
    public void onEnable() {
        saveDefaultConfig(); // config.yml이 없으면 생성
        loadConfiguration();
        startWebServer();
        getLogger().info("WebAttendanceReward enabled!");
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.stop(0);
        }
        getLogger().info("WebAttendanceReward disabled!");
    }

    private void loadConfiguration() {
        apiKey = getConfig().getString("api-key", "your-secret-key");
        allowedIps = getConfig().getStringList("allowed-ips");

        // 설정이 비어있으면 기본값 설정
        if (allowedIps.isEmpty()) {
            allowedIps = Arrays.asList("127.0.0.1");
            getConfig().set("allowed-ips", allowedIps);
            saveConfig();
        }
    }

    private void startWebServer() {
        try {
            int port = getConfig().getInt("port", 8080);
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/command", new CommandHandler());
            server.setExecutor(null);
            server.start();
            getLogger().info("Web server started on port " + port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class CommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // IP 체크
                String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
                if (!allowedIps.contains(clientIp)) {
                    String response = "Forbidden: IP not allowed";
                    exchange.sendResponseHeaders(403, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }

                // API 키 체크
                Headers headers = exchange.getRequestHeaders();
                if (!headers.containsKey("X-API-Key") ||
                        !headers.getFirst("X-API-Key").equals(apiKey)) {
                    String response = "Unauthorized: Invalid API key";
                    exchange.sendResponseHeaders(401, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }

                // POST 메소드 체크
                if (!"POST".equals(exchange.getRequestMethod())) {
                    String response = "Method not allowed";
                    exchange.sendResponseHeaders(405, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }

                // 명령어 실행
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                String command = br.readLine();

                if (command == null || command.trim().isEmpty()) {
                    String response = "Empty command";
                    exchange.sendResponseHeaders(400, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }

                // 메인 스레드에서 명령어 실행
                Bukkit.getScheduler().runTask(WebAttendanceReward.this, () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                });

                String response = "Command executed: " + command;
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }

            } catch (Exception e) {
                e.printStackTrace();
                String response = "Internal server error";
                exchange.sendResponseHeaders(500, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }
}

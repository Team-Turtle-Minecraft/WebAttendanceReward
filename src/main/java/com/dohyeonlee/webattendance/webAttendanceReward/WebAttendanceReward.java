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
import java.util.concurrent.CompletableFuture;

public class WebAttendanceReward extends JavaPlugin {
    private HttpServer server;
    private String apiKey;
    private List<String> allowedIps;

    @Override
    public void onEnable() {
        saveDefaultConfig();
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
                    sendResponse(exchange, 403, "Forbidden: IP not allowed");
                    return;
                }

                // API 키 체크
                Headers headers = exchange.getRequestHeaders();
                if (!headers.containsKey("X-API-Key") ||
                        !headers.getFirst("X-API-Key").equals(apiKey)) {
                    sendResponse(exchange, 401, "Unauthorized: Invalid API key");
                    return;
                }

                // POST 메소드 체크
                if (!"POST".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method not allowed");
                    return;
                }

                // 명령어 읽기
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                String command = br.readLine();

                if (command == null || command.trim().isEmpty()) {
                    sendResponse(exchange, 400, "Empty command");
                    return;
                }

                // CompletableFuture를 사용하여 명령어 실행 결과를 기다림
                CompletableFuture<Boolean> future = new CompletableFuture<>();

                // 메인 스레드에서 명령어 실행
                Bukkit.getScheduler().runTask(WebAttendanceReward.this, () -> {
                    try {
                        boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                        future.complete(success);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });

                // 명령어 실행 결과 기다리기
                try {
                    boolean success = future.get();
                    if (success) {
                        sendResponse(exchange, 200, "Request has been successfully processed");
                    } else {
                        sendResponse(exchange, 500, "Command execution failed");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, 500, "Internal server error");
                }

            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal server error");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
}
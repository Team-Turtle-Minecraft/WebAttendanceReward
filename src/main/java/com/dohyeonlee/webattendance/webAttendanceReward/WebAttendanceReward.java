package com.dohyeonlee.webattendance.webAttendanceReward;

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

public class WebAttendanceReward extends JavaPlugin {
    private HttpServer server;

    @Override
    public void onEnable() {
        saveDefaultConfig(); // config.yml이 없으면 생성
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
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                String command = br.readLine();

                Bukkit.getScheduler().runTask(WebAttendanceReward.this, () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                });

                String response = "Command executed: " + command;
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } else {
                String response = "Only POST requests are allowed";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }
}

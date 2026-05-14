package com.threadtool;

import com.sun.net.httpserver.HttpServer;
import com.threadtool.api.ApiHandler;
import com.threadtool.service.ApplicationState;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public final class ThreadToolApplication {
    private static final int DEFAULT_PORT = 8080;

    private ThreadToolApplication() {
    }

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new ApiHandler(new ApplicationState()));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.printf("Thread draft tool is running on http://localhost:%d%n", port);
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return Integer.parseInt(args[0]);
        }
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            return Integer.parseInt(envPort);
        }
        return DEFAULT_PORT;
    }
}

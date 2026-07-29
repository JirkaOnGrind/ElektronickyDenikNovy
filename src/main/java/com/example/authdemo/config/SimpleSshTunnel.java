package com.example.authdemo.config;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.util.Properties;

public class SimpleSshTunnel {

    private static Session session;

    public static void start() {
        String datasourceUrl = firstNonBlank(
                System.getenv("DATASOURCE_URL"),
                System.getenv("DATABASE_URL")
        );
        String sshPassword = System.getenv("SSH_PASSWORD");

        if (sshPassword == null || sshPassword.isBlank()) {
            System.out.println(">>> SSH Tunel: SKIPPING (SSH_PASSWORD not set)");
            if (datasourceUrl != null && usesLocalDatabase(datasourceUrl)) {
                System.out.println(">>> SSH Tunel: WARNING - datasource points to localhost, but SSH tunnel is disabled.");
                System.out.println(">>> SSH Tunel: On Render set SSH_PASSWORD, or point DATASOURCE_URL/DATABASE_URL to a reachable DB host.");
            }
            return;
        }

        System.out.println(">>> SSH Tunel: FORCING STARTUP...");

        try {
            String sshHost = envOrDefault("SSH_HOST", "www.provoznidenik.com");
            String sshUser = envOrDefault("SSH_USER", "provoznidenik_com");
            int sshPort = intEnvOrDefault("SSH_PORT", 22);
            String remoteDbHost = envOrDefault("SSH_REMOTE_DB_HOST", "127.0.0.1");
            int remoteDbPort = intEnvOrDefault("SSH_REMOTE_DB_PORT", 3306);
            int localPort = intEnvOrDefault("SSH_LOCAL_PORT", 3306);

            JSch jsch = new JSch();
            session = jsch.getSession(sshUser, sshHost, sshPort);
            session.setPassword(sshPassword);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setServerAliveInterval(30_000);
            session.setServerAliveCountMax(3);

            System.out.println(">>> SSH Tunel: Connecting to " + sshHost + "...");
            session.connect(15_000);

            int assignedPort = session.setPortForwardingL(localPort, remoteDbHost, remoteDbPort);

            System.out.println(">>> SSH Tunel: SUCCESS!");
            System.out.println(">>> Forwarding: localhost:" + assignedPort + " -> " + remoteDbHost + ":" + remoteDbPort);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }, "ssh-tunnel-shutdown"));

        } catch (Exception e) {
            System.err.println("!!! SSH TUNEL FAILED !!!");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int intEnvOrDefault(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static boolean usesLocalDatabase(String datasourceUrl) {
        String normalized = datasourceUrl.toLowerCase();
        return normalized.contains("127.0.0.1") || normalized.contains("localhost");
    }
}

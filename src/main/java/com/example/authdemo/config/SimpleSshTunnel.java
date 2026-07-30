package com.example.authdemo.config;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SimpleSshTunnel {
    private static final Logger log = LoggerFactory.getLogger(SimpleSshTunnel.class);
    private static final Object SESSION_LOCK = new Object();

    private static volatile Session session;
    private static volatile boolean stopping;
    private static volatile Thread monitorThread;

    private SimpleSshTunnel() {
    }

    public static void start() {
        if (!booleanEnvOrDefault("SSH_ENABLED", true)) {
            log.info("SSH tunnel is disabled by SSH_ENABLED.");
            return;
        }

        String datasourceUrl = firstNonBlank(
                System.getenv("DATASOURCE_URL"),
                System.getenv("DATABASE_URL")
        );
        String sshPassword = blankToNull(System.getenv("SSH_PASSWORD"));
        String privateKeyFile = firstNonBlank(
                System.getenv("SSH_PRIVATE_KEY_FILE"),
                System.getenv("SSH_PRIVATE_KEY_PATH")
        );

        if (sshPassword == null && privateKeyFile == null) {
            log.info("SSH tunnel is disabled because no SSH credential is set.");
            if (datasourceUrl != null && usesLocalDatabase(datasourceUrl)) {
                log.warn("Datasource points to localhost, but SSH tunnel is disabled. "
                        + "Set SSH_PASSWORD or SSH_PRIVATE_KEY_FILE, or use a directly reachable database URL.");
            }
            return;
        }

        String strictHostKeyChecking =
                envOrDefault("SSH_STRICT_HOST_KEY_CHECKING", "yes").toLowerCase();
        if (!"yes".equals(strictHostKeyChecking) && !"no".equals(strictHostKeyChecking)) {
            throw new IllegalArgumentException(
                    "SSH_STRICT_HOST_KEY_CHECKING must be either yes or no");
        }

        TunnelConfig config = new TunnelConfig(
                requiredEnv("SSH_HOST"),
                requiredEnv("SSH_USER"),
                intEnvOrDefault("SSH_PORT", 22, 1, 65_535),
                sshPassword,
                privateKeyFile,
                blankToNull(System.getenv("SSH_PRIVATE_KEY_PASSPHRASE")),
                envOrDefault("SSH_REMOTE_DB_HOST", "127.0.0.1"),
                intEnvOrDefault("SSH_REMOTE_DB_PORT", 3306, 1, 65_535),
                intEnvOrDefault("SSH_LOCAL_PORT", 3306, 1, 65_535),
                intEnvOrDefault("SSH_CONNECT_TIMEOUT_MS", 10_000, 1_000, 60_000),
                intEnvOrDefault("SSH_CONNECT_ATTEMPTS", 6, 1, 10),
                intEnvOrDefault("SSH_RECONNECT_INTERVAL_MS", 15_000, 5_000, 300_000),
                firstNonBlank(
                        System.getenv("SSH_KNOWN_HOSTS_FILE"),
                        System.getenv("SSH_KNOWN_HOSTS")
                ),
                strictHostKeyChecking
        );

        if ("no".equalsIgnoreCase(config.strictHostKeyChecking())) {
            log.warn("SSH host key verification is disabled. Configure SSH_KNOWN_HOSTS_FILE and "
                    + "SSH_STRICT_HOST_KEY_CHECKING=yes to prevent man-in-the-middle attacks.");
        } else if (config.knownHostsFile() == null) {
            throw new IllegalStateException(
                    "SSH host key verification is enabled, but SSH_KNOWN_HOSTS_FILE is not set");
        }

        connectWithRetry(config, true);
        startMonitor(config);
        Runtime.getRuntime().addShutdownHook(new Thread(SimpleSshTunnel::shutdown, "ssh-tunnel-shutdown"));
    }

    private static boolean connectWithRetry(TunnelConfig config, boolean failWhenExhausted) {
        Exception lastFailure = null;

        for (int attempt = 1; attempt <= config.connectAttempts() && !stopping; attempt++) {
            Session candidate = null;
            try {
                log.info("Connecting SSH tunnel to {} (attempt {}/{})...",
                        config.sshHost(), attempt, config.connectAttempts());

                JSch jsch = new JSch();
                if (config.knownHostsFile() != null) {
                    jsch.setKnownHosts(config.knownHostsFile());
                }
                if (config.privateKeyFile() != null) {
                    if (config.privateKeyPassphrase() == null) {
                        jsch.addIdentity(config.privateKeyFile());
                    } else {
                        jsch.addIdentity(
                                config.privateKeyFile(),
                                config.privateKeyPassphrase()
                        );
                    }
                }

                candidate = jsch.getSession(config.sshUser(), config.sshHost(), config.sshPort());
                if (config.sshPassword() != null) {
                    candidate.setPassword(config.sshPassword().getBytes(StandardCharsets.UTF_8));
                }

                Properties sshOptions = new Properties();
                sshOptions.put("StrictHostKeyChecking", config.strictHostKeyChecking());
                candidate.setConfig(sshOptions);
                candidate.setServerAliveInterval(30_000);
                candidate.setServerAliveCountMax(3);
                candidate.connect(config.connectTimeoutMs());
                int assignedPort = candidate.setPortForwardingL(
                        config.localPort(), config.remoteDbHost(), config.remoteDbPort());

                synchronized (SESSION_LOCK) {
                    disconnectCurrentSession();
                    session = candidate;
                }
                log.info("SSH tunnel connected: localhost:{} -> {}:{}",
                        assignedPort, config.remoteDbHost(), config.remoteDbPort());
                return true;
            } catch (Exception ex) {
                lastFailure = ex;
                if (candidate != null && candidate.isConnected()) {
                    candidate.disconnect();
                }
                log.warn("SSH tunnel connection attempt {}/{} failed: {}",
                        attempt, config.connectAttempts(), safeMessage(ex));
                if (attempt < config.connectAttempts() && !stopping) {
                    sleepWithoutThrowing(Math.min(1_000L << (attempt - 1), 5_000L));
                }
            }
        }

        if (failWhenExhausted) {
            throw new IllegalStateException(
                    "SSH tunnel could not be established after " + config.connectAttempts() + " attempts",
                    lastFailure);
        }
        return false;
    }

    private static void startMonitor(TunnelConfig config) {
        synchronized (SESSION_LOCK) {
            if (monitorThread != null) {
                return;
            }
            monitorThread = new Thread(() -> {
                while (!stopping) {
                    sleepWithoutThrowing(config.reconnectIntervalMs());
                    if (stopping) {
                        break;
                    }

                    Session current = session;
                    if (current == null || !current.isConnected()) {
                        log.warn("SSH tunnel connection was lost; reconnecting.");
                        connectWithRetry(config, false);
                    }
                }
            }, "ssh-tunnel-monitor");
            monitorThread.setDaemon(true);
            monitorThread.start();
        }
    }

    private static void shutdown() {
        stopping = true;
        Thread currentMonitor = monitorThread;
        if (currentMonitor != null) {
            currentMonitor.interrupt();
        }
        synchronized (SESSION_LOCK) {
            disconnectCurrentSession();
        }
    }

    private static void disconnectCurrentSession() {
        Session current = session;
        session = null;
        if (current != null && current.isConnected()) {
            current.disconnect();
        }
    }

    private static void sleepWithoutThrowing(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message.replaceAll("[\\r\\n]", " ");
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = blankToNull(System.getenv(key));
        return value == null ? defaultValue : value;
    }

    private static String requiredEnv(String key) {
        String value = blankToNull(System.getenv(key));
        if (value == null) {
            throw new IllegalStateException(key + " must be set when the SSH tunnel is enabled");
        }
        return value;
    }

    private static int intEnvOrDefault(String key, int defaultValue, int min, int max) {
        String value = blankToNull(System.getenv(key));
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(
                        key + " must be between " + min + " and " + max);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be a valid integer", ex);
        }
    }

    private static boolean booleanEnvOrDefault(String key, boolean defaultValue) {
        String value = blankToNull(System.getenv(key));
        if (value == null) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException(key + " must be either true or false");
    }

    private static String firstNonBlank(String first, String second) {
        String firstValue = blankToNull(first);
        return firstValue != null ? firstValue : blankToNull(second);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean usesLocalDatabase(String datasourceUrl) {
        String normalized = datasourceUrl.toLowerCase();
        return normalized.contains("127.0.0.1") || normalized.contains("localhost");
    }

    private record TunnelConfig(
            String sshHost,
            String sshUser,
            int sshPort,
            String sshPassword,
            String privateKeyFile,
            String privateKeyPassphrase,
            String remoteDbHost,
            int remoteDbPort,
            int localPort,
            int connectTimeoutMs,
            int connectAttempts,
            int reconnectIntervalMs,
            String knownHostsFile,
            String strictHostKeyChecking
    ) {
    }
}

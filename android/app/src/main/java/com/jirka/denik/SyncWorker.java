package com.jirka.denik;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.CookieManager;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SyncWorker extends Worker {
    // ZMĚŇ SI NA SVOJI URL (pokud testuješ lokálně, dej IP počítače, na Renderu dej https://...)
    private static final String SERVER_URL = "https://elektronicky-denik.onrender.com";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("DenikOfflineData", Context.MODE_PRIVATE);
        Gson gson = new Gson();

        String jsonQueue = prefs.getString("offline_queue", "[]");
        List<Map<String, Object>> queue = gson.fromJson(jsonQueue, new TypeToken<ArrayList<Map<String, Object>>>(){}.getType());

        if (queue == null || queue.isEmpty()) return Result.success();

        String cookies = CookieManager.getInstance().getCookie(SERVER_URL);
        if (cookies == null || cookies.isBlank()) {
            return Result.retry();
        }
        String csrfToken = fetchCsrfToken(cookies, gson);
        if (csrfToken == null) {
            return Result.retry();
        }

        List<Map<String, Object>> failedItems = new ArrayList<>();

        for (Map<String, Object> item : queue) {
            try {
                String type = (String) item.get("type");
                Object payload = item.get("payload");
                String endpoint = "";

                if ("DAILY_CHECK".equals(type)) endpoint = "/api/sync/daily-check";
                else if ("MAINTENANCE".equals(type)) endpoint = "/api/sync/maintenance";
                else if ("REVISION".equals(type)) endpoint = "/api/sync/revision";

                if (!endpoint.isEmpty()) {
                    if (!sendPost(SERVER_URL + endpoint, gson.toJson(payload), cookies, csrfToken)) {
                        failedItems.add(item);
                    }
                }
            } catch (Exception e) {
                failedItems.add(item);
            }
        }

        prefs.edit().putString("offline_queue", gson.toJson(failedItems)).apply();

        return failedItems.isEmpty() ? Result.success() : Result.retry();
    }

    private String fetchCsrfToken(String cookies, Gson gson) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(SERVER_URL + "/api/csrf").openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Cookie", cookies);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(20_000);
            conn.setInstanceFollowRedirects(false);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (java.io.InputStreamReader reader = new java.io.InputStreamReader(
                    conn.getInputStream(), StandardCharsets.UTF_8)) {
                Map<String, String> response = gson.fromJson(
                        reader, new TypeToken<Map<String, String>>(){}.getType());
                return response == null ? null : response.get("token");
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private boolean sendPost(String urlString, String json, String cookies, String csrfToken) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json, text/plain");
            conn.setRequestProperty("Cookie", cookies);
            conn.setRequestProperty("X-CSRF-TOKEN", csrfToken);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(20_000);
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int responseCode = conn.getResponseCode();
            return responseCode >= 200 && responseCode < 300;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}

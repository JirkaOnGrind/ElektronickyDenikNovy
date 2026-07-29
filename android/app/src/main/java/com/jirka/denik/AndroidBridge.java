package com.jirka.denik;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingWorkPolicy;

public class AndroidBridge {
    private Context context;
    private SharedPreferences prefs;

    public AndroidBridge(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("DenikOfflineData", Context.MODE_PRIVATE);
    }

    @JavascriptInterface
    public void saveData(String key, String value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.apply();

        // Pokud se ukládá fronta, naplánujeme odeslání
        if ("offline_queue".equals(key)) {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                    .setConstraints(constraints)
                    .build();

            WorkManager.getInstance(context).enqueueUniqueWork(
                    "SendOfflineData",
                    ExistingWorkPolicy.KEEP,
                    syncRequest
            );
        }
    }

    @JavascriptInterface
    public String getData(String key) {
        return prefs.getString(key, "[]");
    }
}
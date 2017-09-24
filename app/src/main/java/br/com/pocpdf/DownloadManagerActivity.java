package br.com.pocpdf;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import java.io.File;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
import static br.com.pocpdf.Constants.PDF_BASE;
import static br.com.pocpdf.Constants.PDF_FILE;

public class DownloadManagerActivity extends AppCompatActivity {

    private long enqueue;
    private DownloadManagerActivity.DownloadBroadcast broadcast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_manager);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    protected void onResume() {
        super.onResume();
        broadcast = new DownloadBroadcast();

        registerReceiver(broadcast, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        downloadManagerAction();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(broadcast);
    }

 class DownloadBroadcast extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                checkDownload(intent);
            }
        }
    }

    private void checkDownload(Intent intent) {
        intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(enqueue);
        DownloadManager dm = getDownloadManager();

        Cursor c = dm.query(query);

        if (c.moveToFirst()) {
            int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                setResult(RESULT_OK, intent);
                finish();
            }
        }
    }

    private void downloadManagerAction() {

        DownloadManager dm = getDownloadManager();
        DownloadManager.Request request =
                new DownloadManager.Request(Uri.parse(PDF_BASE.concat(PDF_FILE)));

        request.setTitle(PDF_FILE);

        request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, PDF_FILE);

        enqueue = dm.enqueue(request);
    }

    private DownloadManager getDownloadManager() {
        return (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
    }
}

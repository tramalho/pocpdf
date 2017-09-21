package br.com.pocpdf;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.BufferedSink;
import okio.Okio;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getClass().getSimpleName();
    private String pdf_base = "http://www.adobe.com/devnet/acrobat/pdfs/";
    private String pdf_file = "pdf_open_parameters.pdf";

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private WebView webview;
    private ProgressBar progressBar;
    private long enqueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();

        verifyStoragePermissions(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(new DownloadBroadcast(), new IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void initView() {
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        webview = (WebView) findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                webview.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                webview.setVisibility(View.VISIBLE);
            }
        });
    }

    public void webviewAction(View view) {
        webview.loadUrl("http://drive.google.com/viewerng/viewer?embedded=true&url=" +
                pdf_base.concat(pdf_file));
    }

    private <T> T createService(Class<T> serviceClass, String baseUrl) {

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // set your desired log level
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        httpClient.addInterceptor(logging);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(httpClient.build())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create()).build();
        return retrofit.create(serviceClass);
    }

    private void verifyStoragePermissions(Activity activity) {
        // Check if we have read or write permission
        int writePermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int readPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE);

        if (writePermission != PackageManager.PERMISSION_GRANTED || readPermission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    public void shareAction(View view) {


        progressBar.setVisibility(View.VISIBLE);
        RetrofitInterface downloadService = createService(RetrofitInterface.class, pdf_base);
        downloadService.downloadFileByUrlRx(pdf_file)
                .flatMap(processResponse())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(handleResult());
    }

    private Func1<Response<ResponseBody>, Observable<File>> processResponse() {
        return new Func1<Response<ResponseBody>, Observable<File>>() {
            @Override
            public Observable<File> call(Response<ResponseBody> responseBodyResponse) {
                return saveToDiskRx(responseBodyResponse);
            }
        };
    }

    private Observable<File> saveToDiskRx(final Response<ResponseBody> responseBodyResponse) {
        return Observable.create(new Observable.OnSubscribe<File>() {
            @Override
            public void call(Subscriber<? super File> subscriber) {
                BufferedSink bufferedSink = null;
                try {
                    String fileName = pdf_file;

                    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);

                    bufferedSink = Okio.buffer(Okio.sink(file));
                    // you can access body of response
                    ResponseBody body = responseBodyResponse.body();
                    bufferedSink.writeAll(body.source());
                    bufferedSink.flush();
                    bufferedSink.close();

                    subscriber.onNext(file);
                    subscriber.onCompleted();
                } catch (IOException e) {
                    subscriber.onError(e);
                    Log.e(TAG, "bufferedSink.close() ", e);
                } finally {
                    if (bufferedSink != null) {
                        try {
                            bufferedSink.close();
                        } catch (IOException e) {
                            Log.d(TAG, "bufferedSink.close() ", e);
                        }
                    }
                }
            }
        });
    }

    private Observer<File> handleResult() {
        return new Observer<File>() {
            @Override
            public void onCompleted() {
                progressBar.setVisibility(View.GONE);
                Log.d(TAG, "onCompleted");
            }

            @Override
            public void onError(Throwable e) {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Error ", e);
            }

            @Override
            public void onNext(File file) {
                Log.d(TAG, "File downloaded to " + file.getAbsolutePath());
                openPdf(file);
            }
        };
    }

    private void openPdf(File file) {

        Uri uri = FileProvider.getUriForFile(MainActivity.this,
                BuildConfig.APPLICATION_ID + ".provider", file);

        Intent target = new Intent(Intent.ACTION_VIEW);
        target.setDataAndType(uri, "application/pdf");
        target.setFlags(FLAG_GRANT_READ_URI_PERMISSION | FLAG_GRANT_WRITE_URI_PERMISSION);

        Intent intent = Intent.createChooser(target, "Open File");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Nenhum app disponivel", Toast.LENGTH_SHORT).show();
        }
    }

    public void downloadManagerAction(View view) {

        DownloadManager dm = getDownloadManager();
        DownloadManager.Request request =
                new DownloadManager.Request(Uri.parse(pdf_base.concat(pdf_file)));

        request.setTitle(pdf_file);
        //Set the local destination for the downloaded file to a path within the application's external files directory
        request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, pdf_file);

        enqueue = dm.enqueue(request);
    }

    private DownloadManager getDownloadManager() {
        return (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
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
                Toast.makeText(this, "Download Finalizado", Toast.LENGTH_SHORT).show();
            }
        }
    }
}


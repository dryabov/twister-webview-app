package com.mfreitas.twister;

import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Application;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.view.View;
import android.view.KeyEvent;
import android.webkit.HttpAuthHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.content.Context;
import android.util.Log;


public class twisterActivity extends Activity {
    private static final String TAG = "twisterActivity";
    WebView mainWebView;

    private ValueCallback<Uri> mUploadMessage;  
    private final static int FILECHOOSER_RESULTCODE = 1;

    private static final int SIGSTOP = 19;
    private static final int SIGCONT = 18;
    private int twister_pid;

    @Override 
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (null == mUploadMessage) return;
            Uri result = intent == null || resultCode != RESULT_OK ? null
                    : intent.getData();
            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String htmlPath = this.getApplicationContext().getDir("html", Context.MODE_PRIVATE).getPath();
        InputStream is = this.getApplicationContext().getResources().openRawResource(R.raw.html);
        try {
            streamToDir(is, htmlPath);
        } catch (Exception e) {
            Log.w(TAG, "unzip error", e);
        }

        Process proc = null;
        String libPath = this.getApplicationInfo().nativeLibraryDir;
        String dataPath = getExternalFilesDir(null).getPath();
        try {
            String[] cmdline = { libPath + "/libtwisterd.so",
                                 "-daemon", "-genproclimit=1",
                                 "-rpcuser=user", "-rpcpassword=pwd",
                                 "-rpcallowip=127.0.0.1",
                                 "-dhtproxy", // to reduce battery&cpu usage
                                 "-datadir=" + dataPath,
                                 "-htmldir=" + htmlPath };
            Log.i(TAG, cmdline[0]);
            proc = Runtime.getRuntime().exec(cmdline);
            /*
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line = null;
            while ((line = reader.readLine())!=null) {
                Log.i(TAG, line);
            }
            */
            proc.waitFor();

            // @todo not sure it works in android:
            Process p = Runtime.getRuntime().exec("ps -C libtwisterd.so -o pid --no-headers");
            p.waitFor();
            InputStreamReader isr = new InputStreamReader(p.getInputStream());
            Scanner s = new java.util.Scanner(isr).useDelimiter("\\D");
            twister_pid = s.hasNext() ? Integer.parseInt(s.next()) : 0;

        } catch (Exception e) {
            Log.w(TAG, "Unable to exec proc for: " + libPath + "libtwisterd.so", e);
        }


        setContentView(R.layout.main);

        mainWebView = (WebView) findViewById(R.id.mainWebView);

        WebSettings settings = mainWebView.getSettings();
        settings.setJavaScriptEnabled(true);

        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        String databasePath = this.getApplicationContext().getDir("database", Context.MODE_PRIVATE).getPath();
        settings.setDatabasePath(databasePath);
        //settings.setDatabasePath("/data/data/"+this.getPackageName()+"/databases/");

        mainWebView.setWebViewClient(new MyCustomWebViewClient());
        mainWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        mainWebView.setWebChromeClient(new WebChromeClient()
        {
            //The undocumented magic method override
            //Eclipse will swear at you if you try to put @Override here
            // For Android 3.0+
            public void openFileChooser(ValueCallback<Uri> uploadMsg) {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                twisterActivity.this.startActivityForResult(Intent.createChooser(i,"File Chooser"), FILECHOOSER_RESULTCODE);
            }

            // For Android 3.0+
            public void openFileChooser( ValueCallback uploadMsg, String acceptType ) {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                twisterActivity.this.startActivityForResult(
                Intent.createChooser(i, "File Browser"),FILECHOOSER_RESULTCODE);
            }

            //For Android 4.1
            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                twisterActivity.this.startActivityForResult( Intent.createChooser( i, "File Chooser" ),
                        twisterActivity.FILECHOOSER_RESULTCODE );
            }
        });

        if (savedInstanceState == null) {
            Toast.makeText(getApplicationContext(), 
                           "twister daemon initializing\nlink may take a while to work",
                           Toast.LENGTH_LONG).show();
            mainWebView.loadUrl("http://127.0.0.1:28332/index.html");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        android.os.Process.sendSignal(twister_pid, SIGSTOP);
    }

    @Override
    public void onResume() {
        super.onResume();
        android.os.Process.sendSignal(twister_pid, SIGCONT);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (mainWebView.canGoBack() == true) {
                    mainWebView.goBack();
                } else {
                    //finish();
                    return super.onKeyDown(keyCode, event);
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
 
        // Save the state of the WebView
        mainWebView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
 
        // Restore the state of the WebView
        mainWebView.restoreState(savedInstanceState);
    }

    private void streamToDir( InputStream is, String outPath) throws IOException {
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is));
        try {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (!ze.isDirectory()) {
                    String filename = ze.getName();
                    File outFile = new File(outPath + "/" + filename);
                    File parent = outFile.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw new IllegalStateException("Couldn't create dir: " + parent);
                    }

                    OutputStream stmOut = new FileOutputStream(outFile);

                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = zis.read(buffer)) != -1) {
                        stmOut.write(buffer, 0, count);
                    }
                    stmOut.close();
                }
            }
        } finally {
            zis.close();
        }
    }

    private class MyCustomWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            // TODO Auto-generated method stub
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            // TODO Auto-generated method stub
            super.onPageFinished(view, url);
        }

        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
            handler.proceed("user", "pwd");
        }
    }
}

package com.merayetak.quiz;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int PICK_PHOTO = 1;
    private static final int STORAGE_PERM = 2;

    private WebView web;
    private ValueCallback<Uri[]> pendingChooser;
    private volatile Uri lastSaved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingChooser != null) pendingChooser.onReceiveValue(null);
                pendingChooser = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "اختار صورتك"), PICK_PHOTO);
                } catch (Exception e) {
                    pendingChooser = null;
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PHOTO && pendingChooser != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            pendingChooser.onReceiveValue(result);
            pendingChooser = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERM && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "تمام، اضغط حمّل التقرير تاني", Toast.LENGTH_LONG).show();
        }
    }

    private Uri savePng(String base64, String fileName) throws Exception {
        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        ContentResolver resolver = getContentResolver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Merayetak");
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return null;
            try (OutputStream os = resolver.openOutputStream(uri)) {
                if (os == null) return null;
                os.write(bytes);
            }
            return uri;
        }
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread(() -> requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERM));
            return null;
        }
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        String url = MediaStore.Images.Media.insertImage(resolver, bmp, fileName, "مرايتك");
        return url == null ? null : Uri.parse(url);
    }

    public class Bridge {
        @JavascriptInterface
        public boolean saveImage(String base64, String fileName) {
            try {
                Uri uri = savePng(base64, fileName);
                if (uri == null) return false;
                lastSaved = uri;
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void shareImage(String base64) {
            try {
                Uri uri = lastSaved;
                if (uri == null) uri = savePng(base64, "merayetak-" + System.currentTimeMillis() + ".png");
                if (uri == null) return;
                final Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("image/png");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(send, "شارك التقرير")));
            } catch (Exception ignored) {
            }
        }
    }
}

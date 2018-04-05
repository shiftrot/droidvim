package jackpal.androidterm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class WebViewActivity extends Activity {
    private static int mFontSize = 140;
    private static int mInitialInterval = 0;
    private static int mNormalInterval = 100;
    private boolean mBack = false;
    private WebView mWebView;
    @SuppressLint({"SetJavaScriptEnabled", "NewApi"})
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.webview_activity);
        mWebView = findViewById(R.id.WebView);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setUseWideViewPort(true);
        mWebView.getSettings().setLoadWithOverviewMode(true);
        mWebView.getSettings().setBuiltInZoomControls(true);
        mWebView.getSettings().setBlockNetworkImage(false);
        mWebView.getSettings().setBlockNetworkLoads(false);
        mWebView.getSettings().setAllowFileAccess(true);
        mWebView.getSettings().setTextZoom(mFontSize);
//        mWebView.getSettings().setCacheMode(LOAD_NO_CACHE);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (mBack && url.startsWith("file:")) {
                    mWebView.reload();
                    mBack = false;
                }
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http:") || url.startsWith("https:") || url.startsWith("file:")) {
                    return false;
                }
                Uri uri = Uri.parse(url);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                return true;
            }
        });
        setButtonListener();

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            try {
                String size = bundle.getString("size");
                if (size != null) mFontSize = Integer.parseInt(size);
                mWebView.getSettings().setTextZoom(mFontSize);
            } catch (Exception e) {
                Log.d("WebViewAcitivity", e.toString());
            }
            String url = bundle.getString("url");
            if ((url == null) || (!load(mWebView, url, mWebView.getUrl()))) {
                Log.d("WebViewAcitivity", "Load error : "+url);
            }
        }
    }

    private void setButtonListener() {
        findViewById(R.id.webview_back).setOnClickListener(mButtonListener);
        findViewById(R.id.webview_forward).setOnClickListener(mButtonListener);
        findViewById(R.id.webview_reload).setOnClickListener(mButtonListener);
        findViewById(R.id.webview_abort).setOnClickListener(mButtonListener);
        findViewById(R.id.webview_quit).setOnClickListener(mButtonListener);
        if (mInitialInterval == 0) {
            findViewById(R.id.webview_plus).setOnClickListener(mButtonListener);
            findViewById(R.id.webview_minus).setOnClickListener(mButtonListener);
        } else {
            findViewById(R.id.webview_plus).setOnTouchListener(new RepeatListener(mInitialInterval, mNormalInterval, mButtonListener));
            findViewById(R.id.webview_minus).setOnTouchListener(new RepeatListener(mInitialInterval, mNormalInterval, mButtonListener));
        }
    }

    View.OnClickListener mButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.webview_back:
                    if (mWebView.canGoBack()){
                        mBack = true;
                        mWebView.goBack();
                    } else {
                        mWebView.stopLoading();
                        finish();
                    }
                    break;
                case R.id.webview_forward:
                    if (mWebView.canGoForward()){
                        mWebView.goForward();
                    }
                    break;
                case R.id.webview_reload:
                    mWebView.reload();
                    break;
                case R.id.webview_abort:
                    mWebView.stopLoading();
                    break;
                case R.id.webview_quit:
                    mWebView.stopLoading();
                    finish();
                    break;
                case R.id.webview_plus:
                    mWebView.getSettings().setTextZoom(mWebView.getSettings().getTextZoom() + 10);
                    mFontSize = mWebView.getSettings().getTextZoom();
                    break;
                case R.id.webview_minus:
                    mWebView.getSettings().setTextZoom(mWebView.getSettings().getTextZoom() - 10);
                    mFontSize = mWebView.getSettings().getTextZoom();
                    break;
                default:
                    break;
            }
        }
    };

    static public void setFontSize(int size) {
        mFontSize = size;
    }

    static public int getFontSize() {
        return mFontSize;
    }

    static public void setRepeatInterval(int initial, int normal) {
        mInitialInterval = initial;
        mNormalInterval = normal;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK){
            if (mWebView.canGoBack()){
                mBack = true;
                mWebView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean load(WebView webView, String url, String prev) {
        if (url.matches("https?://.*")) {
            webView.loadUrl(url);
            return true;
        }
        try {
            File html = new File (url);
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(html),"UTF-8"));
            StringBuilder buffer = new StringBuilder();
            String str;
            while ((str = reader.readLine()) != null) {
                buffer.append(str);
                buffer.append(System.getProperty("line.separator"));
            }
            String data = buffer.toString();
            webView.loadDataWithBaseURL("file://"+url, data, "text/html", "UTF-8", prev);
            return true;
        } catch (Exception e) {
            Log.d("WebViewAcitivity", e.getMessage());
        }
        return false;
    }
}

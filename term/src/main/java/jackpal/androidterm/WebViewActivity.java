package jackpal.androidterm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.preference.PreferenceManager;
import jackpal.androidterm.util.TermSettings;

import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_SHORT;

public class WebViewActivity extends Activity {
    /*
     *  Open Android Settings
     *  If there is a link starting with "file:///android_asset/ACTION_SETTINGS/", open the corresponding setting screen.
     *  E.g. <a href="file:///android_asset/ACTION_SETTINGS/ACTION_HARD_KEYBOARD_SETTINGS">Physical Keyboard settings&</a>
     */
    static final private Map<String, String> mSettingsAction = new HashMap<String, String>() {
        {
            put("ACTION_SETTINGS", Settings.ACTION_SETTINGS);
            put("ACTION_INPUT_METHOD_SETTINGS", Settings.ACTION_INPUT_METHOD_SETTINGS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                put("ACTION_HARD_KEYBOARD_SETTINGS", Settings.ACTION_HARD_KEYBOARD_SETTINGS);
            }
        }
    };
    private static int mFontSize = 140;
    private static int mInitialInterval = 0;
    private static int mNormalInterval = 100;
    private static int mZoom = 5;
    private boolean mBack = false;
    private WebView mWebView;
    private View.OnClickListener mButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.webview_back:
                    if (mWebView.canGoBack()) {
                        mBack = true;
                        mWebView.goBack();
                    } else {
                        mWebView.stopLoading();
                        finish();
                    }
                    break;
                case R.id.webview_forward:
                    if (mWebView.canGoForward()) {
                        mWebView.goForward();
                    }
                    break;
                case R.id.webview_reload:
                    mWebView.reload();
                    toast(R.string.reloading);
                    break;
                case R.id.webview_abort:
                    mWebView.stopLoading();
                    toast(R.string.stop_loading);
                    break;
                case R.id.webview_menu:
                    menu();
                    break;
                case R.id.webview_plus:
                    mWebView.getSettings().setTextZoom(mWebView.getSettings().getTextZoom() + mZoom);
                    mFontSize = mWebView.getSettings().getTextZoom();
                    break;
                case R.id.webview_minus:
                    mWebView.getSettings().setTextZoom(mWebView.getSettings().getTextZoom() - mZoom);
                    mFontSize = mWebView.getSettings().getTextZoom();
                    break;
                default:
                    break;
            }
        }
    };

    static public int getFontSize() {
        return mFontSize;
    }

    static public void setFontSize(int size) {
        mFontSize = size;
    }

    static public void setRepeatInterval(int initial, int normal) {
        mInitialInterval = initial;
        mNormalInterval = normal;
    }

    @SuppressLint({"SetJavaScriptEnabled", "NewApi"})
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupTheme();
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        int viewId = R.layout.webview_activity;
        if (bundle != null) {
            String url = bundle.getString("url");
            if (url != null && url.contains("/tmp/html/text.html")) {
                viewId = R.layout.webview_text_activity;
            }
        }
        setContentView(viewId);
        mWebView = findViewById(R.id.WebView);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setUseWideViewPort(true);
        mWebView.getSettings().setLoadWithOverviewMode(true);
        mWebView.getSettings().setBuiltInZoomControls(true);
        mWebView.getSettings().setBlockNetworkImage(false);
        mWebView.getSettings().setBlockNetworkLoads(false);
        mWebView.getSettings().setAllowFileAccess(true);
        mWebView.getSettings().setTextZoom(mFontSize);
        // mWebView.getSettings().setCacheMode(LOAD_NO_CACHE);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (mBack && url.startsWith("file:")) {
                    mBack = false;
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return overrideUrlLoading(webView, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView webView, String url) {
                return overrideUrlLoading(webView, url);
            }
        });
        setButtonListener();
        mWebView.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype, long contentLength) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });

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
                Log.d("WebViewAcitivity", "Load error : " + url);
            }
        }
    }

    private boolean overrideUrlLoading(WebView view, String url) {
        if (openSettings(url)) return true;
        if (openPlayStorePage(url)) return true;
        if (url.startsWith("http:") || url.startsWith("https:") || url.startsWith("file:")) {
            return false;
        }
        Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
        return true;
    }

    private void setButtonListener() {
        findViewById(R.id.webview_back).setOnClickListener(mButtonListener);
        findViewById(R.id.webview_forward).setOnClickListener(mButtonListener);
        findViewById(R.id.webview_reload).setOnClickListener(mButtonListener);
        findViewById(R.id.webview_abort).setOnClickListener(mButtonListener);
        findViewById(R.id.webview_menu).setOnClickListener(mButtonListener);
        if (mInitialInterval == 0) {
            findViewById(R.id.webview_plus).setOnClickListener(mButtonListener);
            findViewById(R.id.webview_minus).setOnClickListener(mButtonListener);
        } else {
            findViewById(R.id.webview_plus).setOnTouchListener(new RepeatListener(mInitialInterval, mNormalInterval, mButtonListener));
            findViewById(R.id.webview_minus).setOnTouchListener(new RepeatListener(mInitialInterval, mNormalInterval, mButtonListener));
        }
    }

    private void menu() {
        String[] items = {getString(R.string.scroll_to_top), getString(R.string.scroll_to_bottom), getString(R.string.open_in_browser), getString(R.string.quit)};
        final int quit = items.length - 1;
        new AlertDialog.Builder(mWebView.getContext())
                .setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (which == quit) {
                            mWebView.stopLoading();
                            finish();
                        } else if (which == 0) {
                            mWebView.pageUp(true);
                        } else if (which == 1) {
                            mWebView.pageDown(true);
                        } else if (which == 2) {
                            execURL(mWebView.getUrl());
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void execURL(String link) {
        if (link == null) return;
        Uri webLink = Uri.parse(link);
        if (webLink == null) return;
        String url = webLink.toString();
        if (!(url.startsWith("http:") || url.startsWith("https:") || url.startsWith("file:"))) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(R.string.webview_local_file_error);
            builder.setPositiveButton(android.R.string.ok, null);
            builder.create().show();
            return;
        }
        Intent openLink = new Intent(Intent.ACTION_VIEW, webLink);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> handlers = pm.queryIntentActivities(openLink, 0);
        if (handlers.size() > 0) {
            startActivity(openLink);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage("Browser not found.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.create().show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mWebView.canGoBack()) {
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
            File html = new File(url);
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(html), "UTF-8"));
            StringBuilder buffer = new StringBuilder();
            String str;
            while ((str = reader.readLine()) != null) {
                buffer.append(str);
                buffer.append(System.getProperty("line.separator"));
            }
            String data = buffer.toString();
            webView.loadDataWithBaseURL("file://" + url, data, "text/html", "UTF-8", prev);
            return true;
        } catch (Exception e) {
            Log.d("WebViewAcitivity", e.getMessage());
        }
        return false;
    }

    private boolean openPlayStorePage(String url) {
        if (!url.matches("https?://play.google.com/store/apps/details\\?id=.*")) {
            return false;
        }
        String pname = url.replaceFirst("https?://.*/details\\?id=", "");
        Uri uri = Uri.parse("market://details?id=" + pname);
        Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
        // To count with Play market backstack, After pressing back button,
        // to taken back to our application, we need to add following flags to intent.
        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            startActivity(goToMarket);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(url)));
        }
        return true;
    }

    private boolean openSettings(String url) {
        final String ANDROID_SETTINGS = "file:///android_asset/ACTION_SETTINGS";
        if (!url.startsWith(ANDROID_SETTINGS)) {
            return false;
        }
        url = url.replaceFirst(ANDROID_SETTINGS + "/?", "");
        String action = getSettingsAction(url);
        Intent intent = new Intent(action);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return startIntent(intent);
    }

    private String getSettingsAction(String action) {
        String settingsAction = Settings.ACTION_SETTINGS;
        if ((action.equals("ACTION_HARD_KEYBOARD_SETTINGS") && (Build.VERSION.SDK_INT < Build.VERSION_CODES.N))) {
            action = "ACTION_INPUT_METHOD_SETTINGS";
        }
        if (mSettingsAction.containsKey(action)) return mSettingsAction.get(action);
        return settingsAction;
    }

    private boolean startIntent(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private void toast(int resid) {
        Toast.makeText(mWebView.getContext(), resid, Toast.LENGTH_SHORT).show();
        // Snackbar.make(findViewById(R.id.webview_coordinator_layout_top), resid, LENGTH_SHORT).show();
    }

    private void setupTheme() {
        final SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        TermSettings mSettings = new TermSettings(getResources(), mPrefs);
        int theme = mSettings.getColorTheme();
        switch (theme) {
            case 0:
                setTheme(R.style.App_Theme_Dark);
                break;
            case 1:
                setTheme(R.style.App_Theme_Light);
                break;
            case 2:
                setTheme(R.style.App_Theme_Dark_api26);
                break;
            case 3:
                setTheme(R.style.App_Theme_Light_api26);
                break;
            default:
                break;
        }
    }

}

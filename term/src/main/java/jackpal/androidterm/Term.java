/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jackpal.androidterm;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Instrumentation;
import android.content.ContentUris;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.system.Os;
import android.text.TextUtils;
import jackpal.androidterm.compat.AndroidCompat;
import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.emulatorview.UpdateCallback;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompat;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompatFactory;
import jackpal.androidterm.emulatorview.compat.KeycodeConstants;
import jackpal.androidterm.util.SessionList;
import jackpal.androidterm.util.TermSettings;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static android.provider.DocumentsContract.*;

/**
 * A terminal emulator activity.
 */

public class Term extends AppCompatActivity implements UpdateCallback, SharedPreferences.OnSharedPreferenceChangeListener, OnClickListener {
    /**
     * The ViewFlipper which holds the collection of EmulatorView widgets.
     */
    private TermViewFlipper mViewFlipper;

    /**
     * The name of the ViewFlipper in the resources.
     */
    private static final int VIEW_FLIPPER = R.id.view_flipper;

    private SessionList mTermSessions;

    private TermSettings mSettings;

    private final static int PASTE_ID = 0;
    private final static int COPY_ALL_ID = 1;
    private final static int SELECT_TEXT_ID = 2;
    private final static int SEND_CONTROL_KEY_ID = 3;
    private final static int SEND_FN_KEY_ID = 4;
    private final static int SEND_FUNCTION_BAR_ID = 5;
    private final static int SEND_MENU_ID = 6;

    private final static int UNPRESSED = 0;
    private final static int PRESSED   = 1;
    private final static int RELEASED  = 2;
    private final static int USED      = 3;
    private final static int LOCKED    = 4;

    private boolean mAlreadyStarted = false;
    private boolean mStopServiceOnFinish = false;

    private final static boolean FLAVOR_VIM = TermVimInstaller.FLAVOR_VIM;
    private static boolean mVimFlavor = FLAVOR_VIM;

    private Intent TSIntent;

    public static final int REQUEST_CHOOSE_WINDOW = 1;
    public static final int REQUEST_FILE_PICKER = 2;
    public static final int REQUEST_FILE_DELETE = 3;
    public static final String EXTRA_WINDOW_ID = "jackpal.androidterm.window_id";
    private int onResumeSelectWindow = -1;
    private ComponentName mPrivateAlias;

    private boolean mBackKeyPressed;

    private static final String ACTION_PATH_BROADCAST = "jackpal.androidterm.broadcast.APPEND_TO_PATH";
    private static final String ACTION_PATH_PREPEND_BROADCAST = "jackpal.androidterm.broadcast.PREPEND_TO_PATH";
    private static final String PERMISSION_PATH_BROADCAST = "jackpal.androidterm.permission.APPEND_TO_PATH";
    private static final String PERMISSION_PATH_PREPEND_BROADCAST = "jackpal.androidterm.permission.PREPEND_TO_PATH";
    private int mPendingPathBroadcasts = 0;

    private static       int mExternalAppMode   = 1;
    private static       String mExternalApp    = "";
    private static final String APP_DROPBOX     = "com.dropbox.android";
    private static final String APP_GOOGLEDRIVE = "com.google.android.apps.docs";
    private static final String APP_ONEDRIVE    = "com.microsoft.skydrive";
    private static final Map<String, String> mAltBrowser = new LinkedHashMap<String, String>() {
        {put("org.mozilla.firefox_beta", "org.mozilla.firefox_beta.App");}
        {put("org.mozilla.firefox", "org.mozilla.firefox.App");}
        {put("com.amazon.cloud9", "com.amazon.cloud9.browsing.BrowserActivity");}
    };

    private TermService mTermService;
    private ServiceConnection mTSConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TermDebug.LOG_TAG, "Bound to TermService");
            TermService.TSBinder binder = (TermService.TSBinder) service;
            mTermService = binder.getService();
            if (mPendingPathBroadcasts <= 0) {
                populateViewFlipper();
            }
        }

        public void onServiceDisconnected(ComponentName arg0) {
            mTermService = null;
        }
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        mSettings.readPrefs(sharedPreferences);
        setDrawerButtons();
    }

    private boolean mHaveFullHwKeyboard = false;

    private class EmulatorViewGestureListener extends SimpleOnGestureListener {
        private EmulatorView view;

        public EmulatorViewGestureListener(EmulatorView view) {
            this.view = view;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // Let the EmulatorView handle taps if mouse tracking is active
            if (view.isMouseTrackingActive()) return false;

            doUIToggle((int) e.getX(), (int) e.getY(), view.getVisibleWidth(), view.getVisibleHeight());
            return true;
        }

        private float mDeltaColumnsReminder;
        private final int mDeltaColumnsEdge = 3;
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            EmulatorView view = getCurrentEmulatorView();
            if (view == null) return false;
            if (Math.abs(distanceX) < Math.abs(distanceY)) return false;
            if ((int) e1.getY() < view.getVisibleHeight() / mDeltaColumnsEdge) return false;

            distanceX += mDeltaColumnsReminder;
            int mCharacterWidth = view.getCharacterWidth();
            int deltaColumns = (int) (distanceX / mCharacterWidth);
            mDeltaColumnsReminder = distanceX - deltaColumns * mCharacterWidth;

            for (; deltaColumns > 0; deltaColumns--) {
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_LEFT);
            }
            for (; deltaColumns < 0; deltaColumns++) {
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_RIGHT);
            }
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            mDeltaColumnsReminder = 0.0f;
            onLastKey();
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float absVelocityX = Math.abs(velocityX);
            float absVelocityY = Math.abs(velocityY);

            mDeltaColumnsReminder = 0.0f;
            if (absVelocityX > Math.max(1000.0f, 2.0 * absVelocityY)) {
                if ((int) e1.getY() >= view.getVisibleHeight() / mDeltaColumnsEdge) return false;
                // Assume user wanted side to side movement
                if (velocityX > 0) {
                    // Left to right swipe -- previous window
                    mViewFlipper.showPrevious();
                } else {
                    // Right to left swipe -- next window
                    mViewFlipper.showNext();
                }
                return true;
            } else {
                if (Math.abs(velocityX) > Math.abs(velocityY)) return true;
                return false;
            }
        }

    }

    private class EmulatorViewDoubleTapListener implements GestureDetector.OnDoubleTapListener {
        private EmulatorView view;

        public EmulatorViewDoubleTapListener(EmulatorView view) {
            this.view = view;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            Log.w(TermDebug.LOG_TAG, "onSingleTapConfirmed");
            return false;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            Log.w(TermDebug.LOG_TAG, "onDoubleTapEvent");
            return doDoubleTapAction(e);
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            Log.w(TermDebug.LOG_TAG, "onDoubleTapEvent");
            return false;
        }

    }

    /**
     * Should we use keyboard shortcuts?
     */
    private boolean mUseKeyboardShortcuts;

    /**
     * Intercepts keys before the view/terminal gets it.
     */
    private View.OnKeyListener mKeyListener = new View.OnKeyListener() {
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            onLastKey();
            return backkeyInterceptor(keyCode, event) || keyboardShortcuts(keyCode, event);
        }

        /**
         * Keyboard shortcuts (tab management, paste)
         */
        private boolean keyboardShortcuts(int keyCode, KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            if (!mUseKeyboardShortcuts) {
                return false;
            }

            boolean isCtrlPressed = (event.getMetaState() & KeycodeConstants.META_CTRL_ON) != 0;
            boolean isShiftPressed = (event.getMetaState() & KeycodeConstants.META_SHIFT_ON) != 0;

            if (keyCode == KeycodeConstants.KEYCODE_TAB && isCtrlPressed) {
                if (isShiftPressed) {
                    mViewFlipper.showPrevious();
                } else {
                    mViewFlipper.showNext();
                }

                return true;
            } else if (keyCode == KeycodeConstants.KEYCODE_N && isCtrlPressed && isShiftPressed) {
                doCreateNewWindow();

                return true;
            } else if (keyCode == KeycodeConstants.KEYCODE_V && isCtrlPressed && isShiftPressed) {
                doPaste();

                return true;
            } else {
                return false;
            }
        }

        /**
         * Make sure the back button always leaves the application.
         */
        private boolean backkeyInterceptor(int keyCode, KeyEvent event) {
            return false;
        }
    };

    long mLastKeyPress = System.currentTimeMillis();
    private void onLastKey() {
        mLastKeyPress = System.currentTimeMillis();
        if (mKeepScreenEnableAuto) {
            boolean keepScreen = (getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
            if (!keepScreen) doToggleKeepScreen();
        }
    }

    private Handler mHandler = new Handler();
    private SyncFileObserver mSyncFileObserver = null;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Log.v(TermDebug.LOG_TAG, "onCreate");

        mPrivateAlias = new ComponentName(this, RemoteInterface.PRIVACT_ACTIVITY_ALIAS);

        if (icicle == null)
            onNewIntent(getIntent());

        final SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSettings = new TermSettings(getResources(), mPrefs);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        if (!mVimFlavor && mSettings.doPathExtensions()) {
            BroadcastReceiver pathReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String path = makePathFromBundle(getResultExtras(false));
                    if (intent.getAction().equals(ACTION_PATH_PREPEND_BROADCAST)) {
                        mSettings.setPrependPath(path);
                    } else {
                        mSettings.setAppendPath(path);
                    }
                    mPendingPathBroadcasts--;

                    if (mPendingPathBroadcasts <= 0 && mTermService != null) {
                        populateViewFlipper();
                    }
                }
            };

            Intent broadcast = new Intent(ACTION_PATH_BROADCAST);
            if (AndroidCompat.SDK >= 12) {
                broadcast.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            }
            mPendingPathBroadcasts++;
            sendOrderedBroadcast(broadcast, PERMISSION_PATH_BROADCAST, pathReceiver, null, RESULT_OK, null, null);

            if (!mVimFlavor && mSettings.allowPathPrepend()) {
                broadcast = new Intent(broadcast);
                broadcast.setAction(ACTION_PATH_PREPEND_BROADCAST);
                mPendingPathBroadcasts++;
                sendOrderedBroadcast(broadcast, PERMISSION_PATH_PREPEND_BROADCAST, pathReceiver, null, RESULT_OK, null, null);
            }
        }

        TSIntent = new Intent(this, TermService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED) {
                this.getApplicationContext().startForegroundService(TSIntent);
            } else {
                requestPermissions(new String[]{Manifest.permission.FOREGROUND_SERVICE}, REQUEST_FOREGROUND_SERVICE_PERMISSION);
            }
        } else {
            startService(TSIntent);
        }

        int theme = mSettings.getColorTheme();
        if (theme == 0) {
            setTheme(R.style.Theme_AppCompat_NoActionBar);
        } else {
            setTheme(R.style.Theme_AppCompat_Light_NoActionBar);
        }

        setContentView(R.layout.term_activity);
        mViewFlipper = (TermViewFlipper) findViewById(VIEW_FLIPPER);
        setFunctionKeyListener();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mHaveFullHwKeyboard = checkHaveFullHwKeyboard(getResources().getConfiguration());
        setSoftInputMode(mHaveFullHwKeyboard);

        if (mFunctionBar == -1) mFunctionBar = mSettings.showFunctionBar() ? 1 : 0;
        if (mFunctionBar == 1) setFunctionBar(mFunctionBar);
        if (mOnelineTextBox == -1) mOnelineTextBox = mSettings.showOnelineTextBox() ? 1 : 0;
        initOnelineTextBox(mOnelineTextBox);
        INTENT_CACHE_DIR = this.getApplicationContext().getCacheDir().toString()+"/intent";

        WebViewActivity.setFontSize(new PrefValue(this).getInt("mWebViewSize", 140));

        updatePrefs();
        setDrawerButtons();
        restoreSyncFileObserver();
        mAlreadyStarted = true;
    }

    public static File getScratchCacheDir(Activity activity) {
        int sdcard = TermService.getSDCard(activity.getApplicationContext());
        String cacheDir = TermService.getCacheDir(activity.getApplicationContext(), sdcard);
        return new File(cacheDir+"/scratch");
    }

    private static final String mSyncFileObserverFile = "mSyncFileObserver.dat";
    private void restoreSyncFileObserver() {
        if (!FLAVOR_VIM) return;
        File dir = getScratchCacheDir(this);
        mSyncFileObserver = new SyncFileObserver(dir.toString());
        mSyncFileObserver.deleteFromStorage(true);
        File sfofile = new File(dir.toString()+"/"+mSyncFileObserverFile);
        if (sfofile.exists()) {
            try {
                FileInputStream fis = new FileInputStream(sfofile.toString());
                int size = fis.available();
                byte[] buffer = new byte[size];
                fis.read(buffer);
                fis.close();

                String json = new String(buffer);
                JSONObject jsonObject = new JSONObject(json);
                JSONArray jsonArray = jsonObject.getJSONArray("mHashMap");
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonOneRecord = jsonArray.getJSONObject(i);
                    mSyncFileObserver.putHashMap((String) jsonOneRecord.get("path"), (String) jsonOneRecord.get("uri"));
                }
                fis.close();
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }
        mSyncFileObserver.setConTentResolver(this.getContentResolver());
        mSyncFileObserver.setActivity(this);
        mSyncFileObserver.startWatching();
    }

    private void saveSyncFileObserver() {
        if (!FLAVOR_VIM) return;
        if (mSyncFileObserver == null) return;
        mSyncFileObserver.stopWatching();
        try {
            String dir = mSyncFileObserver.getObserverDir();
            File sfofile = new File(dir +"/"+mSyncFileObserverFile);

            JSONObject jsonObject = new JSONObject();
            JSONArray jsonArary = new JSONArray();

            Map<String, String> hashMap = mSyncFileObserver.getHashMap();
            for(Map.Entry<String, String> entry : hashMap.entrySet()) {
                JSONObject jsonOneData;
                jsonOneData = new JSONObject();
                jsonOneData.put("path", entry.getKey());
                jsonOneData.put("uri", entry.getValue());
                jsonArary.put(jsonOneData);
            }
            jsonObject.put("mHashMap", jsonArary);

            FileWriter fileWriter = new FileWriter(sfofile);
            BufferedWriter bw = new BufferedWriter(fileWriter);
            PrintWriter pw = new PrintWriter(bw);
            pw.write(jsonObject.toString());
            pw.close();
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void setExtraButton() {
        Button button = (Button)findViewById(R.id.drawer_extra_button);
        int visibilty = View.GONE;
        button.setVisibility(visibilty);
    }

    private void setDebugButton() {
        if (!BuildConfig.DEBUG) return;
        Button button = (Button)findViewById(R.id.drawer_debug_button);
        button.setVisibility(View.VISIBLE);

        if (mSettings.getColorTheme() == 0) button.setBackgroundResource(R.drawable.extra_button_dark);
    }

    ArrayList<String> mFilePickerItems;
    private void setDrawerButtons() {
        if (FLAVOR_VIM) {
            mFilePickerItems = new ArrayList<>();
            mFilePickerItems.add(this.getString(R.string.create_file));
            mFilePickerItems.add(this.getString(R.string.delete_file));
            int visiblity = mSettings.getExternalAppButtonMode() > 0 ? View.VISIBLE : View.GONE;
            Button button = (Button) findViewById(R.id.drawer_app_button);
            button.setVisibility(visiblity);
            mExternalApp = mSettings.getExternalAppId();
            mExternalAppMode = mSettings.getExternalAppButtonMode();
            if (visiblity == View.VISIBLE) {
                try {
                    PackageManager pm = this.getApplicationContext().getPackageManager();
                    PackageInfo packageInfo = pm.getPackageInfo(mExternalApp, 0);
                    String label = packageInfo.applicationInfo.loadLabel(pm).toString();
                    button.setText(label);
                } catch (Exception e) {
                    button.setText(R.string.external_app_button);
                }
            }
            String launchApp = this.getString(R.string.launch_app);
            if (isAppInstalled(APP_DROPBOX)) {
                visiblity = mSettings.getDropboxFilePicker() > 0 ? View.VISIBLE : View.GONE;
                button = (Button)findViewById(R.id.drawer_dropbox_button);
                button.setVisibility(visiblity);
                mFilePickerItems.add(String.format(launchApp, this.getString(R.string.dropbox)));
            }
            if (isAppInstalled(APP_GOOGLEDRIVE)) {
                visiblity = mSettings.getGoogleDriveFilePicker() > 0 ? View.VISIBLE : View.GONE;
                button = (Button)findViewById(R.id.drawer_googledrive_button);
                button.setVisibility(visiblity);
                mFilePickerItems.add(String.format(launchApp, this.getString(R.string.googledrive)));
            }
            if (isAppInstalled(APP_ONEDRIVE)) {
                visiblity = mSettings.getOneDriveFilePicker() > 0 ? View.VISIBLE : View.GONE;
                button = (Button)findViewById(R.id.drawer_onedrive_button);
                button.setVisibility(visiblity);
                mFilePickerItems.add(String.format(launchApp, this.getString(R.string.onedrive)));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                button = (Button)findViewById(R.id.drawer_storage_button);
                button.setVisibility(View.VISIBLE);
                button = (Button)findViewById(R.id.drawer_createfile_button);
                button.setVisibility(View.VISIBLE);
                mFilePickerItems.add(this.getString(R.string.clear_cache));
            } else {
                button = (Button)findViewById(R.id.drawer_clear_cache_button);
                button.setVisibility(View.VISIBLE);
            }
        }
        setExtraButton();
        findViewById(R.id.drawer_app_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                externalApp();
            }
        });
        findViewById(R.id.drawer_extra_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                onExtraButtonClicked(v);
            }
        });
        findViewById(R.id.drawer_preference_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                doPreferences();
            }
        });
        setDebugButton();
        findViewById(R.id.drawer_debug_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                onDebugButtonClicked(v);
            }
        });

        findViewById(R.id.drawer_dropbox_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (launchExternalApp(mSettings.getDropboxFilePicker(), APP_DROPBOX)) getDrawer().closeDrawers();
            }
        });
        findViewById(R.id.drawer_googledrive_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (launchExternalApp(mSettings.getGoogleDriveFilePicker(), APP_GOOGLEDRIVE)) getDrawer().closeDrawers();
            }
        });
        findViewById(R.id.drawer_onedrive_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                if (launchExternalApp(mSettings.getOneDriveFilePicker(), APP_ONEDRIVE)) getDrawer().closeDrawers();
            }
        });
        findViewById(R.id.drawer_storage_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                filePicker();
            }
        });
        findViewById(R.id.drawer_createfile_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                chooseFilePicker();
            }
        });
        findViewById(R.id.drawer_clear_cache_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                confirmClearCache();
            }
        });
        findViewById(R.id.drawer_keyboard_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                doToggleSoftKeyboard();
            }
        });
        findViewById(R.id.drawer_menu_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                openOptionsMenu();
            }
        });
        findViewById(R.id.drawer_quit_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                if (mSettings.getInitialCommand().matches("(.|\n)*(^|\n)-?vim\\.app(.|\n)*")) {
                    sendKeyStrings(":confirm qa\r", true);
                } else {
                    confirmCloseWindow();
                }
            }
        });
        if (mSettings.getColorTheme() == 0) {
            findViewById(R.id.drawer_app_button).setBackgroundResource(R.drawable.sidebar_button3_dark);
            findViewById(R.id.drawer_dropbox_button).setBackgroundResource(R.drawable.sidebar_button3_dark);
            findViewById(R.id.drawer_googledrive_button).setBackgroundResource(R.drawable.sidebar_button3_dark);
            findViewById(R.id.drawer_onedrive_button).setBackgroundResource(R.drawable.sidebar_button3_dark);

            findViewById(R.id.drawer_storage_button).setBackgroundResource(R.drawable.sidebar_button2_dark);
            findViewById(R.id.drawer_createfile_button).setBackgroundResource(R.drawable.sidebar_button2_dark);
            findViewById(R.id.drawer_clear_cache_button).setBackgroundResource(R.drawable.sidebar_button2_dark);

            findViewById(R.id.drawer_menu_button).setBackgroundResource(R.drawable.extra_button_dark);
            findViewById(R.id.drawer_preference_button).setBackgroundResource(R.drawable.extra_button_dark);
            findViewById(R.id.drawer_quit_button).setBackgroundResource(R.drawable.extra_button_dark);
        }
    }

    private boolean isAppInstalled(String appPackage) {
        PackageManager packageManager = getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(appPackage);
        return (intent != null);
    }

    private boolean intentMainActivity(String app) {
        if (app == null || app.equals("")) return false;
        PackageManager pm = this.getApplicationContext().getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(app);
        if (intent == null) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            try {
                String className;
                className = Objects.requireNonNull(Objects.requireNonNull(pm.getLaunchIntentForPackage(app)).getComponent()).getClassName()+"";
                intent.setClassName(app, Objects.requireNonNull(className));
            } catch (Exception e) {
                alert(app+"\n"+this.getString(R.string.external_app_activity_error));
                return true;
            }
        }
        startActivity(intent);
        return true;
    }

    private void externalApp() {
        if (mExternalApp.equals("") || !launchExternalApp(mExternalAppMode, mExternalApp)) {
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            bld.setMessage(R.string.external_app_id_error);
            bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    doPreferences();
                }
            });
            bld.create().show();
        }
    }

    @SuppressLint("NewApi")
    private boolean launchExternalApp(int mode, String appId) {
        try {
            if (mode == 2) {
                intentMainActivity(appId);
            } else if (mode == 1){
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setPackage(appId);
                intent.setType("*/*");
                startActivityForResult(intent, REQUEST_FILE_PICKER);
            }
        } catch (Exception e) {
            try {
                intentMainActivity(appId);
            } catch (Exception ea) {
                alert(this.getString(R.string.activity_not_found));
                return false;
            }
        }
        return true;
    }

    private void confirmClearCache() {
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.confirm_clear_cache_message);
        final Runnable clearCache = new Runnable() {
            public void run() {
                if (mSyncFileObserver != null) mSyncFileObserver.clearCache();
                if (mTermService != null) mTermService.clearTMPDIR();
            }
        };
        b.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                mHandler.post(clearCache);
            }
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();

    }

    private DrawerLayout getDrawer() {
        return (DrawerLayout) findViewById(R.id.drawer_layout);
    }

    public static final int REQUEST_STORAGE        = 10000;
    public static final int REQUEST_STORAGE_DELETE = 10001;
    public static final int REQUEST_STORAGE_CREATE = 10002;
    public static final int REQUEST_FOREGROUND_SERVICE_PERMISSION = 10003;
    @SuppressLint("NewApi")
    void permissionCheckExternalStorage() {
        if (AndroidCompat.SDK < 23) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    @Override
    @SuppressLint("NewApi")
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
        case REQUEST_STORAGE:
            for (int i = 0; i < permissions.length ; i++) {
                if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        // do something
                    } else {
                        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            doWarningDialog(this.getString(R.string.storage_permission_error), this.getString(R.string.storage_permission_warning), "storage_permission", false);
                        }
                    }
                    break;
                }
            }
            break;
        case REQUEST_FILE_PICKER:
        case REQUEST_STORAGE_DELETE:
        case REQUEST_STORAGE_CREATE:
            for (int i = 0; i < permissions.length ; i++) {
                if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            Toast toast = Toast.makeText(this, this.getString(R.string.storage_permission_error), Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                        } else {
                            doWarningDialog(this.getString(R.string.storage_permission_error), this.getString(R.string.storage_permission_warning), "storage_permission", false);
                        }
                    }
                    switch (requestCode) {
                    case REQUEST_FILE_PICKER:
                        intentFilePicker();
                        break;
                    case REQUEST_STORAGE_DELETE:
                        doFileDelete();
                        break;
                    case REQUEST_STORAGE_CREATE:
                        doFileCreate();
                        break;
                    default:
                        break;
                    }
                }
            }
            break;
        case REQUEST_FOREGROUND_SERVICE_PERMISSION:
            for (int i = 0; i < permissions.length ; i++) {
                if (permissions[i].equals(Manifest.permission.FOREGROUND_SERVICE)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        this.getApplicationContext().startForegroundService(TSIntent);
                    } else {
                        startService(TSIntent);
                    }
                    break;
                }
            }
            break;
        default:
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            break;
        }
    }

    private String makePathFromBundle(Bundle extras) {
        if (extras == null || extras.size() == 0) {
            return "";
        }

        String[] keys = new String[extras.size()];
        keys = extras.keySet().toArray(keys);
        Collator collator = Collator.getInstance(Locale.US);
        Arrays.sort(keys, collator);

        StringBuilder path = new StringBuilder();
        for (String key : keys) {
            String dir = extras.getString(key);
            if (dir != null && !dir.equals("")) {
                path.append(dir);
                path.append(":");
            }
        }

        return path.substring(0, path.length()-1);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!bindTermService(TSIntent, mTSConnection, BIND_AUTO_CREATE)) {
            final String MESSAGE = this.getString(R.string.faild_to_bind_to_termservice);
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            bld.setIcon(android.R.drawable.ic_dialog_alert);
            bld.setTitle(MESSAGE);
            bld.setMessage(R.string.please_restart);
            bld.setPositiveButton(R.string.quit, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    throw new IllegalStateException(MESSAGE);
                }
            });
            AlertDialog dialog = bld.create();
            dialog.show();
        }
    }

    private boolean bindTermService(Intent service, ServiceConnection conn, int flags) {
        return bindTermServiceLoop(service, conn, flags, 0);
    }

    private boolean bindTermServiceLoop(Intent service, ServiceConnection conn, int flags, int loop) {
        if (!bindService(service, conn, flags)) {
            final int END_OF_LOOP = 3;
            if (loop >= END_OF_LOOP) return false;
            try {
                Thread.sleep(1000);
                return bindTermServiceLoop(service, conn, flags, loop+1);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return true;
    }

    private static String AM_INTENT_ACTION = "com.droidterm.am.intent.action";
    private final BroadcastReceiver mBroadcastReceiever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (AM_INTENT_ACTION.equals(action)) {
                doAndroidIntent(intent.getStringExtra("action"), intent.getStringExtra("param"), intent.getStringExtra("mime"));
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setupStorageSymlinks(final Context context) {
        if (AndroidCompat.SDK < android.os.Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        try {
            File storageDir = new File(mSettings.getHomePath(), "storage");

            if (!storageDir.exists() && !storageDir.mkdirs()) {
                Log.e(TermDebug.LOG_TAG, "Unable to mkdirs() for $HOME/storage");
                return;
            }

            File sharedDir = Environment.getExternalStorageDirectory();
            Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath());

            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            Os.symlink(downloadsDir.getAbsolutePath(), new File(storageDir, "downloads").getAbsolutePath());

            File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            Os.symlink(dcimDir.getAbsolutePath(), new File(storageDir, "dcim").getAbsolutePath());

            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            Os.symlink(picturesDir.getAbsolutePath(), new File(storageDir, "pictures").getAbsolutePath());

            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            Os.symlink(musicDir.getAbsolutePath(), new File(storageDir, "music").getAbsolutePath());

            File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            Os.symlink(moviesDir.getAbsolutePath(), new File(storageDir, "movies").getAbsolutePath());

            final File[] dirs = context.getExternalFilesDirs(null);
            if (dirs != null && dirs.length > 1) {
                for (int i = 1; i < dirs.length; i++) {
                    File dir = dirs[i];
                    if (dir == null) continue;
                    String symlinkName = "external-" + i;
                    Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                }
            }
        } catch (Exception e) {
            Log.e(TermDebug.LOG_TAG, "Error setting up link", e);
        }
    }

    private void populateViewFlipper() {
        if (mTermService != null) {
            mTermSessions = mTermService.getSessions();

            if (mTermSessions.size() == 0) {
                try {
                    mTermSessions.add(createTermSession());
                } catch (IOException e) {
                    Toast.makeText(this, "Failed to start terminal session", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }

            mTermSessions.addCallback(this);

            for (TermSession session : mTermSessions) {
                EmulatorView view = createEmulatorView(session);
                mViewFlipper.addView(view);
            }

            updatePrefs();

            if (onResumeSelectWindow >= 0) {
                mViewFlipper.setDisplayedChild(onResumeSelectWindow);
                onResumeSelectWindow = -1;
            }
            mViewFlipper.onResume();
            if (!mHaveFullHwKeyboard) doShowSoftKeyboard();
        }
    }

    private void showAds(boolean show) {
        if (BuildConfig.DEBUG) return;
    }

    @Override
    public void onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);

        saveSyncFileObserver();
        if (mStopServiceOnFinish) {
            stopService(TSIntent);
            mFirstInputtype = true;
            mFunctionBar = -1;
            mOrientation = -1;
            final int MAX_SYNC_FILES = 100;
            if (FLAVOR_VIM && mSyncFileObserver != null) mSyncFileObserver.clearCache(MAX_SYNC_FILES);
            mKeepScreenHandler.removeCallbacksAndMessages(null);
        }
        File cacheDir = new File(INTENT_CACHE_DIR);
        SyncFileObserver.delete(cacheDir);
        mTermService = null;
        mTSConnection = null;
        super.onDestroy();
    }

    @SuppressLint("NewApi")
    private void restart() {
        if (AndroidCompat.SDK >= 11) {
            recreate();
        } else {
            finish();
            startActivity(getIntent());
        }
    }

    protected static TermSession createTermSession(Context context, TermSettings settings, String initialCommand) throws IOException {
        GenericTermSession session = new ShellTermSession(settings, initialCommand);
        // XXX We should really be able to fetch this from within TermSession
        session.setProcessExitMessage(context.getString(R.string.process_exit_message));

        return session;
    }

    private TermSession createTermSession() throws IOException {
        TermSettings settings = mSettings;
        TermSession session = createTermSession(this, settings, getInitialCommand());
        session.setFinishCallback(mTermService);
        return session;
    }

    boolean mFirst = true;
    private String getInitialCommand() {
        String cmd = mSettings.getInitialCommand();
        cmd = mTermService.getInitialCommand(cmd, (mFirst && mTermService.getSessions().size() == 0));
        if (!FLAVOR_VIM) {
            TermVimInstaller.doInstallTerm(Term.this);
            permissionCheckExternalStorage();
        } else if (TermVimInstaller.doInstallVim) {
            final boolean vimApp = cmd.replaceAll(".*\n", "").matches("vim.app\\s*");
            cmd = cmd.replaceAll("\n-?vim.app", "");
            TermVimInstaller.installVim(Term.this, new Runnable(){
                @Override
                public void run() {
                    if (vimApp) {
                        if (getCurrentTermSession() != null) {
                            sendKeyStrings("vim.app\n", false);
                        } else {
                            ShellTermSession.setPostCmd("vim.app\n");
                        }
                    }
                    permissionCheckExternalStorage();
                }
            });
        } else {
            showVimTips();
            permissionCheckExternalStorage();
        }
        mFirst = false;
        return cmd;
    }

    private static Random mRandom = new Random();
    private void showVimTips() {
        if (!mVimFlavor) return;
        if (true) return;

        String title = this.getString(R.string.tips_vim_title);
        String key = "do_warning_vim_tips";
        String[] list = this.getString(R.string.tips_vim_list).split("\\|");
        int index = mRandom.nextInt(list.length-1) + 1;
        String message = list[index];
        doWarningDialog(title, message, key, false);
    }

    private void doWarningDialog(String title, String message, String key, boolean dontShowAgain) {
        boolean warning = getPrefBoolean(Term.this, key, true);
        if (!warning) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        if (title != null) builder.setTitle(title);
        if (message != null) builder.setMessage(message);
        LayoutInflater flater = LayoutInflater.from(this);
        View view = flater.inflate(R.layout.alert_checkbox, null);
        builder.setView(view);
        final CheckBox cb = (CheckBox)view.findViewById(R.id.dont_show_again);
        final String warningKey = key;
        cb.setChecked(dontShowAgain);
        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int m) {
                if (cb.isChecked()) {
                    setPrefBoolean(Term.this, warningKey, false);
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positive.requestFocus();
    }

    static boolean mFirstInputtype = true;
    private TermView createEmulatorView(TermSession session) {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        TermView emulatorView = new TermView(this, session, metrics);

        emulatorView.setExtGestureListener(new EmulatorViewGestureListener(emulatorView));
        emulatorView.setDoubleTapListener(new EmulatorViewDoubleTapListener(emulatorView));
        emulatorView.setOnKeyListener(mKeyListener);
        registerForContextMenu(emulatorView);

        if (mFirstInputtype) {
            emulatorView.setIMEInputTypeDefault(mSettings.getImeDefaultInputtype());
            emulatorView.setImeShortcutsAction(mSettings.getImeDefaultInputtype());
            mFirstInputtype = false;
        }
        return emulatorView;
    }

    private TermSession getCurrentTermSession() {
        SessionList sessions = mTermSessions;
        if (sessions == null || sessions.size() == 0) {
            return null;
        } else {
            if (mViewFlipper != null) {
                int disp = mViewFlipper.getDisplayedChild();
                if (disp < sessions.size()) return sessions.get(disp);
            }
        }
        return null;
    }

    private EmulatorView getCurrentEmulatorView() {
        return (EmulatorView) mViewFlipper.getCurrentView();
    }

    private void updatePrefs() {
        invalidateOptionsMenu();
        mUseKeyboardShortcuts = mSettings.getUseKeyboardShortcutsFlag();

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        setEditTextVisibility();
        setFunctionKeyVisibility();
        mViewFlipper.updatePrefs(mSettings);

        for (View v : mViewFlipper) {
            ((EmulatorView) v).setDensity(metrics);
            ((TermView) v).updatePrefs(mSettings);
            setPreIMEShortsuts((EmulatorView) v);
            if (!mSettings.useCookedIME()) {
                ((EmulatorView) v).setIMECtrlBeginBatchEditDisable(false);
            }
        }
        EmulatorView.setCursorHeight(mSettings.getCursorStyle());
        EmulatorView.setCursorBlink(mSettings.getCursorBlink());
        EmulatorView.setForceFlush(mSettings.getCursorStyle() >= 2);
        EmulatorView.setIMEInputTypeDefault(mSettings.getImeDefaultInputtype());

        if (mTermSessions != null) {
            for (TermSession session : mTermSessions) {
                ((GenericTermSession) session).updatePrefs(mSettings);
            }
        }

        {
            Window win = getWindow();
            WindowManager.LayoutParams params = win.getAttributes();
            final int FULLSCREEN = WindowManager.LayoutParams.FLAG_FULLSCREEN;
            int desiredFlag = mSettings.showStatusBar() ? 0 : FULLSCREEN;
            if (desiredFlag != (params.flags & FULLSCREEN)) {
                if (mAlreadyStarted) {
                    // Can't switch to/from fullscreen after
                    // starting the activity.
                    restart();
                } else {
                    win.setFlags(desiredFlag, FULLSCREEN);
                }
            }
        }

        int orientation = getOrientation();
        int o = 0;
        if (orientation == 0) {
            o = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        } else if (orientation == 1) {
            o = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        } else if (orientation == 2) {
            o = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        } else {
            /* Shouldn't be happened. */
        }
        setRequestedOrientation(o);
        mKeepScreenEnableAuto = mSettings.getKeepScreenAtStartup();
    }

    static int mOrientation = -1;
    private int getOrientation() {
        if (mOrientation == -1) return mSettings.getScreenOrientation();
        return mOrientation;
    }

    private void doScreenMenu() {
        String screenLockItem;
        final boolean keepScreen = ((getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
        if (keepScreen) {
            screenLockItem = this.getString(R.string.disable_keepscreen);
        } else {
            screenLockItem = this.getString(R.string.enable_keepscreen);
        }
        final String[] items = {this.getString(R.string.dialog_title_orientation_preference), this.getString(R.string.share_screen_text), this.getString(R.string.copy_screen), screenLockItem, this.getString(R.string.reset)};
        final Toast toastReset = Toast.makeText(this,R.string.reset_toast_notification, Toast.LENGTH_LONG);
        new AlertDialog.Builder(this)
                .setTitle(this.getString(R.string.screen))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            setCurrentOrientation();
                        } else if (which == 1) {
                            doShareAll();
                        } else if (which == 2) {
                            doCopyAll();
                        } else if (which == 3) {
                            if (keepScreen) mKeepScreenEnableAuto = false;
                            doToggleKeepScreen();
                            if (!keepScreen) mKeepScreenEnableAuto = true;
                        } else {
                            doResetTerminal(true);
                            updatePrefs();
                            toastReset.setGravity(Gravity.CENTER, 0, 0);
                            toastReset.show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doWindowMenu() {
        final String[] items = {this.getString(R.string.new_window), this.getString(R.string.close_window)};
        new AlertDialog.Builder(this)
                .setTitle(this.getString(R.string.menu_window))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            doCreateNewWindow();
                        } else if (which == 1) {
                            confirmCloseWindow();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setCurrentOrientation() {
        final String[] items = getResources().getStringArray(R.array.entries_orientation_preference);
        new AlertDialog.Builder(this)
                .setTitle(this.getString(R.string.dialog_title_orientation_preference))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mOrientation = which;
                        doResetTerminal();
                        updatePrefs();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setPreIMEShortsuts(EmulatorView v) {
        final SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean value = mPrefs.getBoolean("AltGrave", false);
        v.setPreIMEShortcut("AltGrave", value);
        value = mPrefs.getBoolean("AltEsc", false);
        v.setPreIMEShortcut("AltEsc", value);
        value = mPrefs.getBoolean("AltSpace", false);
        v.setPreIMEShortcut("AltSpace", value);
        value = mPrefs.getBoolean("CtrlSpace", false);
        v.setPreIMEShortcut("CtrlSpace", value);
        value = mPrefs.getBoolean("ShiftSpace", false);
        v.setPreIMEShortcut("ShiftSpace", value);
        value = mPrefs.getBoolean("ZENKAKU_HANKAKU", false);
        v.setPreIMEShortcut("ZENKAKU_HANKAKU", value);
        value = mPrefs.getBoolean("GRAVE", false);
        v.setPreIMEShortcut("GRAVE", value);
        value = mPrefs.getBoolean("SWITCH_CHARSET", false);
        v.setPreIMEShortcut("SWITCH_CHARSET", value);
        value = mPrefs.getBoolean("CtrlJ", false);
        v.setPreIMEShortcut("CtrlJ", value);
        v.setPreIMEShortcutsAction(mSettings.getImeShortcutsAction());
    }

    @Override
    public void onPause() {
        unregisterReceiver(mBroadcastReceiever);

        if (AndroidCompat.SDK < 5) {
            /* If we lose focus between a back key down and a back key up,
               we shouldn't respond to the next back key up event unless
               we get another key down first */
            mBackKeyPressed = false;
        }

        /* Explicitly close the input method
           Otherwise, the soft keyboard could cover up whatever activity takes
           our place */
        final IBinder token = mViewFlipper.getWindowToken();
        new Thread() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null && token != null) imm.hideSoftInputFromWindow(token, 0);
            }
        }.start();

        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        registerReceiver(mBroadcastReceiever, new IntentFilter(AM_INTENT_ACTION));

        EmulatorView v = (EmulatorView) mViewFlipper.getCurrentView();
        if (v != null) {
            v.updateSize(true);
            doResetTerminal();
        }
        if (mSyncFileObserver != null) {
            mSyncFileObserver.setActivity(this);
        } else {
            restoreSyncFileObserver();
        }
    }

    @Override
    protected void onStop() {
        if (mViewFlipper != null) mViewFlipper.onPause();
        if (mTermSessions != null) {
            mTermSessions.removeCallback(this);

//            if (mWinListAdapter != null) {
//                mTermSessions.removeCallback(mWinListAdapter);
//                mTermSessions.removeTitleChangedListener(mWinListAdapter);
//                mViewFlipper.removeCallback(mWinListAdapter);
//            }
        }

        if (mViewFlipper != null) mViewFlipper.removeAllViews();

        try {
            unbindService(mTSConnection);
        } catch (Exception e) {
            e.printStackTrace();
        }

        int webViewSize = WebViewActivity.getFontSize();
        PrefValue pv = new PrefValue(this);
        if (webViewSize != pv.getInt("mWebViewSize", 140)) {
            pv.setInt("mWebViewSize", webViewSize);
        }

        super.onStop();
    }

    private int mPrevHaveFullHwKeyboard = -1;
    private boolean mHideFunctionBar = false;
    private boolean checkHaveFullHwKeyboard(Configuration c) {
        boolean haveFullHwKeyboard =  (c.keyboard == Configuration.KEYBOARD_QWERTY) &&
                                            (c.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO);
        if (mPrevHaveFullHwKeyboard == -1 || (haveFullHwKeyboard != (mPrevHaveFullHwKeyboard == 1))) {
            mHideFunctionBar = haveFullHwKeyboard && mSettings.getAutoHideFunctionbar();
            if (!haveFullHwKeyboard) mFunctionBar = mSettings.showFunctionBar() ? 1 : 0;
            if (haveFullHwKeyboard) doWarningHwKeyboard();
            // if (!haveFullHwKeyboard) mOnelineTextBox = mSettings.showOnelineTextBox() ? 1 : 0;
        }
        mPrevHaveFullHwKeyboard = haveFullHwKeyboard ? 1 : 0;
        return haveFullHwKeyboard;
    }

    private void doWarningHwKeyboard() {
        if (!mVimFlavor) return;
        doWarningDialog(null, this.getString(R.string.keyboard_warning), "do_warning_physical_keyboard", false);
    }

    private void setSoftInputMode(boolean haveFullHwKeyboard) {
        int mode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        if (haveFullHwKeyboard) {
            mode |= WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        } else {
            mode |= WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
        }
        getWindow().setSoftInputMode(mode);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mHaveFullHwKeyboard = checkHaveFullHwKeyboard(newConfig);
        setSoftInputMode(mHaveFullHwKeyboard);

        EmulatorView v = (EmulatorView) mViewFlipper.getCurrentView();
        if (v != null) {
            v.updateSize(false);
            v.onConfigurationChangedToEmulatorView(newConfig);
        }

//        if (mWinListAdapter != null) {
            // Force Android to redraw the label in the navigation dropdown
//            mWinListAdapter.notifyDataSetChanged();
//        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        menu.removeItem(R.id.menu_close_window);
        menu.removeItem(R.id.menu_copy_screen);
        menu.removeItem(R.id.menu_new_window);
        menu.removeItem(R.id.menu_plus);
        menu.removeItem(R.id.menu_reset);
        menu.removeItem(R.id.menu_send_email);
        menu.removeItem(R.id.menu_special_keys);
        menu.removeItem(R.id.menu_toggle_wakelock);
        menu.removeItem(R.id.menu_toggle_wifilock);
        menu.removeItem(R.id.menu_update);
        menu.removeItem(R.id.menu_window_list);
        menu.removeItem(R.id.menu_x);
        if (!FLAVOR_VIM) menu.removeItem(R.id.menu_share_text);
        if (!FLAVOR_VIM) menu.removeItem(R.id.menu_edit_vimrc);
        if (!FLAVOR_VIM) menu.removeItem(R.id.menu_reload);
        if (!FLAVOR_VIM) menu.removeItem(R.id.menu_tutorial);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item;
        boolean visibility;
        item = menu.findItem(R.id.menu_toggle_soft_keyboard);
        visibility = (mSettings.getBackKeyAction() != TermSettings.BACK_KEY_TOGGLE_IME)
                  && (mSettings.getBackKeyAction() != TermSettings.BACK_KEY_TOGGLE_IME_ESC);
        item.setVisible(visibility);
        item = menu.findItem(R.id.menu_disable_keepscreen);
        visibility = ((getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
        item.setVisible(visibility);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_preferences) {
            doPreferences();
        } else if (id == R.id.menu_paste) {
            doPaste();
        } else if (id == R.id.menu_copy_screen) {
            doCopyAll();
        } else if (id == R.id.menu_new_window) {
            doCreateNewWindow();
        } else if (id == R.id.menu_plus) {
            EmulatorView view = getCurrentEmulatorView();
            if (view != null) {
                int key = mSettings.getActionBarPlusKeyAction();
                return doSendActionBarKey(view, key);
            }
//        } else if (id == R.id.menu_minus) {
//            EmulatorView view = getCurrentEmulatorView();
//            if (view != null) {
//                int key = mSettings.getActionBarMinusKeyAction();
//                return doSendActionBarKey(view, key);
//            }
        } else if (id == R.id.menu_close_window) {
            confirmCloseWindow();
        } else if (id == R.id.menu_x) {
            EmulatorView view = getCurrentEmulatorView();
            if (view != null) {
                int key = mSettings.getActionBarXKeyAction();
                return doSendActionBarKey(view, key);
            }
//        } else if  (id == R.id.menu_user) {
//            EmulatorView view = getCurrentEmulatorView();
//            if (view != null) {
//                int key = mSettings.getActionBarUserKeyAction();
//                return doSendActionBarKey(view, key);
//            }
        } else if (id == R.id.menu_window_list) {
//            startActivityForResult(new Intent(this, WindowList.class), REQUEST_CHOOSE_WINDOW);
        } else if (id == R.id.menu_screen) {
            doScreenMenu();
        } else if (id == R.id.menu_window) {
            doWindowMenu();
        } else if (id == R.id.menu_reset) {
            doResetTerminal(true);
            updatePrefs();
            Toast toast = Toast.makeText(this,R.string.reset_toast_notification,Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        } else if (id == R.id.menu_share_text) {
            shareIntentTextDialog();
        } else if (id == R.id.menu_send_email) {
            doEmailTranscript();
        } else if (id == R.id.menu_special_keys) {
            doDocumentKeys();
        } else if (id == R.id.menu_toggle_soft_keyboard) {
            doToggleSoftKeyboard();
        } else if (id == R.id.menu_toggle_function_bar) {
            setFunctionBar(2);
        } else if (id == R.id.menu_update) {
            networkUpdate();
        } else if (id == R.id.menu_tutorial) {
            sendKeyStrings(":Vimtutor\r", true);
        } else if (id == R.id.menu_toggle_wakelock) {
            doToggleWakeLock();
        } else if (id == R.id.menu_disable_keepscreen) {
            mKeepScreenEnableAuto = false;
            doToggleKeepScreen();
        } else if (id == R.id.menu_toggle_wifilock) {
            doToggleWifiLock();
        } else if (id == R.id.menu_edit_vimrc) {
            sendKeyStrings(":call ATETermVimVimrc()\r", true);
        } else if (id == R.id.menu_text_box) {
            setEditTextView(2);
        } else if (id == R.id.menu_drawer) {
            toggleDrawer();
        } else if (id == R.id.menu_reload) {
            fileReload();
        } else if  (id == R.id.action_help) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Intent intent = new Intent(this, WebViewActivity.class);
                intent.putExtra("url", getString(R.string.help_url));
                startActivity(intent);
            } else {
                Intent openHelp = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.help_url)));
                startActivity(openHelp);
            }
        } else if (id == R.id.menu_quit) {
            EmulatorView view = getCurrentEmulatorView();
            if (view != null) doSendActionBarKey(view, mSettings.getActionBarQuitKeyAction());
        }
        return super.onOptionsItemSelected(item);
    }

    private void networkUpdate() {
    }

    public static String getVersionName(Context context) {
        PackageManager pm = context.getPackageManager();
        String versionName = "";
        try {
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionName;
    }

    private boolean doSendActionBarKey(EmulatorView view, int key) {
        if (view == null) return false;
        if (key == 999) {
            // do nothing
        } else if (key == 1002) {
            doToggleSoftKeyboard();
        } else if (key == 1249) {
            doPaste();
        } else if (key == 1250) {
            doCreateNewWindow();
        } else if (key == 1251) {
            if (mTermSessions != null) {
                if (mSettings.getInitialCommand().matches("(.|\n)*(^|\n)-?vim\\.app(.|\n)*")) {
                    sendKeyStrings(":confirm qa\r", true);
                } else {
                    confirmCloseWindow();
                }
            }
        } else if (key == 1252) {
            InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        } else if (key == 1253) {
            if (mSettings.getInitialCommand().matches("(.|\n)*(^|\n)-?vim\\.app(.|\n)*")) {
                sendKeyStrings(":confirm qa\r", true);
            } else {
                confirmCloseWindow();
            }
        } else if (key == 1254) {
            view.sendFnKeyCode();
        } else if (key == KeycodeConstants.KEYCODE_ALT_LEFT) {
            view.sendAltKeyCode();
        } else if (key == KeycodeConstants.KEYCODE_CTRL_LEFT) {
            view.sendControlKeyCode();
        } else if (key == 1247) {
            sendKeyStrings(":", false);
        } else if (key == 1255) {
            setFunctionBar(2);
        } else if (key == 1260) {
            int action = mSettings.getImeShortcutsAction();
            if (action == 0) {
                doToggleSoftKeyboard();
            } else if (action == 1261) {
                doEditTextFocusAction();
            } else {
                setEditTextAltCmd();
                view.doImeShortcutsAction();
            }
            return true;
        } else if (key == 1261) {
            doEditTextFocusAction();
        } else if (key == 1360 || (key >= 1351 && key <= 1354)) {
            if (setEditTextAltCmd()) return true;
            view.doImeShortcutsAction(key-1300);
        } else if (key == 1361) {
            keyEventSender(KEYEVENT_SENDER_SHIFT_SPACE);
        } else if (key == 1362) {
            keyEventSender(KEYEVENT_SENDER_ALT_SPACE);
        } else if (key == 1363) {
            mInvertCursorDirection = !mInvertCursorDirection;
            mDefaultInvertCursorDirection = mInvertCursorDirection;
            setCursorDirectionLabel();
        } else if (key == 1364) {
            if (getInvertCursorDirection() != getDefaultInvertCursorDirection()) {
                mInvertCursorDirection = getDefaultInvertCursorDirection();
                setCursorDirectionLabel();
            }
        } else if (key == 1355) {
            toggleDrawer();
        } else if (key == 1356) {
            sendKeyStrings(":tabnew\r", true);
            openDrawer();
        } else if (key == 1357) {
            setFunctionBar(2);
        } else if (key == 1358) {
            setCurrentOrientation();
        } else if (key == KeycodeConstants.KEYCODE_ESCAPE) {
            view.restartInputGoogleIme();
            if (onelineTextBoxEsc()) return true;
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, key);
            dispatchKeyEvent(event);
            event = new KeyEvent(KeyEvent.ACTION_UP, key);
            dispatchKeyEvent(event);
            if ((mSettings.getViCooperativeMode() & 1) != 0) {
                view.setImeShortcutsAction(mSettings.getImeDefaultInputtype());
            }
        } else if (key > 0) {
            int state = view.getControlKeyState();
            if ((key == KeycodeConstants.KEYCODE_DPAD_UP) ||
                (key == KeycodeConstants.KEYCODE_DPAD_DOWN) ||
                (key == KeycodeConstants.KEYCODE_DPAD_LEFT) ||
                (key == KeycodeConstants.KEYCODE_DPAD_RIGHT)) {
                    view.setControlKeyState(UNPRESSED);
            }
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, key);
            dispatchKeyEvent(event);
            event = new KeyEvent(KeyEvent.ACTION_UP, key);
            dispatchKeyEvent(event);
            view.setControlKeyState(state);
        }
        return true;
    }

    private static boolean mInvertCursorDirection = false;
    private boolean getInvertCursorDirection() {
        return mInvertCursorDirection;
    }

    private static boolean mDefaultInvertCursorDirection = false;
    private boolean getDefaultInvertCursorDirection() {
        return mDefaultInvertCursorDirection;
    }

    private void setCursorDirectionLabel() {
        if (!getInvertCursorDirection()) {
            ((Button)findViewById(R.id.button_right)).setText("▶");
            ((Button)findViewById(R.id.button_left)).setText("◀");
            ((Button)findViewById(R.id.button_up)).setText("▲");
            ((Button)findViewById(R.id.button_down)).setText("▼");
        } else {
            ((Button)findViewById(R.id.button_right)).setText("▼");
            ((Button)findViewById(R.id.button_left)).setText("▲");
            ((Button)findViewById(R.id.button_up)).setText("◀");
            ((Button)findViewById(R.id.button_down)).setText("▶");
        }
    }

    private void toggleDrawer() {
        DrawerLayout mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else {
            mDrawer.openDrawer(GravityCompat.START);
        }
    }

    private void openDrawer() {
        DrawerLayout mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (!mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.openDrawer(GravityCompat.START);
        }
    }

    private boolean sendKeyStrings(String str, boolean esc) {
        TermSession session = getCurrentTermSession();
        if (session == null) return false;
        if (esc) str = "\u001b"+str;
        session.write(str);
        return true;
    }

    private void doCreateNewWindow() {
        if (mTermSessions == null) {
            Log.w(TermDebug.LOG_TAG, "Couldn't create new window because mTermSessions == null");
            return;
        }

        try {
            TermSession session = createTermSession();

            mTermSessions.add(session);

            TermView view = createEmulatorView(session);
            view.updatePrefs(mSettings);

            mViewFlipper.addView(view);
            mViewFlipper.setDisplayedChild(mViewFlipper.getChildCount()-1);
            doWarningDialog(null, this.getString(R.string.switch_windows_warning), "switch_window", false);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to create a session", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmCloseWindow() {
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.confirm_window_close_message);
        final Runnable closeWindow = new Runnable() {
            public void run() {
                doCloseWindow();
            }
        };
        b.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
           public void onClick(DialogInterface dialog, int id) {
               dialog.dismiss();
               mHandler.post(closeWindow);
           }
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void doCloseWindow() {
        if (mTermSessions == null) {
            return;
        }

        EmulatorView view = getCurrentEmulatorView();
        if (view == null) {
            return;
        }
        if (mTermSessions.size() == 1 && !mHaveFullHwKeyboard) {
            doHideSoftKeyboard();
        }
        TermSession session = mTermSessions.remove(mViewFlipper.getDisplayedChild());
        view.onPause();
        session.finish();
        mViewFlipper.removeView(view);
        if (mTermSessions.size() != 0) {
            mViewFlipper.showNext();
        }
    }

    public boolean checkImplicitIntent(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
        if (apps.size() < 1) {
            alert(this.getString(R.string.storage_intent_error));
            return false;
        }
        return true;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void filePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_FILE_PICKER);
        } else {
            intentFilePicker();
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void intentFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        if (checkImplicitIntent(this, intent)) startActivityForResult(intent, REQUEST_FILE_PICKER);
    }

    private void chooseFilePicker() {
        final String[] items = new String[mFilePickerItems.size()];
        for (int i = 0; i < mFilePickerItems.size(); i++) {
            items[i] = mFilePickerItems.get(i);
        }
        new AlertDialog.Builder(this).setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String item = items[which];
                if (item == null) {
                    // do nothing
                } else if (Term.this.getString(R.string.create_file).equals(item)) {
                    fileCreate();
                } else if (Term.this.getString(R.string.delete_file).equals(item)) {
                    fileDelete();
                } else if (item.matches(".*"+Term.this.getString(R.string.dropbox)+".*")) {
                    intentMainActivity(APP_DROPBOX);
                } else if (item.matches(".*"+Term.this.getString(R.string.googledrive)+".*")) {
                    intentMainActivity(APP_GOOGLEDRIVE);
                } else if (item.matches(".*"+Term.this.getString(R.string.onedrive)+".*")) {
                    intentMainActivity(APP_ONEDRIVE);
                } else if (Term.this.getString(R.string.clear_cache).equals(item)) {
                    confirmClearCache();
                }
            }
        }).show();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void fileDelete() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_DELETE);
        } else {
            doFileDelete();
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void doFileDelete() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        if (checkImplicitIntent(this, intent)) startActivityForResult(intent, REQUEST_FILE_DELETE);
    }

    private void confirmDelete(final Uri uri) {
        String path = getPath(this, uri);
        if (path == null) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null, null);
            path = handleOpenDocument(uri, cursor);
            if (path == null) {
                alert(this.getString(R.string.storage_read_error));
                return;
            }
        }
        String file = new File(path).getName();
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(file);
        b.setPositiveButton(this.getString(R.string.delete_file), new DialogInterface.OnClickListener() {
            @SuppressLint("NewApi")
            public void onClick(DialogInterface dialog, int id) {
                try {
                    deleteDocument(getContentResolver(), uri);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void fileCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_CREATE);
        } else {
            doFileCreate();
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void doFileCreate() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "Newfile.txt");
        if (checkImplicitIntent(this, intent)) startActivityForResult(intent, REQUEST_FILE_PICKER);
    }

    private void fileReload() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setTitle(this.getString(R.string.reload_file_title));

        LayoutInflater inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout_encoding = inflater.inflate(R.layout.force_encoding, null);
        final AutoCompleteTextView  textView = (AutoCompleteTextView)layout_encoding.findViewById((R.id.autocomplete_encoding));

        ArrayAdapter<String> ac_adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, IconvHelper.encodings);
        textView.setAdapter(ac_adapter);
        textView.setThreshold(1);

        ArrayAdapter<String> sp_adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, IconvHelper.encodings);
        sp_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner sp_encoding = (Spinner) layout_encoding.findViewById(R.id.spinner_encoding);
        sp_encoding.setAdapter(sp_adapter);

        builder.setView(layout_encoding);
        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int m) {
                String encoding = textView.getText().toString();
                String cmd = ":e!";
                if (!encoding.equals("")) cmd += " ++enc="+encoding;
                sendKeyStrings(cmd+"\r", true);
            }
        });
        builder.setNegativeButton(android.R.string.no, null);
        builder.create().show();

        sp_encoding.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                textView.setText(IconvHelper.encodings[position], null);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                textView.setText("", null);
            }
        });

    }

    public final static String SHELL_ESCAPE = "([ ()%#&$|])";
    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        switch (request) {
            case REQUEST_FILE_DELETE:
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    confirmDelete(uri);
                }
                break;
            case REQUEST_FILE_PICKER:
                String path = null;
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    path = getPath(this, uri);
                    if (path == null) {
                        try {
                            Cursor cursor = getContentResolver().query(uri, null, null, null, null, null);
                            if (mSyncFileObserver != null) {
                                path = handleOpenDocument(uri, cursor);
                                if (path == null) {
                                    alert(this.getString(R.string.storage_read_error));
                                    break;
                                }
                                String fname = new File(path).getName();
                                if (path != null && mSyncFileObserver != null) {
                                    path = mSyncFileObserver.getObserverDir() + path;
                                    if (path.equals("") || !mSyncFileObserver.putUriAndLoad(uri, path)) {
                                        alert(fname+"\n"+this.getString(R.string.storage_read_error));
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e){
                            Log.d("FilePicker", e.toString());
                            alert(this.getString(R.string.storage_read_error)+"\n"+e.toString());
                            break;
                        }
                    }
                }
                if (path != null) {
                    String intentCommand = mSettings.getIntentCommand();
                    if (!intentCommand.matches("^:.*")) intentCommand = ":e ";
                    path = path.replaceAll(SHELL_ESCAPE, "\\\\$1");
                    path = intentCommand+" "+path+"\r";
                    sendKeyStrings(path, true);
                }
                break;
            case REQUEST_CHOOSE_WINDOW:
                if (result == RESULT_OK && data != null) {
                    int position = data.getIntExtra(EXTRA_WINDOW_ID, -2);
                    if (position >= 0) {
                        // Switch windows after session list is in sync, not here
                        onResumeSelectWindow = position;
                    } else if (position == -1) {
                        doCreateNewWindow();
                        onResumeSelectWindow = mTermSessions.size() - 1;
                    }
                } else {
                    // Close the activity if user closed all sessions
                    // TODO the left path will be invoked when nothing happened, but this Activity was destroyed!
                    if (mTermSessions == null || mTermSessions.size() == 0) {
                        mStopServiceOnFinish = true;
                        finish();
                    }
                }
                break;
        }
    }

    public static String getPath(final Context context, final Uri uri) {
        String realPath = getRealPathFromURI(context, uri);
        if (realPath != null && new File(realPath).canRead()) return realPath;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return null;
        }
        if (uri == null) return null;
        if (isDocumentUri(context, uri)) {
            try {
                File file = new File(getDocumentId(uri));
                if (file.exists()) return file.toString();
            } catch (Exception e){
                Log.d("FilePicker", e.toString());
            }
            if (isExternalStorageDocument(uri)) {
                final String docId = getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                File file;
                if ("primary".equalsIgnoreCase(type)) {
                    file = new File(Environment.getExternalStorageDirectory() + "/" + split[1]);
                    if (file.exists() && file.canWrite()) {
                        return file.toString();
                    }
                }

                String path = uri.getPath();
                path = path.replaceAll(":", "/");
                path = path.replaceFirst("document", "storage");
                file = new File(path);
                if (file.exists() && file.canWrite()) {
                    return file.toString();
                }
                path = uri.getPath();
                final File[] dirs = context.getExternalFilesDirs(null);
                if (dirs != null && dirs.length >= 2) {
                    for (File dir : dirs) {
                        if (dir == null) continue;
                        path = dir.getAbsolutePath().replaceAll(type.concat(".*"), "");
                        path = String.format("%s/%s/%s", path, type, split[1]);
                        path = path.replaceAll("/+", "/");
                        file = new File(path);
                        if (file.exists() && file.canWrite()) {
                            return file.toString();
                        }
                    }
                }
                return null;
            } else if (isInternalPrivateStorageDocument(uri)) {
                String path = uri.getPath();
                path = path.replaceAll(":", "/");
                path = path.replaceFirst("/document/", "");
                File file = new File(path);
                if (file.exists()) {
                    return file.toString();
                }
            }
        }
        String path = Uri.decode(uri.toString());
        final String AUTHORITY_TRESORIT = "content://com.tresorit.mobile.provider/external_files/";
        if (path != null && path.matches("^"+AUTHORITY_TRESORIT +".*$")) {
            path = Environment.getExternalStorageDirectory().toString()+path.substring(AUTHORITY_TRESORIT.length()-1);
            if (new File(path).exists()) return path;
        }
        return null;
    }

    @SuppressLint("NewApi")
    private static String getRealPathFromURI(final Context context, final Uri uri) {
        try {
            boolean isAfterKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
            // DocumentProvider
            Log.e(TermDebug.LOG_TAG,"uri:" + uri.getAuthority());
            if (isAfterKitKat && isDocumentUri(context, uri)) {
                if ("com.android.externalstorage.documents".equals(
                        uri.getAuthority())) { // ExternalStorageProvider
                    final String docId = getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory() + "/" + split[1];
                    } else {
                        return "/stroage/" + type +  "/" + split[1];
                    }
                } else if ("com.android.providers.downloads.documents".equals(
                        uri.getAuthority())) { // DownloadsProvider
                        final String id = getDocumentId(uri);
                        final Uri contentUri = ContentUris.withAppendedId(
                                Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                        return getDataColumn(context, contentUri, null, null);
                } else if ("com.android.providers.media.documents".equals(
                        uri.getAuthority())) { // MediaProvider
                    final String docId = getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    Uri contentUri = null;
                    contentUri = MediaStore.Files.getContentUri("external");
                    final String selection = "_id=?";
                    final String[] selectionArgs = new String[] {
                            split[1]
                    };
                    return getDataColumn(context, contentUri, selection, selectionArgs);
                }
            } else if ("content".equalsIgnoreCase(uri.getScheme())) { // MediaStore
                return getDataColumn(context, uri, null, null);
            } else if ("file".equalsIgnoreCase(uri.getScheme())) { // File
                return uri.getPath();
            }
        } catch (Exception e) {
            AlertDialog.Builder bld = new AlertDialog.Builder(context);
            bld.setMessage(e.getMessage());
            bld.setPositiveButton(android.R.string.ok, null);
            Log.d(TermDebug.LOG_TAG, "Showing alert dialog: " + e.toString());
            bld.create().show();
        }
        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String[] projection = {
            MediaStore.Files.FileColumns.DATA
        };
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                try {
                    final int cindex = cursor.getColumnIndexOrThrow(projection[0]);
                    return cursor.getString(cindex);
                } catch (Exception e) {
                    // do nothing
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            // Don't repeat action if intent comes from history
            return;
        }

        String action = intent.getAction();
        if (TextUtils.isEmpty(action) || !mPrivateAlias.equals(intent.getComponent())) {
            return;
        }

        // huge number simply opens new window
        // TODO: add a way to restrict max number of windows per caller (possibly via reusing BoundSession)
        switch (action) {
            case RemoteInterface.PRIVACT_OPEN_NEW_WINDOW:
                onResumeSelectWindow = Integer.MAX_VALUE;
                break;
            case RemoteInterface.PRIVACT_SWITCH_WINDOW:
                int target = intent.getIntExtra(RemoteInterface.PRIVEXTRA_TARGET_WINDOW, -1);
                if (target >= 0) {
                    onResumeSelectWindow = target;
                }
                break;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
       openOptionsMenu();
       // super.onCreateContextMenu(menu, v, menuInfo);
       // menu.setHeaderTitle(R.string.edit_text);
       // menu.add(0, PASTE_ID, 0, R.string.paste);
       // menu.add(0, COPY_ALL_ID, 0, R.string.copy_all);
       // // menu.add(0, SELECT_TEXT_ID, 0, R.string.select_text);
       // // menu.add(0, SEND_CONTROL_KEY_ID, 0, R.string.send_control_key);
       // // menu.add(0, SEND_FN_KEY_ID, 0, R.string.send_fn_key);
       // menu.add(0, SEND_FUNCTION_BAR_ID, 0, R.string.toggle_function_bar);
       // menu.add(0, SEND_MENU_ID, 0, R.string.title_functionbar_menu);
       // if (!canPaste()) {
       //   menu.getItem(PASTE_ID).setEnabled(false);
       // }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
          switch (item.getItemId()) {
          case SELECT_TEXT_ID:
            getCurrentEmulatorView().toggleSelectingText();
            return true;
          case COPY_ALL_ID:
            doCopyAll();
            return true;
          case PASTE_ID:
            doPaste();
            return true;
          case SEND_CONTROL_KEY_ID:
            doSendControlKey();
            return true;
          case SEND_FN_KEY_ID:
            doSendFnKey();
            return true;
          case SEND_MENU_ID:
              openOptionsMenu();
              return true;
          case SEND_FUNCTION_BAR_ID:
            setFunctionBar(2);
            return true;
          default:
            return super.onContextItemSelected(item);
          }
        }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        /* The pre-Eclair default implementation of onKeyDown() would prevent
           our handling of the Back key in onKeyUp() from taking effect, so
           ignore it here */
        if (AndroidCompat.SDK < 5 && keyCode == KeyEvent.KEYCODE_BACK) {
            /* Android pre-Eclair has no key event tracking, and a back key
               down event delivered to an activity above us in the back stack
               could be succeeded by a back key up event to us, so we need to
               keep track of our own back key presses */
            mBackKeyPressed = true;
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case 0xffff0990:
            String cmd = "vim.app\n";
            if (RemoteInterface.IntentCommand != null) {
                cmd += "\u001b"+RemoteInterface.IntentCommand+"\n";
            }
            if (RemoteInterface.ShareText != null) {
                String filename = mSettings.getHomePath()+"/.clipboard";
                Term.writeStringToFile(filename, "\n"+RemoteInterface.ShareText.toString());
                cmd += "\u001b"+":ATEMod _paste\n";
            }
            final String riCmd = cmd;
            TermVimInstaller.doInstallVim(Term.this, new Runnable(){
                @Override
                public void run() {
                    if (getCurrentTermSession() != null) {
                        sendKeyStrings(riCmd, false);
                    } else {
                        ShellTermSession.setPostCmd(riCmd);
                    }
                    permissionCheckExternalStorage();
                }
            }, true);
            return true;
        case 0xffff0998:
            if (mTermSessions.size() > 1) {
                return true;
            }
            // fall into next
        case 0xffff0999:
            if (mTermSessions.size() == 1 && !mHaveFullHwKeyboard) {
                doHideSoftKeyboard();
            }
            doCloseWindow();
            return true;
        case 0xffff0000:
            setFunctionBar(2);
            return true;
        case KeyEvent.KEYCODE_ESCAPE:
            if (onelineTextBoxEsc()) return true;
            break;
        case KeyEvent.KEYCODE_BACK:
            if (AndroidCompat.SDK < 5) {
                if (!mBackKeyPressed) {
                    /* This key up event might correspond to a key down
                       delivered to another activity -- ignore */
                    return false;
                }
                mBackKeyPressed = false;
            }
            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            if (drawer.isDrawerOpen(GravityCompat.START)) {
                drawer.closeDrawer(GravityCompat.START);
                return true;
            }
            if (mHaveFullHwKeyboard && mSettings.getBackAsEscFlag()) {
                sendKeyStrings("\u001b", false);
                return true;
            }
            switch (mSettings.getBackKeyAction()) {
            case TermSettings.BACK_KEY_STOPS_SERVICE:
                // mStopServiceOnFinish = true;
                // finish();
                doSendActionBarKey(getCurrentEmulatorView(), 1251);
                return true;
            case TermSettings.BACK_KEY_CLOSES_ACTIVITY:
                finish();
                return true;
            case TermSettings.BACK_KEY_CLOSES_WINDOW:
                doSendActionBarKey(getCurrentEmulatorView(), 1251);
                return true;
            case TermSettings.BACK_KEY_TOGGLE_IME:
            case TermSettings.BACK_KEY_TOGGLE_IME_ESC:
                doToggleSoftKeyboard();
                return true;
            default:
                return false;
            }
        case KeyEvent.KEYCODE_MENU:
            openOptionsMenu();
            break;
        case 0xffffffc0:
            if (mSettings.getBackKeyAction() == TermSettings.BACK_KEY_TOGGLE_IME_ESC) {
                sendKeyStrings("\u001b", false);
            }
            break;
        case 0xffff0003:
            copyFileToClipboard(mSettings.getHomePath()+"/.clipboard");
            return true;
        case 0xffff0004:
            setEditTextView(0);
            return true;
        case 0xffff0005:
            setEditTextView(1);
            return true;
        case 0xffff0006:
            setEditTextView(2);
            return true;
        case 0xffff1010:
            setFunctionBar(0);
            return true;
        case 0xffff1011:
            setFunctionBar(1);
            return true;
        case 0xffff1002:
            setFunctionBar(2);
            return true;
        case 0xffff0033:
        case 0xffff0333:
            if (!canPaste()) {
                alert(Term.this.getString(R.string.toast_clipboard_error));
                return true;
            }
            copyClipboardToFile(mSettings.getHomePath()+"/.clipboard");
            if (keyCode == 0xffff0333) sendKeyStrings(":ATEMod _paste\r", true);
            return true;
        case 0xffff1006:
        case 0xffff1007:
            mVimPaste = (keyCode == 0xffff1006) ? true : false;
            return true;
        case 0xffff1008:
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setupStorageSymlinks(this);
            }
            return true;
        case 0xffff1009:
            return true;
        case 0xffff1364:
            doSendActionBarKey(getCurrentEmulatorView(), 1364);
            return true;
        case 0xffff0056:
            doEditTextFocusAction();
            setEditTextInputType(50);
            return true;
        case 0xffff0057:
            setEditTextViewFocus(0);
            return true;
        case 0xffff0058:
            setEditTextViewFocus(1);
            return true;
        case 0xffff0061:
            keyEventSender(KEYEVENT_SENDER_SHIFT_SPACE);
            return true;
        case 0xffff0062:
            keyEventSender(KEYEVENT_SENDER_ALT_SPACE);
            return true;
        case 0xffff0030:
            clearClipBoard();
            return true;
        case 0xffff1001:
            AndroidIntent(mSettings.getHomePath() + "/.intent");
            return true;
        case 0xffff9998:
            networkUpdate();
            return true;
        default:
            return super.onKeyUp(keyCode, event);
        }
        return super.onKeyUp(keyCode, event);
    }

    private final float EDGE = (float) 50.0;
    private boolean doDoubleTapAction(MotionEvent me) {
        EmulatorView v = (EmulatorView) mViewFlipper.getCurrentView();
        if (v != null) {
            Resources resources = getApplicationContext().getResources();
            DisplayMetrics metrics = resources.getDisplayMetrics();
            float px = EDGE * (metrics.densityDpi/160.0f);
            int size = (int) Math.ceil(px);

            int height = getCurrentEmulatorView().getVisibleHeight();
            int width = getCurrentEmulatorView().getVisibleWidth();
            int rightAction = mSettings.getRightDoubleTapAction();
            int leftAction = mSettings.getLeftDoubleTapAction();
            int bottomAction = mSettings.getBottomDoubleTapAction();

            // if (mFunctionBar == 1 && rightAction == 1261 && mEditTextView) rightAction = 999;
            // if (mFunctionBar == 1 && leftAction == 1261 && mEditTextView) leftAction = 999;
            // if (mFunctionBar == 1 && bottomAction == 1261 && mEditTextView) bottomAction = 999;
            if (rightAction != 999 && (me.getX() > (width - size))) {
                doSendActionBarKey(getCurrentEmulatorView(), rightAction);
            } else if (leftAction != 999 && (me.getX() < size)) {
                doSendActionBarKey(getCurrentEmulatorView(), leftAction);
            } else if (bottomAction != 999 && me.getY() > (height - size)) {
                doSendActionBarKey(getCurrentEmulatorView(), bottomAction);
            } else {
                doSendActionBarKey(getCurrentEmulatorView(), mSettings.getDoubleTapAction());
            }
            return true;
        }
        return false;
    }

    private String INTENT_CACHE_DIR = "/data/data/"+BuildConfig.APPLICATION_ID+"/cache/intent";
    private void AndroidIntent(String filename) {
        if (filename == null) return;
        TermSession session = getCurrentTermSession();
        if (session == null) return;
        String str[] = new String[3];
        try {
            File file = new File(filename);
            BufferedReader br = new BufferedReader(new FileReader(file));

            for (int i = 0; i < 3; i++) {
                str[i] = br.readLine();
                if (str[i] == null) break;
            }
            br.close();
            doAndroidIntent(str[0], str[1], str[2]);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    private void doAndroidIntent(String str0, String str1, String str2) {
        if (str0 == null) return;
        String action = str0;
        if (action.equalsIgnoreCase("symlinks")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setupStorageSymlinks(this);
            }
            return;
        }
        if (str1 == null) return;
        if (action.equalsIgnoreCase("share.text")) {
            doShareIntentTextFile(str1);
            return;
        }
        if (action.equalsIgnoreCase("activity")) {
            try {
                startActivity(new Intent(this, Class.forName(str1)));
            } catch (Exception e) {
                e.printStackTrace();
                alert("Unknown activity:\n"+str1);
            }
            return;
        } else if (str1.matches("^%(w3m|open)%.*")) {
            str1 = str1.replaceFirst("%(w3m|open)%", "");
        } else if (action.equalsIgnoreCase("share.file")) {
            File file = new File(str1);
            if (!file.canRead()) {
                alert(this.getString(R.string.storage_read_error)+"\n"+str1);
                return;
            }
            action = "android.intent.action.VIEW";
        }
        if (str1.matches("'.*'")) {
            str1 = str1.replaceAll("^'|'$", "");
        }
        String MIME_HTML = MimeTypeMap.getSingleton().getMimeTypeFromExtension("html");
        String mime;
        String ext = "";
        int ch = str1.lastIndexOf('.');
        ext = (ch >= 0) ? str1.substring(ch + 1) : "";
        ext = ext.toLowerCase();
        ext = ext.replaceAll("(html?)#.*", "$1");
        String path = str1.replaceFirst("file://", "");
        path = path.replaceFirst("(.*\\.html?)#.*", "$1");
        if (str2 != null) {
            mime = str2;
        } else if (str1.matches("^(https?|ftp)://.*")) {
            mime = MIME_HTML;
        } else if (str1.matches("^www\\..*")) {
            mime = MIME_HTML;
        } else {
            mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime == null) mime = "";
        }
        File file = new File(path);
        Uri uri;
        if (action.matches("^.*(VIEW|EDIT).*")) {
            Intent intent = new Intent(action);
            boolean privateStorage = path.matches("/data/.*");
            boolean extFilesStorage = path.matches(TermService.getAPPEXTFILES()+"/.*");
            if (file.canRead() || str1.matches("^file://.*")) {
                if (!mime.equals(MIME_HTML) && !(privateStorage || extFilesStorage) && AndroidCompat.SDK < android.os.Build.VERSION_CODES.N) {
                    uri = Uri.fromFile(file);
                } else {
                    if (mime.equals(MIME_HTML)) {
                        try {
                            if ((mSettings.getHtmlViewerMode() == 2) && (!privateStorage)) {
                                String pkg = null;
                                String cls = null;
                                for (String key : mAltBrowser.keySet()) {
                                    if (isAppInstalled(key)) {
                                        pkg = key;
                                        cls = mAltBrowser.get(pkg);
                                        break;
                                    }
                                }
                                if (pkg != null) {
                                    try {
                                        Intent altIntent = new Intent(action);
                                        altIntent.setComponent(new ComponentName(pkg, cls));
                                        uri = Uri.parse(path);
                                        altIntent.setDataAndType(uri, mime);
                                        startActivity(altIntent);
                                        return;
                                    } catch (Exception altWebViewErr) {
                                        Log.d(TermDebug.LOG_TAG, altWebViewErr.getMessage());
                                    }
                                }
                            }
                            intent = new Intent(this, WebViewActivity.class);
                            intent.putExtra("url", file.toString());
                            startActivity(intent);
                            return;
                        } catch (Exception webViewErr) {
                            Log.d(TermDebug.LOG_TAG, webViewErr.getMessage());
                        }
                    }
                    try {
                        uri = FileProvider.getUriForFile(getApplicationContext(), BuildConfig.APPLICATION_ID + ".fileprovider", file);
                        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception e) {
                        String hash = getHashString(file.toString());
                        if (!ext.equals("")) hash += "."+ext;

                        File cacheDir = new File(INTENT_CACHE_DIR);
                        File cache = new File(cacheDir.toString()+"/"+hash);
                        if (cache.isDirectory()) {
                            SyncFileObserver.delete(cache);
                        }
                        if (!cacheDir.isDirectory()) {
                            cacheDir.mkdirs();
                        }
                        if (!copyFile(file, cache)) return;
                        try {
                            uri = FileProvider.getUriForFile(getApplicationContext(), BuildConfig.APPLICATION_ID + ".fileprovider", cache);
                            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception makeCacheErr) {
                            alert(this.getString(R.string.prefs_read_error_title)+"\n"+path);
                            return;
                        }
                    }
                }
            } else {
                uri = Uri.parse(path);
                if (mime.equals("")) mime = MIME_HTML;
            }
            if (mime.equals("")) mime = "text/*";
            intent.setDataAndType(uri, mime);
            try {
                if (mSettings.getHtmlViewerMode() == 1) {
                    intent = new Intent(this, WebViewActivity.class);
                    intent.putExtra("url", uri.toString());
                    startActivity(intent);
                } else {
                    intent.setAction(action);
                    startActivity(intent);
                }
            } catch (Exception e) {
                alert(Term.this.getString(R.string.storage_read_error));
            }
        } else {
            alert("Unknown action:\n"+action);
        }
    }

    private boolean copyFile(File src, File dst) {
        try {
            InputStream is = new FileInputStream(src);
            BufferedInputStream reader = new BufferedInputStream(is);

            OutputStream os = new FileOutputStream(dst);
            BufferedOutputStream writer = new BufferedOutputStream(os);
            byte buf[] = new byte[4096];
            int len;
            while ((len = reader.read(buf)) != -1) {
                writer.write(buf, 0, len);
            }
            writer.flush();
            writer.close();
            reader.close();
        } catch(Exception e) {
            return false;
        }
        return true;
    }

    private final static String HASH_ALGORITHM = "SHA-1";
    private String getHashString(String s) {
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance(HASH_ALGORITHM);
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < messageDigest.length; i++) {
                hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    private void copyFileToClipboard(String filename) {
        if (filename == null) return;
        FileInputStream fis;
        try {
            fis = new FileInputStream(filename);
            FileChannel fc = fis.getChannel();
            try {
                ByteBuffer bbuf;
                bbuf = fc.map(FileChannel.MapMode.READ_ONLY, 0, (int) fc.size());
                // Create a read-only CharBuffer on the file
                CharBuffer cbuf = Charset.forName("UTF-8").newDecoder().decode(bbuf);
                String str = cbuf.toString();
                ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                        .getManager(getApplicationContext());
                clip.setText(str);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                alert(e.toString());
            }
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            alert(e.toString());
        }
    }

    private boolean copyClipboardToFile(String filename) {
        if (filename == null) return false;
        if (canPaste()) {
            ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                    .getManager(getApplicationContext());
            String str = clip.getText().toString();
            writeStringToFile(filename, str);
            return true;
        }
        return false;
    }

    public static void writeStringToFile(String filename, String str) {
        if (filename == null) return;
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(filename);
            FileChannel fc = fos.getChannel();
            try {
                ByteBuffer by = ByteBuffer.wrap(str.getBytes());
                fc.write(by);
                fc.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void clearClipBoard() {
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        clip.setText("");
    }

    // Called when the list of sessions changes
    public void onUpdate() {
        SessionList sessions = mTermSessions;
        if (sessions == null) {
            return;
        }

        if (sessions.size() == 0) {
            mStopServiceOnFinish = true;
            finish();
        } else if (sessions.size() < mViewFlipper.getChildCount()) {
            for (int i = 0; i < mViewFlipper.getChildCount(); ++i) {
                EmulatorView v = (EmulatorView) mViewFlipper.getChildAt(i);
                if (!sessions.contains(v.getTermSession())) {
                    v.onPause();
                    mViewFlipper.removeView(v);
                    --i;
                }
            }
        }
    }

    private boolean canPaste() {
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        if (clip.hasText() && (clip.getText().toString().length() > 0)) {
            return true;
        }
        return false;
    }

    private void doPreferences() {
        mOrientation = -1;
        startActivity(new Intent(this, TermPreferences.class));
    }

    boolean mDoResetTerminal = false;
    private void doResetTerminal() {
        doResetTerminal(false);
    }

    private void doResetTerminal(boolean keyboard) {
        doRestartSoftKeyboard();
        if (keyboard && !mHaveFullHwKeyboard) doHideSoftKeyboard();
        TermSession session = getCurrentTermSession();
        if (session != null) {
            session.reset();
            sendKeyStrings("\u001b\u000c", false);
        }
        if (keyboard && !mHaveFullHwKeyboard) doShowSoftKeyboard();
        mDoResetTerminal = false;
    }

    private void doEmailTranscript() {
        TermSession session = getCurrentTermSession();
        if (session != null) {
            // Don't really want to supply an address, but
            // currently it's required, otherwise nobody
            // wants to handle the intent.
            String addr = "user@example.com";
            Intent intent =
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"
                            + addr));

            String subject = getString(R.string.email_transcript_subject);
            String title = session.getTitle();
            if (title != null) {
                subject = subject + " - " + title;
            }
            intent.putExtra(Intent.EXTRA_SUBJECT, subject);
            intent.putExtra(Intent.EXTRA_TEXT,
                    session.getTranscriptText().trim());
            try {
                startActivity(Intent.createChooser(intent,
                        getString(R.string.email_transcript_chooser_title)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this,
                        R.string.email_transcript_no_email_activity_found,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean doShareIntentClipboard() {
        if (canPaste()) {
            ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                    .getManager(getApplicationContext());
            String str = clip.getText().toString();
            doShareIntentText(str);
            return true;
        }
        return false;
    }

    private void shareIntentTextDialog(){
        final String[] items = { this.getString(R.string.share_buffer_text), this.getString(R.string.share_visual_text), this.getString(R.string.share_unnamed_text), this.getString(R.string.share_file)};
        new AlertDialog.Builder(this)
                .setTitle(this.getString(R.string.share_title))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                sendKeyStrings(":ShareIntent\r", true);
                                break;
                            case 1:
                                sendKeyStrings(":ShareIntent!\r", true);
                                break;
                            case 2:
                                sendKeyStrings(":ShareIntent u\r", true);
                                break;
                            case 3:
                                sendKeyStrings(":ShareIntent file\r", true);
                                break;
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private boolean doShareIntentTextFile(String filename) {
        File file = new File(filename);
        if (!file.canRead()) return false;
        String str = getStringFromFile(file);
        if (str != null) doShareIntentText(str);
        return true;
    }

    private String getStringFromFile(File file) {
        if (!file.canRead()) return null;
        StringBuilder builder = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file.toString()));
            String string = reader.readLine();
            while (string != null) {
                builder.append(string).append(System.getProperty("line.separator"));
                string = reader.readLine();
            }
        } catch (Exception e) {
            return null;
        }
        return builder.toString();
    }

    private void doShareIntentText(String text) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share"));
    }

    private void doShareAll() {
        final String[] items = {this.getString(R.string.copy_screen_current), this.getString(R.string.copy_screen_buffer)};
        new AlertDialog.Builder(this)
                .setTitle(this.getString(R.string.share_screen_text))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doCopyAll(which+2);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doCopyAll() {
        final String[] items = {this.getString(R.string.copy_screen_current), this.getString(R.string.copy_screen_buffer)};
        new AlertDialog.Builder(this)
            .setTitle(this.getString(R.string.copy_screen))
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    doCopyAll(which);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void doCopyAll(int mode) {
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        String str;
        String mes;
        if (mode == 0) {
            str = getCurrentEmulatorView().getTranscriptCurrentText();
            clip.setText(str);
            mes = Term.this.getString(R.string.toast_clipboard);
        } else if (mode == 1) {
            str = getCurrentEmulatorView().getTranscriptText();
            clip.setText(str);
            mes = Term.this.getString(R.string.toast_clipboard);
        } else if (mode == 2) {
            str = getCurrentEmulatorView().getTranscriptCurrentText();
            doShareIntentText(str);
            return;
        } else if (mode == 3) {
            str = getCurrentEmulatorView().getTranscriptText();
            doShareIntentText(str);
            return;
        } else {
            return;
        }
        final Toast toastCopy = Toast.makeText(this, mes, Toast.LENGTH_LONG);
        toastCopy.setGravity(Gravity.CENTER, 0, 0);
        toastCopy.show();
    }

    private static boolean mVimPaste = false;
    private void doPaste() {
        if (mVimPaste && mSettings.getInitialCommand().matches("(.|\n)*(^|\n)-?vim\\.app(.|\n)*")) {
            sendKeyStrings("\"*p", false);
            return;
        }
        if (!canPaste()) {
            alert(Term.this.getString(R.string.toast_clipboard_error));
            return;
        }
        doWarningBeforePaste();
    }

    private void doTermPaste() {
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        if (clip == null) return;
        CharSequence paste = clip.getText();
        if (paste == null) return;
        TermSession session = getCurrentTermSession();
        if (session != null) session.write(paste.toString());
    }

    private void doWarningBeforePaste() {
        if (!mVimFlavor) {
            doTermPaste();
            return;
        }
        boolean warning = getPrefBoolean(Term.this, "do_warning_before_paste", true);
        if (!warning) {
            doTermPaste();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setTitle(R.string.clipboard_warning_title);
            builder.setMessage(R.string.clipboard_warning);
            LayoutInflater flater = LayoutInflater.from(this);
            View view = flater.inflate(R.layout.alert_checkbox, null);
            builder.setView(view);
            final CheckBox dontShowAgain = (CheckBox)view.findViewById(R.id.dont_show_again);
            dontShowAgain.setChecked(false);
            builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int m) {
                    if (dontShowAgain.isChecked()) {
                        setPrefBoolean(Term.this, "do_warning_before_paste", false);
                    }
                    doTermPaste();
                }
            });
            builder.setNegativeButton(android.R.string.no, null);
            builder.create().show();
    }

    private void doSendControlKey() {
        getCurrentEmulatorView().sendControlKey();
    }

    private void doSendFnKey() {
        getCurrentEmulatorView().sendFnKey();
    }

    private void doDocumentKeys() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        Resources r = getResources();
        dialog.setTitle(r.getString(R.string.control_key_dialog_title));
        dialog.setMessage(
            formatMessage(mSettings.getControlKeyId(), TermSettings.CONTROL_KEY_ID_NONE,
                r, R.array.control_keys_short_names,
                R.string.control_key_dialog_control_text,
                R.string.control_key_dialog_control_disabled_text, "CTRLKEY")
            + "\n\n" +
            formatMessage(mSettings.getFnKeyId(), TermSettings.FN_KEY_ID_NONE,
                r, R.array.fn_keys_short_names,
                R.string.control_key_dialog_fn_text,
                R.string.control_key_dialog_fn_disabled_text, "FNKEY"));
         dialog.show();
     }

     private String formatMessage(int keyId, int disabledKeyId,
         Resources r, int arrayId,
         int enabledId,
         int disabledId, String regex) {
         if (keyId == disabledKeyId) {
             return r.getString(disabledId);
         }
         String[] keyNames = r.getStringArray(arrayId);
         String keyName = keyNames[keyId];
         String template = r.getString(enabledId);
         String result = template.replaceAll(regex, keyName);
         return result;
    }

    private void doRestartSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager)
            getSystemService(Context.INPUT_METHOD_SERVICE);
        EmulatorView view = getCurrentEmulatorView();
        if (imm != null && view != null) imm.restartInput(view);
    }

    private void doToggleSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager)
            getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
    }

    private void doShowSoftKeyboard() {
        if (getCurrentEmulatorView() == null) return;
        Activity activity = this;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(getCurrentEmulatorView(), InputMethodManager.SHOW_FORCED);
    }

    private void doHideSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = getCurrentFocus();
        if (imm != null && view != null) imm.hideSoftInputFromWindow(view.getWindowToken(),0);
    }

    final int WAKELOCK_TIMEOUT = 30; /* minutes */
    private void doToggleWakeLock() {
    }

    private final Handler mKeepScreenHandler = new Handler();
    private boolean mKeepScreenEnableAuto = false;
    private void doToggleKeepScreen() {
        boolean keepScreen = (getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
        if (keepScreen) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mKeepScreenHandler.removeCallbacksAndMessages(null);
            final Toast toast = Toast.makeText(this, this.getString(R.string.keepscreen_deacitvated), Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            if (!mKeepScreenEnableAuto) toast.show();
        } else {
            final int timeout = mSettings.getKeepScreenTime();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            String mes = String.format(this.getString(R.string.keepscreen_notice), timeout);
            if (!mKeepScreenEnableAuto) alert(mes);
            final Toast toast = Toast.makeText(this, this.getString(R.string.keepscreen_deacitvated), Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            final Runnable r = new Runnable() {
                @Override
                public void run() {
                    mKeepScreenHandler.removeCallbacks(this);
                    boolean keepScreen = (getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
                    final long timeoutMills = mSettings.getKeepScreenTime()*60L*1000L;
                    final long currentTimeMillis = System.currentTimeMillis();
                    if (keepScreen) {
                        if (currentTimeMillis >= mLastKeyPress + timeoutMills) {
                            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            if (!mKeepScreenEnableAuto) toast.show();
                        } else {
                            mKeepScreenHandler.postDelayed(this, timeoutMills - (currentTimeMillis - mLastKeyPress));
                        }
                    }
                }
            };
            mKeepScreenHandler.removeCallbacksAndMessages(null);
            long timeoutMills = mSettings.getKeepScreenTime()*60L*1000L;
            mKeepScreenHandler.postDelayed(r, timeoutMills);
        }
    }

    private void doToggleWifiLock() {
    }

    private void doUIToggle(int x, int y, int width, int height) {
        doToggleSoftKeyboard();
        EmulatorView view = getCurrentEmulatorView();
        if (view != null) getCurrentEmulatorView().requestFocusFromTouch();
    }

    /**
     *
     * Send a URL up to Android to be handled by a browser.
     * @param link The URL to be opened.
     */
    private void execURL(String link)
    {
        Uri webLink = Uri.parse(link);
        Intent openLink = new Intent(Intent.ACTION_VIEW, webLink);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> handlers = pm.queryIntentActivities(openLink, 0);
        if(handlers.size() > 0)
            startActivity(openLink);
    }

    public void setPrefBoolean(Context context, String key, boolean value) {
        PrefValue pv = new PrefValue(context);
        pv.setBoolean(key, value);
    }

    public boolean getPrefBoolean(Context context, String key, boolean defValue) {
        PrefValue pv = new PrefValue(context);
        return pv.getBoolean(key, defValue);
    }

    private int mOnelineTextBox = -1;
    private EditText mEditText;
    private void initOnelineTextBox(int mode) {
        mEditText = (EditText) findViewById(R.id.text_input);
        mEditText.setText("");
        setEditTextView(mode);
        mEditText.setInputType(EditorInfo.TYPE_CLASS_TEXT);
        mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                    // do nothing
                } else if ((actionId == EditorInfo.IME_ACTION_DONE)
                        || (actionId == EditorInfo.IME_ACTION_SEND)
                        || (actionId == EditorInfo.IME_ACTION_UNSPECIFIED)) {
                    String str = v.getText().toString();
                    if (str.equals("")) str = "\r";
                    sendKeyStrings(str, false);
                    v.setText("");
                }
                return true;
            }
        });
        mEditText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                onLastKey();
                if (keyCode == KeycodeConstants.KEYCODE_TAB) {
                    return true;
                }
                int shortcut = EmulatorView.getPreIMEShortcutsStatus(keyCode, event);
                if (shortcut == EmulatorView.PREIME_SHORTCUT_ACTION) {
                    int action = mSettings.getImeShortcutsAction();
                    if (action == 0) {
                        doToggleSoftKeyboard();
                    } else if (action == 1261) {
                        doEditTextFocusAction();
                    } else if (action == 1361) {
                        keyEventSender(KEYEVENT_SENDER_SHIFT_SPACE);
                    } else if (action == 1362) {
                        keyEventSender(KEYEVENT_SENDER_ALT_SPACE);
                    } else {
                        int inputType = mEditText.getInputType();
                        if ((inputType & EditorInfo.TYPE_CLASS_TEXT) != 0) {
                            setEditTextInputType(action);
                        } else {
                            inputType = EditorInfo.TYPE_CLASS_TEXT;
                            mEditText.setInputType(inputType);
                        }
                    }
                    return true;
                } else if (shortcut == EmulatorView.PREIME_SHORTCUT_ACTION2) {
                    doEditTextFocusAction();
                    return true;
                }
                return false;
            }
        });
    }

    private void setEditTextInputType(int action) {
        int inputType;
        switch (action) {
            case 51:
                inputType = EditorInfo.TYPE_TEXT_VARIATION_PASSWORD;
                break;
            case 52:
                inputType = EditorInfo.TYPE_TEXT_VARIATION_URI;
                break;
            case 53:
                inputType = EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
                break;
            case 54:
                inputType = EditorInfo.TYPE_NULL;
                break;
            default:
                inputType = EditorInfo.TYPE_CLASS_TEXT;
                break;
        }
        mEditText.setInputType(inputType);
    }

    private static boolean mEditTextView = false;
    private void setEditTextView(int mode) {
        EmulatorView view = getCurrentEmulatorView();
        if (mode == 2) {
            mEditTextView = !mEditTextView;
            if (view != null) view.restartInputGoogleIme();
        } else {
            if (view != null && (mEditTextView != (mode == 1))) {
                view.restartInputGoogleIme();
            }
            mEditTextView = mode == 1;
            if (mode == 0 && mEditText != null) mEditText.setText("");
        }
        if (mAlreadyStarted) updatePrefs();
    }

    private void doEditTextFocusAction() {
        boolean focus = false;
        if (mEditText != null && mEditTextView) focus = !mEditText.hasFocus();
        if (focus) {
            mEditText.requestFocusFromTouch();
        } else setEditTextViewFocus(2);
    }

    private void setEditTextViewFocus(int mode) {
        setEditTextView(mode);
        int focus = mode;
        if (mode == 2) focus = mEditTextView ? 1 : 0;
        if (focus == 1 && mEditTextView && mEditText != null) {
            mEditText.requestFocusFromTouch();
        } else {
            EmulatorView view = getCurrentEmulatorView();
            if (view != null) view.requestFocusFromTouch();
        }
    }

    private boolean setEditTextAltCmd() {
        if (mEditTextView && mEditText != null && mEditText.isFocused()) {
            EmulatorView view = getCurrentEmulatorView();
            if (view != null) view.requestFocusFromTouch();
            return true;
        } else {
            return false;
        }
    }

    private void setEditTextVisibility() {
        final View layout = findViewById(R.id.oneline_text_box);
        int visibility = mEditTextView ? View.VISIBLE : View.GONE;
        EmulatorView view = getCurrentEmulatorView();
        if (view != null) view.restartInputGoogleIme();
        layout.setVisibility(visibility);
        if (visibility == View.VISIBLE) {
            if (!mHaveFullHwKeyboard) doShowSoftKeyboard();
            if (mEditText != null) mEditText.requestFocusFromTouch();
//            doWarningEditTextView();
        } else {
            if (view != null) view.requestFocusFromTouch();
        }
    }

    private void doWarningEditTextView() {
        if (!mVimFlavor) return;
        doWarningDialog(this.getString(R.string.edit_text_view_warning_title), this.getString(R.string.edit_text_view_warning), "do_warning_edit_text_view", true);
    }

    private static int mFunctionBar = -1;
    private void setFunctionBar(int mode) {
        if (mode == 2) {
            mFunctionBar = mFunctionBar == 0 ? 1 : 0;
            if (mHideFunctionBar) mFunctionBar = 1;
            mHideFunctionBar = false;
        }
        else mFunctionBar = mode;
        if (mAlreadyStarted) updatePrefs();
    }

    class FunctionKey {
        public String key;
        public int resid;
        public boolean defValue;
        FunctionKey(String k, int i, boolean v) {
            key = k;
            resid = i;
            defValue = v;
        }
    }

    FunctionKey[] FunctionKeys = {
        new FunctionKey("functionbar_esc",          R.id.button_esc,               true),
        new FunctionKey("functionbar_ctrl",         R.id.button_ctrl,              true),
        new FunctionKey("functionbar_alt",          R.id.button_alt,               false),
        new FunctionKey("functionbar_tab",          R.id.button_tab,               true),
        new FunctionKey("functionbar_up",           R.id.button_up,                true),
        new FunctionKey("functionbar_down",         R.id.button_down,              true),
        new FunctionKey("functionbar_left",         R.id.button_left,              false),
        new FunctionKey("functionbar_right",        R.id.button_right,             false),
        new FunctionKey("functionbar_page_up",      R.id.button_page_up,           false),
        new FunctionKey("functionbar_page_down",    R.id.button_page_down,         false),
        new FunctionKey("functionbar_backspace",    R.id.button_backspace,         false),
        new FunctionKey("functionbar_enter",        R.id.button_enter,             false),
        new FunctionKey("functionbar_i",            R.id.button_i,                 false),
        new FunctionKey("functionbar_colon",        R.id.button_colon,             true),
        new FunctionKey("functionbar_slash",        R.id.button_slash,             false),
        new FunctionKey("functionbar_equal",        R.id.button_equal,             false),
        new FunctionKey("functionbar_asterisk",     R.id.button_asterisk,          false),
        new FunctionKey("functionbar_pipe",         R.id.button_pipe,              false),
        new FunctionKey("functionbar_minus",        R.id.button_minus,             false),
        new FunctionKey("functionbar_vim_paste",    R.id.button_vim_paste,         true),
        new FunctionKey("functionbar_vim_yank",     R.id.button_vim_yank,          false),
        new FunctionKey("functionbar_softkeyboard", R.id.button_softkeyboard,      true),
        new FunctionKey("functionbar_invert",       R.id.button_invert,            true),
        new FunctionKey("functionbar_menu",         R.id.button_menu,              true),
        new FunctionKey("functionbar_menu_hide",    R.id.button_menu_hide,         true),
        new FunctionKey("functionbar_menu_plus",    R.id.button_menu_plus,         false),
        new FunctionKey("functionbar_menu_minus",   R.id.button_menu_minus,        false),
        new FunctionKey("functionbar_menu_x",       R.id.button_menu_x,            false),
        new FunctionKey("functionbar_menu_user",    R.id.button_menu_user,         true),
        new FunctionKey("functionbar_menu_quit",    R.id.button_menu_quit,         true),
        new FunctionKey("functionbar_next0",        R.id.button_next_functionbar0, true),
        new FunctionKey("functionbar_next2",        R.id.button_next_functionbar2, true),
        new FunctionKey("functionbar_prev",         R.id.button_prev_functionbar,  true),
        new FunctionKey("functionbar_prev2",        R.id.button_prev_functionbar2, true),
        new FunctionKey("functionbar_m1",           R.id.button_m1,                true),
        new FunctionKey("functionbar_m2",           R.id.button_m2,                true),
        new FunctionKey("functionbar_m3",           R.id.button_m3,                true),
        new FunctionKey("functionbar_m4",           R.id.button_m4,                true),
        new FunctionKey("functionbar_m5",           R.id.button_m5,                true),
        new FunctionKey("functionbar_m6",           R.id.button_m6,                true),
        new FunctionKey("functionbar_m7",           R.id.button_m7,                true),
        new FunctionKey("functionbar_m8",           R.id.button_m8,                true),
        new FunctionKey("functionbar_m9",           R.id.button_m9,                true),
        new FunctionKey("functionbar_m10",          R.id.button_m10,               true),
        new FunctionKey("functionbar_m11",          R.id.button_m11,               true),
        new FunctionKey("functionbar_m12",          R.id.button_m12,               true),
    };

    private void setFunctionKeyListener() {
        for (FunctionKey fkey: FunctionKeys) {
            switch (fkey.resid) {
            case R.id.button_up:
            case R.id.button_down:
            case R.id.button_left:
            case R.id.button_right:
                findViewById(fkey.resid).setOnTouchListener(new RepeatListener(400, 25, new OnClickListener() {
                    public void onClick(View v) {
                        Term.this.onClick(v);
                    }
                }));
                break;
            default:
                findViewById(fkey.resid).setOnClickListener(this);
                break;
            }
        }
        Button button = (Button)findViewById(R.id.button_oneline_text_box_clear);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onelineTextBoxClear();
            }
        });

        int visibility = (mSettings.getOneLineTextBoxCr()) ? View.VISIBLE : View.GONE;
        button = (Button) findViewById(R.id.button_oneline_text_box_enter);
        button.setVisibility(visibility);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onelineTextBoxEnter(true);
            }
        });
        final String label = "⏎";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) button.setText(label);
        button = (Button)findViewById(R.id.button_enter);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) button.setText(label);
    }

    private boolean onelineTextBoxClear(){
        if (mEditTextView) {
            if (mEditText != null) {
                String str = mEditText.getText().toString();
                EmulatorView view = getCurrentEmulatorView();
                if (mEditText.isFocused()) {
                    if (!str.equals("")) {
                        if (view != null) view.restartInputGoogleIme();
                        mEditText.setText("");
                    } else {
                        setEditTextView(0);
                    }
                } else if (str.equals("")) {
                    setEditTextView(0);
                } else {
                    mEditText.requestFocusFromTouch();
                    mEditText.setText("");
                }
                return true;
            }
        }
        return false;
    }

    private boolean onelineTextBoxEsc(){
        if (mEditTextView) {
            if (mEditText != null && mEditText.isFocused()) {
                EmulatorView view = getCurrentEmulatorView();
                if (view != null) view.requestFocusFromTouch();
                if (mSettings.getOneLineTextBoxEsc()) {
                    setEditTextView(0);
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    private boolean onelineTextBoxTab(){
        if (mEditTextView) {
            if (mEditText != null && mEditText.isFocused()) {
                sendKeyStrings("\t", false);
                return true;
            }
        }
        return false;
    }

    private boolean onelineTextBoxEnter(boolean force){
        if (mEditTextView) {
            if (mEditText != null && (force || mEditText.isFocused())) {
                String str = mEditText.getText().toString();
                EmulatorView view = getCurrentEmulatorView();
                if (view != null) {
                    view.restartInputGoogleIme();
                    if (str.equals("")) str = "\r";
                    sendKeyStrings(str, false);
                    mEditText.setText("");
                    return true;
                }
            }
        }
        return false;
    }

    public static final String FKEY_LABEL = "fkey_label";
    private void setFunctionKeyVisibility(SharedPreferences prefs, String key, int id, boolean defValue) {
        int visibility = prefs.getBoolean(key, defValue) ? View.VISIBLE : View.GONE;
        if (id == R.id.button_menu_plus)  visibility = View.GONE;
        if (id == R.id.button_menu_minus) visibility = View.GONE;
        if (id == R.id.button_menu_x)     visibility = View.GONE;
        String label = prefs.getString(FKEY_LABEL+key, "");
        setFunctionBarButton(id, visibility, label);

        Button button = (Button) findViewById(R.id.button_oneline_text_box_enter);
        visibility = (mSettings.getOneLineTextBoxCr()) ? View.VISIBLE : View.GONE;
        button.setVisibility(visibility);

        setCursorDirectionLabel();
    }

    static int mFunctionBarId = 0;
    private void setFunctionKeyVisibility() {
        int visibility;
        if (mHideFunctionBar) {
            visibility = View.GONE;
            findViewById(R.id.view_function_bar).setVisibility(visibility);
            findViewById(R.id.view_function_bar1).setVisibility(visibility);
            findViewById(R.id.view_function_bar2).setVisibility(visibility);
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        for (FunctionKey fkey: FunctionKeys) {
            setFunctionKeyVisibility(prefs, fkey.key, fkey.resid, fkey.defValue);
        }

        visibility = (mFunctionBar == 1) ? View.VISIBLE : View.GONE;
        findViewById(R.id.view_function_bar).setVisibility(visibility);
        findViewById(R.id.view_function_bar1).setVisibility(visibility);
        visibility = (mFunctionBar == 1 && mFunctionBarId == 1) ? View.VISIBLE : View.GONE;
        findViewById(R.id.view_function_bar2).setVisibility(visibility);
    }

    @SuppressLint("NewApi")
    private void setFunctionBarButton(int id, int visibility, String label) {
        Button button = (Button)findViewById(id);
        if (!label.equals("")) button.setText(label);
        button.setVisibility(visibility);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float fs = mSettings.getFontSize();
        if (fs == 0) fs = EmulatorView.getTextSize(this);
        int height = (int) Math.ceil(fs * metrics.density * metrics.scaledDensity);
        button.setMinHeight(height);
        if (AndroidCompat.SDK >= 14) {
            button.setAllCaps(false);
        }
    }

    public void onClick(View v) {
        EmulatorView view = getCurrentEmulatorView();
        switch (v.getId()) {
        case R.id.button_esc:
            if (view.getControlKeyState() != 0 || (getInvertCursorDirection() != getDefaultInvertCursorDirection())) {
                mInvertCursorDirection = getDefaultInvertCursorDirection();
                setCursorDirectionLabel();
                view.setControlKeyState(0);
                break;
            }
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_ESCAPE);
            break;
        case R.id.button_ctrl:
            int ctrl = view.getControlKeyState();
            if (ctrl == LOCKED) {
                mInvertCursorDirection = getDefaultInvertCursorDirection();
                setCursorDirectionLabel();
            } else if (mSettings.getCursorDirectionControlMode() == 3) {
                if (ctrl == RELEASED) {
                    mInvertCursorDirection = !getInvertCursorDirection();
                    setCursorDirectionLabel();
                }
            } else if (mSettings.getCursorDirectionControlMode() > 0) {
                if (ctrl == UNPRESSED) {
                    mInvertCursorDirection = !getInvertCursorDirection();
                    setCursorDirectionLabel();
                }
            }
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_CTRL_LEFT);
            break;
        case R.id.button_alt:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_ALT_LEFT);
            break;
        case R.id.button_tab:
            if (onelineTextBoxTab()) break;
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_TAB);
            break;
        case R.id.button_up:
        case R.id.button_down:
        case R.id.button_left:
        case R.id.button_right:
            int state = view.getControlKeyState();
            boolean invert = getInvertCursorDirection();
            if ((!invert && v.getId() == R.id.button_up) ||
                 (invert && v.getId() == R.id.button_left)) {
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_UP);
            } else if ((!invert  && v.getId() == R.id.button_down) ||
                        (invert  && v.getId() == R.id.button_right)) {
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_DOWN);
            } else if ((!invert  && v.getId() == R.id.button_left) ||
                        (invert  && v.getId() == R.id.button_up)) {
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_LEFT);
            } else if ((!invert  && v.getId() == R.id.button_right) ||
                        (invert  && v.getId() == R.id.button_down)) {
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_RIGHT);
            }
            if (state == RELEASED && mSettings.getCursorDirectionControlMode() == 1) {
                view.setControlKeyState(UNPRESSED);
                mInvertCursorDirection = getDefaultInvertCursorDirection();
                setCursorDirectionLabel();
            }
            break;
        case R.id.button_page_up:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_PAGE_UP);
            break;
        case R.id.button_page_down:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_PAGE_DOWN);
            break;
        case R.id.button_backspace:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_DEL);
            break;
        case R.id.button_enter:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_ENTER);
            break;
        case R.id.button_i:
            sendKeyStrings("i", false);
            if (!mHaveFullHwKeyboard) doShowSoftKeyboard();
            break;
        case R.id.button_colon:
            sendKeyStrings(":", false);
            break;
        case R.id.button_slash:
            sendKeyStrings("/", false);
            break;
        case R.id.button_equal:
            sendKeyStrings("=", false);
            break;
        case R.id.button_asterisk:
            sendKeyStrings("*", false);
            break;
        case R.id.button_pipe:
            sendKeyStrings("|", false);
            break;
        case R.id.button_minus:
            sendKeyStrings("-", false);
            break;
        case R.id.button_vim_paste:
            sendKeyStrings("\"*p", false);
            break;
        case R.id.button_vim_yank:
            sendKeyStrings("\"*yy"+"\u001b", false);
            break;
        case R.id.button_menu_plus:
            doSendActionBarKey(view, mSettings.getActionBarPlusKeyAction());
            break;
        case R.id.button_menu_minus:
            doSendActionBarKey(view, mSettings.getActionBarMinusKeyAction());
            break;
        case R.id.button_menu_x:
            doSendActionBarKey(view, mSettings.getActionBarXKeyAction());
            break;
        case R.id.button_menu_user:
            doSendActionBarKey(view, mSettings.getActionBarUserKeyAction());
            break;
        case R.id.button_menu_quit:
            doSendActionBarKey(view, mSettings.getActionBarQuitKeyAction());
            break;
        case R.id.button_softkeyboard:
            doSendActionBarKey(view, mSettings.getActionBarIconKeyAction());
            break;
        case R.id.button_invert:
            doSendActionBarKey(view, mSettings.getActionBarInvertKeyAction());
            break;
        case R.id.button_menu:
            openOptionsMenu();
            break;
        case R.id.button_menu_hide:
            setFunctionBar(2);
            break;
        case R.id.button_next_functionbar0:
        case R.id.button_prev_functionbar:
        case R.id.button_prev_functionbar2:
        case R.id.button_next_functionbar2:
            mFunctionBarId = mFunctionBarId == 0 ? 1 : 0;
            setFunctionKeyVisibility();
            break;
        case R.id.button_m1:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_F1);
            break;
        case R.id.button_m2:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_F2);
            break;
        case R.id.button_m3:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_F3);
            break;
        case R.id.button_m4:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_F4);
            break;
        case R.id.button_m5:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_F5);
            break;
        case R.id.button_m6:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_F6);
            break;
        case R.id.button_m7:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_F7);
            break;
        case R.id.button_m8:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_F8);
            break;
        case R.id.button_m9:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_F9);
            break;
        case R.id.button_m10:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_F10);
            break;
        case R.id.button_m11:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_F11);
            break;
        case R.id.button_m12:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_F12);
            break;
        }
    }

    @SuppressLint("NewApi")
    public static String handleOpenDocument(Uri uri, Cursor cursor) {
        if (uri == null || cursor == null) return null;

        String displayName = null;
        try {
            int index = -1;
            cursor.moveToFirst();
            index = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME);
            if (index != -1) displayName = cursor.getString(index);
        } catch (Exception e) {
            // do nothing
        }

        String path = null;
        if (isExternalStorageDocument(uri)) {
            path = uri.getPath();
            path = path.replaceAll(":", "/");
            path = path.replaceFirst("/document/", "/storage/");
        } else if (isInternalPrivateStorageDocument(uri)) {
            path = uri.getPath();
            path = path.replaceAll(":", "/");
            path = path.replaceFirst("/document/", "");
        } else {
            if (isDownloadDocument(uri)) {
                path = uri.toString().replaceFirst("content://[^/]+/", "/Download/");
            } else if (isGoogleDriveDocument(uri)) {
                path = uri.toString().replaceFirst("content://[^/]+/", "/GoogleDrive/");
            } else if (isGoogleDriveLegacyDocument(uri)) {
                path = uri.toString().replaceFirst("content://[^/]+/", "/GoogleDriveLegacy/");
            } else if (isGoogleSambaDocument(uri)) {
                path = Uri.decode(uri.toString()).replaceFirst("content://[^/]+/", "/");
                try {
                    path = URLDecoder.decode(path, "UTF-8");
                } catch (Exception e) {
                    // do nothing
                }
            } else if (isOneDriveDocument(uri)) {
                path = uri.toString().replaceFirst("content://[^/]+/", "/OneDrive/");
            } else if (isMediaDocument(uri)) {
                path = uri.toString().replaceFirst("content://[^/]+/", "/MediaDocument/");
            } else {
                path = uri.toString().replaceFirst("content://[^/]+/", "/");
            }
            if (path != null) {
                path = "/"+path.replaceAll("%2F", "/");
                String fname = new File(path).getName();
                if (displayName != null && !(fname == null || fname.equals(displayName))) path = path+"/"+displayName;
                path = path.replaceAll(":|\\|", "-");
                path = path.replaceAll("//+", "/");
            }
        }
        return path;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isInternalPrivateStorageDocument(Uri uri) {
        return "com.droidvim.storage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static boolean isGoogleDriveDocument(Uri uri) {
        return "com.google.android.apps.docs.storage".equals(uri.getAuthority());
    }

    public static boolean isGoogleDriveLegacyDocument(Uri uri) {
        return ("com.google.android.apps.docs.storage.legacy".equals(uri.getAuthority()));
    }

    public static boolean isGoogleSambaDocument(Uri uri) {
        return ("com.google.android.sambadocumentsprovider".equals(uri.getAuthority()));
    }

    public static boolean isOneDriveDocument(Uri uri) {
        return "com.microsoft.skydrive.content.StorageAccessProvider".equals(uri.getAuthority());
    }

    public static boolean isConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info != null) {
            return info.isConnected();
        }
        return false;
    }

    boolean existsPlayStore() {
        return false;
    }

    public void onDebugButtonClicked(View arg0) {
    }

    private boolean mOnExtraButtonClicked = false;
    // User clicked the "Upgrade to Premium" button.
    public void onExtraButtonClicked(View arg0) {
        if (!isConnected(this.getApplicationContext())) {
            alert(this.getString(R.string.network_error));
            return;
        }
    }

    public void updateUi() {
        setExtraButton();
    }

    // Enables or disables the "please wait" screen.
    void setWaitScreen(boolean set) {
        // findViewById(R.id.screen_main).setVisibility(set ? View.GONE : View.VISIBLE);
        // findViewById(R.id.screen_wait).setVisibility(set ? View.VISIBLE : View.GONE);
    }

    void complain(String message) {
        Log.e(TermDebug.LOG_TAG, "**** Error: " + message);
        alert("Error: " + message);
    }

    void alert(String message) {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setMessage(message);
        bld.setPositiveButton(android.R.string.ok, null);
        Log.d(TermDebug.LOG_TAG, "Showing alert dialog: " + message);
        bld.create().show();
    }

    private void showImageDialog(AlertDialog dialog, String mes, int id) {
        LayoutInflater inflater = getLayoutInflater();
        final View view = inflater.inflate(R.layout.image_dialog, null);
        ImageView iv = (ImageView)view.findViewById(R.id.iv_image_dialog);
        iv.setImageResource(id);
        dialog.setMessage(mes);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setView(view);
        dialog.show();
    }

    private void keyEventSender(int key) {
        mSenderKeyEvent = key;
        keyEventSenderAction.run();
    }

    static final private int KEYEVENT_SENDER_SHIFT_SPACE = -1;
    static final private int KEYEVENT_SENDER_ALT_SPACE = -2;
    static private int mSenderKeyEvent = KEYEVENT_SENDER_SHIFT_SPACE;
    static private final Runnable keyEventSenderAction = new Runnable() {
        public void run() {
            KeyEventSender sender = new KeyEventSender();
            sender.execute(mSenderKeyEvent);
        }
    };

    static private class KeyEventSender extends AsyncTask<Integer, Integer, Integer> {
      @Override
      protected Integer doInBackground(Integer... params) {
          Instrumentation inst = new Instrumentation();
          if (params[0] == KEYEVENT_SENDER_SHIFT_SPACE) {
              inst.sendKeySync(new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 1, KeyEvent.META_SHIFT_ON));
          } else if (params[0] == KEYEVENT_SENDER_ALT_SPACE) {
              inst.sendKeySync(new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 1, KeyEvent.META_ALT_ON));
          } else {
              inst.sendKeyDownUpSync(params[0]);
          }
          return null;
      }
    }
}

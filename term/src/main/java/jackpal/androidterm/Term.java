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
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.system.Os;
import android.text.TextUtils;
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
import android.view.ViewGroup;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import jackpal.androidterm.compat.AndroidCompat;
import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.emulatorview.UpdateCallback;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompat;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompatFactory;
import jackpal.androidterm.emulatorview.compat.KeycodeConstants;
import jackpal.androidterm.util.SessionList;
import jackpal.androidterm.util.TermSettings;

import static android.provider.DocumentsContract.Document;
import static android.provider.DocumentsContract.deleteDocument;
import static jackpal.androidterm.ShellTermSession.getProotCommand;
import static jackpal.androidterm.TermVimInstaller.shell;

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
    private final static int PRESSED = 1;
    private final static int RELEASED = 2;
    private final static int USED = 3;
    private final static int LOCKED = 4;

    private boolean mAlreadyStarted = false;
    private boolean mStopServiceOnFinish = false;

    private final static boolean FLAVOR_VIM = TermVimInstaller.FLAVOR_VIM;
    private final static boolean SCOPED_STORAGE = (getProotCommand().length > 0) && (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q);
    private static boolean mVimFlavor = FLAVOR_VIM;

    private Intent TSIntent;

    public static final int REQUEST_CHOOSE_WINDOW = 1;
    public static final int REQUEST_FILE_PICKER = 2;
    public static final int REQUEST_FILE_DELETE = 3;
    public static final int REQUEST_DOCUMENT_TREE = 10;
    public static final int REQUEST_COPY_DOCUMENT_TREE_TO_HOME = 11;

    public static final String EXTRA_WINDOW_ID = "jackpal.androidterm.window_id";
    private int onResumeSelectWindow = -1;
    private ComponentName mPrivateAlias;


    private boolean mBackKeyPressed;

    @SuppressLint("SdCardPath")
    private String INTENT_CACHE_DIR = "/data/data/" + BuildConfig.APPLICATION_ID + "/cache/intent";
    @SuppressLint("SdCardPath")
    private String FILE_CLIPBOARD = "/data/data/" + BuildConfig.APPLICATION_ID + "/files/.clipboard";
    @SuppressLint("SdCardPath")
    private String FILE_INTENT = "/data/data/" + BuildConfig.APPLICATION_ID + "/files/.intent";

    private static final String ACTION_PATH_BROADCAST = "jackpal.androidterm.broadcast.APPEND_TO_PATH";
    private static final String ACTION_PATH_PREPEND_BROADCAST = "jackpal.androidterm.broadcast.PREPEND_TO_PATH";
    private static final String PERMISSION_PATH_BROADCAST = "jackpal.androidterm.permission.APPEND_TO_PATH";
    private static final String PERMISSION_PATH_PREPEND_BROADCAST = "jackpal.androidterm.permission.PREPEND_TO_PATH";
    private int mPendingPathBroadcasts = 0;

    private static int mExternalAppMode = 1;
    private static String mExternalApp = "";
    private static String APP_FILER = "";
    private static final String APP_DROPBOX = "com.dropbox.android";
    private static final String APP_GOOGLEDRIVE = "com.google.android.apps.docs";
    private static final String APP_ONEDRIVE = "com.microsoft.skydrive";
    private static final Map<String, String> mAltBrowser = new LinkedHashMap<String, String>() {
        {
            put("org.mozilla.firefox_beta", "org.mozilla.firefox_beta.App");
        }

        {
            put("org.mozilla.firefox", "org.mozilla.firefox.App");
        }

        {
            put("com.amazon.cloud9", "com.amazon.cloud9.browsing.BrowserActivity");
        }
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
                return Math.abs(velocityX) > Math.abs(velocityY);
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
    private static SyncFileObserver mSyncFileObserver = null;
    private static String BASH = "bash\n";

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

        setupTheme(mSettings.getColorTheme());

        setContentView(R.layout.term_activity);
        mViewFlipper = findViewById(VIEW_FLIPPER);
        setFunctionKeyListener();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mHaveFullHwKeyboard = checkHaveFullHwKeyboard(getResources().getConfiguration());
        setSoftInputMode(mHaveFullHwKeyboard);

        if (mFunctionBar == -1) mFunctionBar = mSettings.showFunctionBar() ? 1 : 0;
        if (mFunctionBar == 1) setFunctionBar(mFunctionBar);
        if (mOnelineTextBox == -1) mOnelineTextBox = mSettings.showOnelineTextBox() ? 1 : 0;
        initOnelineTextBox(mOnelineTextBox);
        INTENT_CACHE_DIR = this.getApplicationContext().getCacheDir().toString() + "/intent";
        FILE_CLIPBOARD = TermService.getAPPFILES() + "/.clipboard";
        FILE_INTENT = TermService.getAPPFILES() + "/.intent";

        WebViewActivity.setFontSize(new PrefValue(this).getInt("mWebViewSize", 140));

        updatePrefs();
        setDrawerButtons();
        restoreSyncFileObserver(this);
        TermPreferences.setAppPickerList(this);
        mAlreadyStarted = true;
    }

    static int mTheme = -1;
    private void setupTheme(int theme) {
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
        mTheme = theme;
    }

    public static void showToast(final Toast toast) {
        if (AndroidCompat.SDK >= Build.VERSION_CODES.O && mTheme == 2) {
            View v = toast.getView();
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup)v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    View c = g.getChildAt(i);
                    if (c instanceof TextView) {
                        ((TextView) c).setTextColor(Color.DKGRAY);
                        ((TextView) c).setBackgroundColor(Color.argb(60, 255, 255, 255));
                    }
                }
            }
        }
        toast.show();
    }

    public static File getScratchCacheDir(Activity activity) {
        int sdcard = TermService.getSDCard(activity.getApplicationContext());
        String cacheDir = TermService.getCacheDir(activity.getApplicationContext(), sdcard);
        return new File(cacheDir + "/scratch");
    }

    private static final String mSyncFileObserverFile = "SyncFileObserver.json";

    static SyncFileObserver restoreSyncFileObserver(Activity activity) {
        if (!FLAVOR_VIM) return null;
        saveSyncFileObserver();
        File dir = getScratchCacheDir(activity);
        mSyncFileObserver = new SyncFileObserver(dir.getAbsolutePath());
        File sfofile = new File(dir.getAbsolutePath() + "/" + mSyncFileObserverFile);
        mSyncFileObserver.restoreHashMap(sfofile);
        mSyncFileObserver.setActivity(activity);
        mSyncFileObserver.startWatching();
        return mSyncFileObserver;
    }

    static private void saveSyncFileObserver() {
        if (!FLAVOR_VIM) return;
        if (mSyncFileObserver == null) return;
        mSyncFileObserver.stopWatching();
        String dir = mSyncFileObserver.getObserverDir();
        File sfofile = new File(dir + "/" + mSyncFileObserverFile);
        mSyncFileObserver.saveHashMap(sfofile);
    }

    private void setExtraButton() {
        Button button = findViewById(R.id.drawer_extra_button);
        int visibilty = View.VISIBLE;
        if (!FLAVOR_VIM) visibilty = View.GONE;
        button.setVisibility(visibilty);
    }

    private void setDebugButton() {
        if (!BuildConfig.DEBUG) return;
        Button button = findViewById(R.id.drawer_debug_button);
        button.setVisibility(View.VISIBLE);

        if (mSettings.getColorTheme() % 2 == 0)
            button.setBackgroundResource(R.drawable.extra_button_dark);
    }

    ArrayList<String> mFilePickerItems;

    private void setDrawerButtons() {
        if (FLAVOR_VIM) {
            int visiblity = mSettings.getExternalAppButtonMode() > 0 ? View.VISIBLE : View.GONE;
            if (AndroidCompat.SDK < Build.VERSION_CODES.KITKAT) visiblity = View.GONE;
            Button button = findViewById(R.id.drawer_app_button);
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
            mFilePickerItems = new ArrayList<>();
            mFilePickerItems.add(this.getString(R.string.create_file));
            APP_FILER = getAppFiler();
            if (isAppInstalled(APP_FILER)) {
                mFilePickerItems.add(String.format(launchApp, this.getString(R.string.app_files)));
            } else {
                mFilePickerItems.add(this.getString(R.string.delete_file));
            }
            if (isAppInstalled(APP_FILER)) {
                visiblity = mSettings.getUseFilesAppButton() ? View.VISIBLE : View.GONE;
                button = findViewById(R.id.drawer_files_button);
                button.setVisibility(visiblity);
            }
            if (isAppInstalled(APP_DROPBOX)) {
                visiblity = mSettings.getDropboxFilePicker() > 0 ? View.VISIBLE : View.GONE;
                button = findViewById(R.id.drawer_dropbox_button);
                button.setVisibility(visiblity);
                mFilePickerItems.add(String.format(launchApp, this.getString(R.string.dropbox)));
            }
            if (isAppInstalled(APP_GOOGLEDRIVE)) {
                visiblity = mSettings.getGoogleDriveFilePicker() > 0 ? View.VISIBLE : View.GONE;
                button = findViewById(R.id.drawer_googledrive_button);
                button.setVisibility(visiblity);
                mFilePickerItems.add(String.format(launchApp, this.getString(R.string.googledrive)));
            }
            if (isAppInstalled(APP_ONEDRIVE)) {
                visiblity = mSettings.getOneDriveFilePicker() > 0 ? View.VISIBLE : View.GONE;
                button = findViewById(R.id.drawer_onedrive_button);
                button.setVisibility(visiblity);
                mFilePickerItems.add(String.format(launchApp, this.getString(R.string.onedrive)));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                button = findViewById(R.id.drawer_storage_button);
                button.setVisibility(View.VISIBLE);
                button = findViewById(R.id.drawer_createfile_button);
                button.setVisibility(View.VISIBLE);
                mFilePickerItems.add(this.getString(R.string.clear_cache));
            } else {
                button = findViewById(R.id.drawer_clear_cache_button);
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

        findViewById(R.id.drawer_files_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                final Runnable runFiler = new Runnable() {
                    public void run() {
                        launchExternalApp(2, APP_FILER);
                    }
                };
                doWarningDialogRun(null, getString(R.string.google_filer_warning_message), "google_file_chooser", false, runFiler);
            }
        });
        findViewById(R.id.drawer_dropbox_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (launchExternalApp(mSettings.getDropboxFilePicker(), APP_DROPBOX))
                    getDrawer().closeDrawers();
            }
        });
        findViewById(R.id.drawer_googledrive_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (launchExternalApp(mSettings.getGoogleDriveFilePicker(), APP_GOOGLEDRIVE))
                    getDrawer().closeDrawers();
            }
        });
        findViewById(R.id.drawer_onedrive_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                if (launchExternalApp(mSettings.getOneDriveFilePicker(), APP_ONEDRIVE))
                    getDrawer().closeDrawers();
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
                storageMenu();
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
        if (mSettings.getColorTheme() % 2 == 0) {
            findViewById(R.id.drawer_files_button).setBackgroundResource(R.drawable.sidebar_button2_dark);
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

    static public String getAppFilerPackageName() {
        return APP_FILER;
    }

    private String getAppFiler() {
        String[] appFilers = {
                "com.android.documentsui",
                "com.google.android.documentsui",
                ""};
        String app = appFilers[0];
        for (String pname: appFilers) {
            if (isAppInstalled(pname)) {
                app = pname;
                break;
            }
        }
        return app;
    }

    private boolean isAppInstalled(String appPackage) {
        PackageManager packageManager = getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(appPackage);
        return (intent != null);
    }

    private boolean intentMainActivity(String app) {
        if (app == null || app.equals("")) return false;
        try {
            PackageManager pm = this.getApplicationContext().getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(app);
            if (intent == null) throw new NullPointerException();
            startActivity(intent);
        } catch (Exception e) {
            alert(app + "\n" + this.getString(R.string.external_app_activity_error));
            return true;
        }
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
            } else if (mode == 1) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setPackage(appId);
                intent.setType("*/*");
                doStartActivityForResult(intent, REQUEST_FILE_PICKER);
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

    public static final int REQUEST_STORAGE = 10000;
    public static final int REQUEST_STORAGE_DELETE = 10001;
    public static final int REQUEST_STORAGE_CREATE = 10002;
    public static final int REQUEST_FOREGROUND_SERVICE_PERMISSION = 10003;

    @SuppressLint("NewApi")
    void permissionCheckExternalStorage() {
        if (SCOPED_STORAGE) return;
        if (AndroidCompat.SDK < android.os.Build.VERSION_CODES.N) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    @Override
    @SuppressLint("NewApi")
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_STORAGE:
                for (int i = 0; i < permissions.length; i++) {
                    if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!SCOPED_STORAGE) {
                                        AlertDialog.Builder bld = new AlertDialog.Builder(Term.this);
                                        bld.setIcon(android.R.drawable.ic_dialog_alert);
                                        bld.setMessage(R.string.storage_permission_granted);
                                        bld.setPositiveButton(R.string.quit, new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                dialog.dismiss();
                                            }
                                        });
                                        AlertDialog dialog = bld.create();
                                        dialog.show();
                                    }
                                }
                            });
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
                for (int i = 0; i < permissions.length; i++) {
                    if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                                final Toast toast = Toast.makeText(this, this.getString(R.string.storage_permission_error), Toast.LENGTH_LONG);
                                toast.setGravity(Gravity.CENTER, 0, 0);
                                showToast(toast);
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
                for (int i = 0; i < permissions.length; i++) {
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

        return path.substring(0, path.length() - 1);
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
                return bindTermServiceLoop(service, conn, flags, loop + 1);
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
                    final Toast toast = Toast.makeText(this, "Failed to start terminal session", Toast.LENGTH_LONG);
                    showToast(toast);
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

    private void destroyAppWarning() {
        String key = "scoped_storage_warning_backup";
        String title = this.getString(R.string.scoped_storage_warning_title);
        String message = this.getString(R.string.scoped_storage_uninstall_warning_message);
        message += "\n - " + TermService.getAPPBASE();
        message += "\n - " + TermService.getAPPEXTFILES();
        boolean first = TermVimInstaller.ScopedStorageWarning;
        TermVimInstaller.ScopedStorageWarning = false;

        boolean warning = getPrefBoolean(Term.this, key, true);
        if (!first && (!warning || mRandom.nextInt(5) != 1)) {
            doExitShell();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setTitle(title);
        builder.setMessage(message);
        if (!first) {
            LayoutInflater flater = LayoutInflater.from(this);
            View view = flater.inflate(R.layout.alert_checkbox, null);
            builder.setView(view);
            final CheckBox cb = view.findViewById(R.id.dont_show_again);
            final String warningKey = key;
            cb.setChecked(false);
            builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int m) {
                    if (cb.isChecked()) {
                        setPrefBoolean(Term.this, warningKey, false);
                    }
                    doExitShell();
                }
            });
        } else {
            builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int m) {
                    doExitShell();
                }
            });
        }
        if (isAppInstalled(APP_FILER)) {
            builder.setNeutralButton(getString(R.string.app_files), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int m) {
                    intentMainActivity(APP_FILER);
                    doExitShell();
                }
            });
        }
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positive.requestFocus();
    }

    @Override
    public void onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);

        if (mStopServiceOnFinish) {
            stopService(TSIntent);
            mFirstInputtype = true;
            mFunctionBar = -1;
            mOrientation = -1;
            if (FLAVOR_VIM && mSyncFileObserver != null) {
                mSyncFileObserver.clearOldCache();
                saveSyncFileObserver();
            }
            mKeepScreenHandler.removeCallbacksAndMessages(null);
        }
        new File(FILE_CLIPBOARD).delete();
        File cacheDir = new File(INTENT_CACHE_DIR);
        shell("rm -rf " + cacheDir.getAbsolutePath());
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

            final DrawerLayout layout = findViewById(R.id.drawer_layout);
            final ProgressBar progressBar = new ProgressBar(this.getApplicationContext(), null, android.R.attr.progressBarStyleLarge);
//            showProgressRing(layout, progressBar);

            TermVimInstaller.installVim(Term.this, new Runnable() {
                @Override
                public void run() {
                    final String bash = (AndroidCompat.SDK >= Build.VERSION_CODES.LOLLIPOP &&
                            new File(TermService.getAPPFILES() + "/usr/bin/bash").canExecute())
                            ? BASH : "";
                    if (vimApp) {
                        if (getCurrentTermSession() != null) {
                            sendKeyStrings(bash + "vim.app\n", false);
                        } else {
                            ShellTermSession.setPostCmd(bash + "vim.app\n");
                        }
                    }
//                    dismissProgressRing(layout, progressBar);
                    permissionCheckExternalStorage();
                }
            });
        } else {
            final String bash = (AndroidCompat.SDK >= Build.VERSION_CODES.LOLLIPOP &&
                    new File(TermService.getAPPFILES() + "/usr/bin/bash").canExecute())
                    ? BASH : "";
            cmd = cmd.replaceAll("\n(-?vim.app)", "\n" + bash + "$1");
            permissionCheckExternalStorage();
        }
        mFirst = false;
        return cmd;
    }

    private void showProgressRing(final DrawerLayout layout, final ProgressBar progressBar) {
        if (layout == null || progressBar == null) return;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 0);
        progressBar.setLayoutParams(params);
        layout.addView(progressBar);
        progressBar.setVisibility(View.VISIBLE);
    }

    private void dismissProgressRing(final DrawerLayout layout, final ProgressBar progressBar) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (layout != null) layout.removeView(progressBar);
                } catch (Exception e) {
                    // Do nothing
                }
            }
        });
    }

    private static Random mRandom = new Random();

    private void showVimTips() {
        if (!mVimFlavor) return;
        if (true) return;

        String title = this.getString(R.string.tips_vim_title);
        String key = "do_warning_vim_tips";
        String[] list = this.getString(R.string.tips_vim_list).split("\\|");
        int index = mRandom.nextInt(list.length - 1) + 1;
        String message = list[index];
        doWarningDialog(title, message, key, false);
    }

    private void doWarningDialog(String title, String message, String key, boolean dontShowAgain) {
        doWarningDialogRun(title, message, key, dontShowAgain, null);
    }

    private void doWarningDialogRun(String title, String message, String key, boolean dontShowAgain, final Runnable whenDone) {
        boolean warning = getPrefBoolean(Term.this, key, true);
        if (!warning) {
            if (whenDone != null) whenDone.run();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        if (title != null) builder.setTitle(title);
        if (message != null) builder.setMessage(message);
        LayoutInflater flater = LayoutInflater.from(this);
        View view = flater.inflate(R.layout.alert_checkbox, null);
        builder.setView(view);
        final CheckBox cb = view.findViewById(R.id.dont_show_again);
        final String warningKey = key;
        cb.setChecked(dontShowAgain);
        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int m) {
                if (cb.isChecked()) {
                    setPrefBoolean(Term.this, warningKey, false);
                }
                if (whenDone != null) whenDone.run();
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
        if (mViewFlipper == null) return null;
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
        EmulatorView.setForceFlush(mSettings.getForceFlushDrawText());
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
        final Toast toast = Toast.makeText(this, R.string.reset_toast_notification, Toast.LENGTH_LONG);
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
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            showToast(toast);
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
        try {
            unregisterReceiver(mBroadcastReceiever);
        } catch (Exception e) {
            e.printStackTrace();
        }

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
            restoreSyncFileObserver(this);
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
        boolean haveFullHwKeyboard = (c.keyboard == Configuration.KEYBOARD_QWERTY) &&
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
        // menu.removeItem(R.id.menu_reset);
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
        if (!FLAVOR_VIM || (AndroidCompat.SDK < Build.VERSION_CODES.KITKAT))
            menu.removeItem(R.id.menu_drawer);
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
            final Toast toast = Toast.makeText(this, R.string.reset_toast_notification, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            showToast(toast);
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
            chooseEditVimFiles();
        } else if (id == R.id.menu_text_box) {
            setEditTextView(2);
        } else if (id == R.id.menu_drawer) {
            drawerMenu();
        } else if (id == R.id.menu_reload) {
            fileReload();
        } else if (id == R.id.action_help) {
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

    private void chooseEditVimFiles() {
        sendKeyStrings(":call ATETermVimVimrc()\r", true);
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
            toggleVimIminsert();
            return true;
        } else if (key == 1261) {
            doEditTextFocusAction();
        } else if (key == 1360 || (key >= 1351 && key <= 1354)) {
            if (setEditTextAltCmd()) return true;
            view.doImeShortcutsAction(key - 1300);
            if (key == 1360) toggleVimIminsert();
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
        } else if (key == 1365) {
            sendVimIminsertKey();
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

    private boolean mUseIminsert = false;
    void toggleVimIminsert() {
        if (!FLAVOR_VIM || !mUseIminsert) return;
        sendVimIminsertKey();
    }

    void sendVimIminsertKey() {
        EmulatorView view = getCurrentEmulatorView();
        if (view == null) return;
        // send <C-^>
        TermSession session = getCurrentTermSession();
        if (session != null) session.write(30);
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
            ((Button) findViewById(R.id.button_right)).setText("▶");
            ((Button) findViewById(R.id.button_left)).setText("◀");
            ((Button) findViewById(R.id.button_up)).setText("▲");
            ((Button) findViewById(R.id.button_down)).setText("▼");
            ((Button) findViewById(R.id.button_navigation_right)).setText("▶");
            ((Button) findViewById(R.id.button_navigation_left)).setText("◀");
            ((Button) findViewById(R.id.button_navigation_up)).setText("▲");
            ((Button) findViewById(R.id.button_navigation_down)).setText("▼");
        } else {
            ((Button) findViewById(R.id.button_right)).setText("▼");
            ((Button) findViewById(R.id.button_left)).setText("▲");
            ((Button) findViewById(R.id.button_up)).setText("◀");
            ((Button) findViewById(R.id.button_down)).setText("▶");
            ((Button) findViewById(R.id.button_navigation_right)).setText("▼");
            ((Button) findViewById(R.id.button_navigation_left)).setText("▲");
            ((Button) findViewById(R.id.button_navigation_up)).setText("◀");
            ((Button) findViewById(R.id.button_navigation_down)).setText("▶");
        }
    }

    private void toggleDrawer() {
        DrawerLayout mDrawer = findViewById(R.id.drawer_layout);
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else {
            mDrawer.openDrawer(GravityCompat.START);
        }
    }

    public void openDrawer() {
        DrawerLayout mDrawer = findViewById(R.id.drawer_layout);
        if (!mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.openDrawer(GravityCompat.START);
        }
    }

    private boolean sendKeyStrings(String str, boolean esc) {
        TermSession session = getCurrentTermSession();
        if (session == null) return false;
        if (esc) str = "\u001b" + str;
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
            mViewFlipper.setDisplayedChild(mViewFlipper.getChildCount() - 1);
            doWarningDialog(null, this.getString(R.string.switch_windows_warning), "switch_window", false);
        } catch (IOException e) {
            final Toast toast = Toast.makeText(this, "Failed to create a session", Toast.LENGTH_SHORT);
            showToast(toast);
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

    private void doExitShell() {
        if (mTermSessions.size() == 1 && !mHaveFullHwKeyboard) {
            doHideSoftKeyboard();
        }
        if (mUninstall) {
            doUninstallExtraContents();
        }
        doCloseWindow();
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

    private void filePicker() {
        final String mruCommand = mSettings.getMRUCommand();
        final LinkedList<SyncFileObserverMru> list = mSyncFileObserver.getMRU();
        final Runnable runFiler = new Runnable() {
            public void run() {
                doFilePicker();
            }
        };
        if (mruCommand.equals("") || list == null || list.size() == 0) {
            doWarningDialogRun(null, getString(R.string.google_filer_warning_message), "google_storage_filer", false, runFiler);
            return;
        }
        String mru = mruCommand.equals("MRU") ? this.getString(R.string.use_mru_cache) : this.getString(R.string.use_mru);
        final String[] items = {
                this.getString(R.string.use_file_chooser),
                mru};
        new AlertDialog.Builder(this).setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String item = items[which];
                if (item.equals(Term.this.getString(R.string.use_file_chooser))) {
                    doWarningDialogRun(null, getString(R.string.google_filer_warning_message), "google_storage_filer", false, runFiler);
                } else if (item.equals(Term.this.getString(R.string.use_mru))) {
                    sendKeyStrings(mruCommand+"\r", true);
                } else if (item.equals(Term.this.getString(R.string.use_mru_cache))) {
                    chooseMruCache();
                }
            }
        }).setNegativeButton(android.R.string.cancel, null)
                .setTitle(getString(R.string.external_storage))
                .show();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void doFilePicker() {
        if (SCOPED_STORAGE) {
            intentFilePicker();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_FILE_PICKER);
        } else {
            intentFilePicker();
        }
    }

    private void chooseMruCache() {
        final LinkedList<SyncFileObserverMru> list = mSyncFileObserver.getMRU();
        int MRU_FILES = 5;
        if (MRU_FILES > list.size()) MRU_FILES = list.size();
        final String[] items = new String[MRU_FILES];
        for (int i = 0; i < MRU_FILES; i++) {
            String item = list.get(i).getPath();
            item = new File(item).getName();
            items[i] = item;
        }
        new AlertDialog.Builder(this).setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String item = items[which];
                if (item == null) {
                    // do nothing
                } else {
                    String intentCommand = mSettings.getIntentCommand();
                    if (!intentCommand.matches("^:.*")) intentCommand = ":e ";
                    String path = list.get(which).getPath();
                    path = path.replaceAll(SHELL_ESCAPE, "\\\\$1");
                    path = intentCommand + " " + path + "\r";
                    sendKeyStrings(path, true);
                }
            }
        }).setNegativeButton(android.R.string.cancel, null)
        .setTitle(getString(R.string.use_mru_cache))
        .show();
    }

    private void documentTreePicker(int requestCode) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            doStartActivityForResult(intent, requestCode);
        }
    }

    private void doStartActivityForResult(Intent intent, int requestCode) {
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        intent.putExtra("android.content.extra.FANCY", true);
        intent.putExtra("android.content.extra.SHOW_FILESIZE", true);
        startActivityForResult(intent, requestCode);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void intentFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        if (checkImplicitIntent(this, intent))
            doStartActivityForResult(intent, REQUEST_FILE_PICKER);
    }

    private void storageMenu() {
        final String[] items = new String[mFilePickerItems.size()];
        for (int i = 0; i < mFilePickerItems.size(); i++) {
            items[i] = mFilePickerItems.get(i);
        }
        new AlertDialog.Builder(this).setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String launchApp = Term.this.getString(R.string.launch_app);
                String item = items[which];
                if (item == null) {
                    // do nothing
                } else if (item.equals(String.format(launchApp, Term.this.getString(R.string.app_files)))) {
                    intentMainActivity(APP_FILER);
                } else if (Term.this.getString(R.string.create_file).equals(item)) {
                    fileCreate();
                } else if (Term.this.getString(R.string.delete_file).equals(item)) {
                    fileDelete();
                } else if (item.equals(String.format(launchApp, Term.this.getString(R.string.dropbox)))) {
                    intentMainActivity(APP_DROPBOX);
                } else if (item.equals(String.format(launchApp, Term.this.getString(R.string.googledrive)))) {
                    intentMainActivity(APP_GOOGLEDRIVE);
                } else if (item.equals(String.format(launchApp, Term.this.getString(R.string.onedrive)))) {
                    intentMainActivity(APP_ONEDRIVE);
                } else if (Term.this.getString(R.string.clear_cache).equals(item)) {
                    confirmClearCache();
                }
            }
        }).setNegativeButton(android.R.string.cancel, null)
                .setTitle(getString(R.string.storage_menu))
                .show();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void fileDelete() {
        if (SCOPED_STORAGE) {
            doFileDelete();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
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
        if (checkImplicitIntent(this, intent))
            doStartActivityForResult(intent, REQUEST_FILE_DELETE);
    }

    private void confirmDelete(final Uri uri) {
        String path = UriToPath.getPath(this, uri);
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
        if (SCOPED_STORAGE) {
            doFileCreate();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
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
        if (checkImplicitIntent(this, intent))
            doStartActivityForResult(intent, REQUEST_FILE_PICKER);
    }

    private void fileReload() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setTitle(this.getString(R.string.reload_file_title));

        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout_encoding = inflater.inflate(R.layout.force_encoding, null);
        final AutoCompleteTextView textView = layout_encoding.findViewById((R.id.autocomplete_encoding));

        ArrayAdapter<String> ac_adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, IconvHelper.encodings);
        textView.setAdapter(ac_adapter);
        textView.setThreshold(1);

        ArrayAdapter<String> sp_adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, IconvHelper.encodings);
        sp_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner sp_encoding = layout_encoding.findViewById(R.id.spinner_encoding);
        sp_encoding.setAdapter(sp_adapter);

        builder.setView(layout_encoding);
        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int m) {
                String encoding = textView.getText().toString();
                String cmd = ":e!";
                if (!encoding.equals("")) cmd += " ++enc=" + encoding;
                sendKeyStrings(cmd + "\r", true);
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

    public final static String SHELL_ESCAPE = "([ *?\\[{`$&%#'\"|!<;])";

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        switch (request) {
            case REQUEST_DOCUMENT_TREE:
                if (result == RESULT_OK && data != null) {
                }
                break;
            case REQUEST_COPY_DOCUMENT_TREE_TO_HOME:
                if (result == RESULT_OK && data != null) {
                }
                break;
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
                    path = UriToPath.getPath(this, uri);
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
                                        alert(fname + "\n" + this.getString(R.string.storage_read_error));
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.d("FilePicker", e.toString());
                            alert(this.getString(R.string.storage_read_error) + "\n" + e.toString());
                            break;
                        }
                    }
                }
                if (path != null) {
                    String intentCommand = mSettings.getIntentCommand();
                    if (!intentCommand.matches("^:.*")) intentCommand = ":e ";
                    path = path.replaceAll(SHELL_ESCAPE, "\\\\$1");
                    path = intentCommand + " " + path + "\r";
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
                    cmd += "\u001b" + RemoteInterface.IntentCommand + "\n";
                }
                if (RemoteInterface.ShareText != null) {
                    String filename = FILE_CLIPBOARD;
                    Term.writeStringToFile(filename, "\n" + RemoteInterface.ShareText.toString());
                    cmd += "\u001b" + ":ATEMod _paste\n";
                }
                final String riCmd = cmd;
                TermVimInstaller.doInstallVim(Term.this, new Runnable() {
                    @Override
                    public void run() {
                        String bash = (AndroidCompat.SDK >= Build.VERSION_CODES.LOLLIPOP &&
                                new File(TermService.getAPPFILES() + "/usr/bin/bash").canExecute())
                                ? BASH : "";
                        String cmd = bash + riCmd;
                        if (getCurrentTermSession() != null) {
                            sendKeyStrings(cmd, false);
                        } else {
                            ShellTermSession.setPostCmd(cmd);
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
                if (SCOPED_STORAGE) {
                    destroyAppWarning();
                } else {
                    doExitShell();
                }
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
                DrawerLayout drawer = findViewById(R.id.drawer_layout);
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
                copyFileToClipboard(FILE_CLIPBOARD);
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
                copyClipboardToFile(FILE_CLIPBOARD);
                if (keyCode == 0xffff0333) sendKeyStrings(":ATEMod _paste\r", true);
                return true;
            case 0xffff1006:
            case 0xffff1007:
                mVimPaste = keyCode == 0xffff1006;
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
            case 0xffff0063:
            case 0xffff1365:
                sendVimIminsertKey();
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
                AndroidIntent(FILE_INTENT);
                return true;
            case 0xffff9998:
                try {
                    ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                            .getManager(getApplicationContext());
                    String str = getCurrentEmulatorView().getTranscriptCurrentText();
                    clip.setText(str);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                fatalCrashVim();
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
        return super.onKeyUp(keyCode, event);
    }

    static private boolean mUninstall = false;

    static public void setUninstallExtraContents(boolean uninstall) {
        mUninstall = uninstall;
    }

    private void doUninstallExtraContents() {
        shell("rm -rf " + TermService.getAPPFILES() + "/usr");
        shell("rm " + TermService.getAPPFILES() + "/bin/vim");
        shell("rm " + TermService.getAPPFILES() + "/bin/vim.default");
        shell("rm " + TermService.getAPPFILES() + "/bin/vim.python");
        shell("rm -rf " + TermService.getAPPEXTFILES() + "/runtime/pack/shiftrot/start");
        shell("rm " + TermService.getAPPEXTFILES() + "/version");
        shell("rm " + TermService.getAPPEXTFILES() + "/version.*");
        mUninstall = false;
    }

    private int mLibrary = -1;
    private int mLdLibraryPathMode = -1;
    private int mVimPythonMode = -1;
    private int mChecked = -1;

    private void fatalCrashVim() {
        try {
            setUninstallExtraContents(false);
            mUninstall = false;
            mLibrary = -1;
            mLdLibraryPathMode = -1;
            mVimPythonMode = -1;
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            bld.setIcon(android.R.drawable.ic_dialog_alert);
            bld.setTitle(getString(R.string.crash_title));
            bld.setMessage(getString(R.string.crash_message));
            bld.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int m) {
                    dialog.dismiss();
                    fatalCrashVimQuit();
                }
            });
            bld.setNeutralButton(getString(R.string.crash_trouble_shooting_button), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int m) {
                    dialog.dismiss();
                    troubleShooting(true);
                }
            });
            AlertDialog dlg = bld.create();
            dlg.setCancelable(false);
            dlg.setCanceledOnTouchOutside(false);
            dlg.show();
        } catch (Exception e) {
            if (new File(TermService.getAPPFILES() + "/bin/vim.default").canExecute()) {
                sendKeyStrings("vim.app.default\r", false);
            } else {
                sendKeyStrings("vim.app\r", false);
            }
        }
    }

    private void fatalCrashVimQuit() {
        mFatalTroubleShooting = true;
        setUninstallExtraContents(false);
        final String[] items = {
                this.getString(R.string.launch_default_vim),
                this.getString(R.string.revert_to_default_vim),
                this.getString(R.string.crash_quit_button)};
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(this.getString(R.string.title_choose))
                .setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (getString(R.string.launch_default_vim).equals(items[which])) {
                            if (new File(TermService.getAPPFILES() + "/bin/vim.default").canExecute()) {
                                sendKeyStrings("vim.app.default\r", false);
                            } else {
                                sendKeyStrings("vim.app\r", false);
                            }
                        } else if (getString(R.string.revert_to_default_vim).equals(items[which])) {
                            setUninstallExtraContents(true);
                            doCloseCrashVimWindow(getString(R.string.revert_to_default_vim));
                        } else if (getString(R.string.crash_quit_button).equals(items[which])) {
                            doCloseCrashVimWindow();
                        } else {
                            fatalCrashVim();
                        }
                    }
                })
                .setPositiveButton(getString(R.string.quit_to_shell), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int m) {
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int m) {
                        dialog.dismiss();
                        fatalCrashVim();
                    }
                })
                .create();
        dlg.setCancelable(false);
        dlg.setCanceledOnTouchOutside(false);
        dlg.show();
    }

    private boolean mFatalTroubleShooting = false;

    private void troubleShooting(boolean fatal) {
        mFatalTroubleShooting = fatal;
        final String[] items = {
                this.getString(R.string.choose_vim_python_script),
                this.getString(R.string.choose_ld_library_path_mode),
                this.getString(R.string.title_change_lib_preference)};
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(this.getString(R.string.crash_trouble_shooting_button))
                .setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (which == 0) {
                            chooseVimPython();
                        } else if (which == 1) {
                            chooseLdLibraryMode();
                        } else if (which == 2) {
                            forceLibrary();
                        }
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        String message = getString(R.string.confirm_do_close_troubleshooting) + getString(R.string.confirm_change_lib);
                        doCloseCrashVimWindow(message);
                    }
                })
                .setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int m) {
                        dialog.dismiss();
                        if (mFatalTroubleShooting) {
                            fatalCrashVim();
                        } else {
                            mUninstall = false;
                            mLibrary = -1;
                            mLdLibraryPathMode = -1;
                            mVimPythonMode = -1;
                        }
                    }
                })
                .create();
        dlg.setCancelable(false);
        dlg.setCanceledOnTouchOutside(false);
        dlg.show();
    }

    private void chooseVimPython() {
        final String[] items = {
                this.getString(R.string.vim_python_script_default),
                this.getString(R.string.vim_python_script_alt1)};
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mChecked = prefs.getInt("VIM_PYTHON_MODE", 0);
        if (mVimPythonMode != -1) mChecked = mVimPythonMode;
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(this.getString(R.string.choose_vim_python_script))
                .setSingleChoiceItems(items, mChecked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mChecked = which;
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mVimPythonMode = mChecked;
                        troubleShooting(mFatalTroubleShooting);
                    }
                })
                .setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int m) {
                        dialog.dismiss();
                        troubleShooting(mFatalTroubleShooting);
                    }
                })
                .create();
        dlg.setCancelable(false);
        dlg.setCanceledOnTouchOutside(false);
        dlg.show();
    }

    private void chooseLdLibraryMode() {
        final String[] items = {
                this.getString(R.string.ld_library_path_mode_default),
                this.getString(R.string.ld_library_path_mode_alt_1)};
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mChecked = prefs.getInt("FATAL_CRASH_RESOLVER", 0);
        if (mLdLibraryPathMode != -1) mChecked = mLdLibraryPathMode;
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(this.getString(R.string.choose_ld_library_path_mode))
                .setSingleChoiceItems(items, mChecked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mChecked = which;
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mLdLibraryPathMode = mChecked;
                        troubleShooting(mFatalTroubleShooting);
                    }
                })
                .setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int m) {
                        dialog.dismiss();
                        troubleShooting(mFatalTroubleShooting);
                    }
                })
                .create();
        dlg.setCancelable(false);
        dlg.setCanceledOnTouchOutside(false);
        dlg.show();
    }

    static String getArch() {
        return TermService.getArch();
    }

    private void forceLibrary() {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_alert);
        bld.setTitle(R.string.title_change_lib_preference);
        String message = this.getString(R.string.current_library) + " " + getArch();
        message = message + "\n" + this.getString(R.string.message_change_lib_preference);
        bld.setMessage(message);
        bld.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                chooseLibrary();
            }
        });
        bld.setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                troubleShooting(mFatalTroubleShooting);
            }
        });
        bld.create().show();
    }

    void chooseLibrary() {
        String[] items = {
                this.getString(R.string.force_64bit),
                this.getString(R.string.force_32bit),
                this.getString(R.string.reset_to_default)
        };

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_change_lib_preference))
                .setSingleChoiceItems(items, mLibrary, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        confirmChangeLib(which);
                    }
                })
                .setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        troubleShooting(mFatalTroubleShooting);
                    }
                })
                .show();
    }

    void confirmChangeLib(final int which) {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_alert);
        int messageId = R.string.reset_to_default;
        if (which < 2) messageId = which == 0 ? R.string.force_64bit : R.string.force_32bit;
        bld.setTitle(messageId);
        String message = this.getString(R.string.current_library) + " " + getArch() + "\n" + this.getString(R.string.confirm_change_lib);
        bld.setMessage(message);
        bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                mUninstall = true;
                mLibrary = which;
                troubleShooting(mFatalTroubleShooting);
            }
        });
        bld.setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                troubleShooting(mFatalTroubleShooting);
            }
        });
        bld.show();
    }

    private void doCloseCrashVimWindow() {
        doCloseCrashVimWindow(null);
    }

    private void doCloseCrashVimWindow(CharSequence message) {
        final AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_alert);
        bld.setCancelable(false);
        bld.setTitle(R.string.close_window);
        if (message != null) bld.setMessage(message);
        bld.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                if (mVimPythonMode != -1) {
                    if (new File(TermService.getAPPFILES() + "/bin/vim.python").canExecute()) {
                        String vimsh = TermService.getAPPFILES() + "/bin/vim";
                        String mode = mVimPythonMode == 0 ? "" : ".alt";
                        new File(vimsh).delete();
                        shell("cat " + TermService.getAPPFILES() + "/usr/etc/src.vim.python" + mode + " > " + vimsh);
                        shell("chmod 755 " + vimsh);
                    }
                    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    final SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("VIM_PYTHON_MODE", mVimPythonMode);
                    editor.apply();
                }
                if (mLdLibraryPathMode != -1) {
                    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    final SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("FATAL_CRASH_RESOLVER", mLdLibraryPathMode);
                    editor.apply();
                }
                if (mUninstall) doUninstallExtraContents();
                if (mLibrary != -1) {
                    shell("rm " + TermService.getAPPEXTFILES() + "/.64bit");
                    shell("rm " + TermService.getAPPEXTFILES() + "/.32bit");
                    if (mLibrary == 0) {
                        shell("cat " + TermService.getAPPEXTFILES() + "/version > " + TermService.getVimRuntimeInstallDir() + "/.64bit");
                    } else if (mLibrary == 1) {
                        shell("cat " + TermService.getAPPEXTFILES() + "/version > " + TermService.getVimRuntimeInstallDir() + "/.32bit");
                    }
                }
                doCloseWindow();
            }
        });
        bld.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                fatalCrashVim();
            }
        });
        AlertDialog dlg = bld.create();
        dlg.setCancelable(false);
        dlg.setCanceledOnTouchOutside(false);
        dlg.show();
    }

    private final float EDGE = (float) 50.0;

    private boolean doDoubleTapAction(MotionEvent me) {
        EmulatorView v = (EmulatorView) mViewFlipper.getCurrentView();
        if (v != null) {
            Resources resources = getApplicationContext().getResources();
            DisplayMetrics metrics = resources.getDisplayMetrics();
            float px = EDGE * (metrics.densityDpi / 160.0f);
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

    private void AndroidIntent(String filename) {
        if (filename == null) return;
        TermSession session = getCurrentTermSession();
        if (session == null) return;
        String[] str = new String[3];
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
                alert("Unknown activity:\n" + str1);
            }
            return;
        } else if (str1.matches("^%(w3m|open)%.*")) {
            str1 = str1.replaceFirst("%(w3m|open)%", "");
        } else if (action.equalsIgnoreCase("share.file")) {
            File file = new File(str1);
            if (!file.canRead()) {
                alert(this.getString(R.string.storage_read_error) + "\n" + str1);
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
            boolean extFilesStorage = path.matches(TermService.getAPPEXTFILES() + "/.*");
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
                        if (!ext.equals("")) hash += "." + ext;

                        File cacheDir = new File(INTENT_CACHE_DIR);
                        File cache = new File(cacheDir.toString() + "/" + hash);
                        if (cache.isDirectory()) {
                            shell("rm -rf " + cache.getAbsolutePath());
                        }
                        if (!cacheDir.isDirectory()) {
                            cacheDir.mkdirs();
                        }
                        if (!copyFile(file, cache)) return;
                        try {
                            uri = FileProvider.getUriForFile(getApplicationContext(), BuildConfig.APPLICATION_ID + ".fileprovider", cache);
                            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception makeCacheErr) {
                            alert(this.getString(R.string.prefs_read_error_title) + "\n" + path);
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
                    PackageManager pm = this.getApplicationContext().getPackageManager();
                    if (intent.resolveActivity(pm) != null) startActivity(intent);
                } else {
                    intent.setAction(action);
                    PackageManager pm = this.getApplicationContext().getPackageManager();
                    if (intent.resolveActivity(pm) != null) startActivity(intent);
                    else alert("Unknown action:\n" + action);
                }
            } catch (Exception e) {
                alert(Term.this.getString(R.string.storage_read_error));
            }
        } else {
            alert("Unknown action:\n" + action);
        }
    }

    private boolean copyFile(File src, File dst) {
        try {
            InputStream is = new FileInputStream(src);
            BufferedInputStream reader = new BufferedInputStream(is);

            OutputStream os = new FileOutputStream(dst);
            BufferedOutputStream writer = new BufferedOutputStream(os);
            byte[] buf = new byte[4096];
            int len;
            while ((len = reader.read(buf)) != -1) {
                writer.write(buf, 0, len);
            }
            writer.flush();
            writer.close();
            reader.close();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private final static String HASH_ALGORITHM = "SHA-1";

    private String getHashString(String s) {
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance(HASH_ALGORITHM);
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();

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
        if (mViewFlipper != null) mViewFlipper.redraw();
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
                final Toast toast = Toast.makeText(this, R.string.email_transcript_no_email_activity_found, Toast.LENGTH_LONG);
                showToast(toast);
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

    private void drawerMenu() {
        String externalApp = null;
        if (mSettings.getExternalAppButtonMode() > 0) {
            externalApp = getString(R.string.external_app_button);
            try {
                String appId = mSettings.getExternalAppId();
                PackageManager pm = this.getApplicationContext().getPackageManager();
                PackageInfo packageInfo = pm.getPackageInfo(appId, 0);
                externalApp = packageInfo.applicationInfo.loadLabel(pm).toString();
            } catch (Exception e) {
                // Do nothing
            }
        }
        final String externalAppLabel = externalApp;
        final String[] externalApps = externalApp != null ? new String[]{externalApp} : new String[]{};

        final Map<String, String> apps = new LinkedHashMap<String, String>() {
            {
                put(getString(R.string.dropbox), APP_DROPBOX);
            }

            {
                put(getString(R.string.googledrive), APP_GOOGLEDRIVE);
            }

            {
                put(getString(R.string.onedrive), APP_ONEDRIVE);
            }
        };
        String[] appLabels = new String[apps.size()];
        int storages = 0;
        for (Map.Entry<String, String> entry : apps.entrySet()) {
            String app = entry.getValue();
            if (APP_DROPBOX.equals(app) && mSettings.getDropboxFilePicker() == 0) continue;
            if (APP_GOOGLEDRIVE.equals(app) && mSettings.getGoogleDriveFilePicker() == 0) continue;
            if (APP_ONEDRIVE.equals(app) && mSettings.getOneDriveFilePicker() == 0) continue;
            if (isAppInstalled(entry.getValue())) {
                appLabels[storages] = entry.getKey();
                storages++;
            }
        }
        final String[] storageApps = new String[storages];
        System.arraycopy(appLabels, 0, storageApps, 0, storages);

        final String[] base = {
                this.getString(R.string.file_chooser),
                this.getString(R.string.create_or_delete),
        };

        final String[] items = new String[base.length + storageApps.length + externalApps.length];

        System.arraycopy(base, 0, items, 0, base.length);
        System.arraycopy(appLabels, 0, items, base.length, storageApps.length);
        System.arraycopy(externalApps, 0, items, base.length + storageApps.length, externalApps.length);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.storage_menu))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (getString(R.string.file_chooser).equals(items[which])) {
                            filePicker();
                        } else if (getString(R.string.create_or_delete).equals(items[which])) {
                            storageMenu();
                        } else if (getString(R.string.dropbox).equals(items[which])) {
                            launchExternalApp(mSettings.getDropboxFilePicker(), APP_DROPBOX);
                        } else if (getString(R.string.googledrive).equals(items[which])) {
                            launchExternalApp(mSettings.getGoogleDriveFilePicker(), APP_GOOGLEDRIVE);
                        } else if (getString(R.string.onedrive).equals(items[which])) {
                            launchExternalApp(mSettings.getOneDriveFilePicker(), APP_ONEDRIVE);
                        } else if (externalAppLabel.equals(items[which])) {
                            externalApp();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void shareIntentTextDialog() {
        final String[] items = {this.getString(R.string.share_buffer_text), this.getString(R.string.share_visual_text), this.getString(R.string.share_unnamed_text), this.getString(R.string.share_file)};
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
        try {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, text);
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent, "Share"));
        } catch (Exception e) {
            complain("ShareIntent Text: " + e.toString());
        }
    }

    private void doShareAll() {
        final String[] items = {this.getString(R.string.copy_screen_current), this.getString(R.string.copy_screen_buffer)};
        new AlertDialog.Builder(this)
                .setTitle(this.getString(R.string.share_screen_text))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doCopyAll(which + 2);
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
        final Toast toast = Toast.makeText(this, mes, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        showToast(toast);
    }

    private static boolean mVimPaste = false;

    private void doPaste() {
        doWarningBeforePaste();
    }

    private void choosePasteMode() {
        final String[] items = {
                getString(R.string.paste_vim),
                getString(R.string.paste_shell) };

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.clipboard))
                .setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        String item = items[which];
                        if (getString(R.string.paste_vim).equals(item)) {
                            sendKeyStrings("\"*p", true);
                        } else if (getString(R.string.paste_shell).equals(item)) {
                            doTermPaste();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doTermPaste() {
        if (!canPaste()) {
            alert(Term.this.getString(R.string.toast_clipboard_error));
            return;
        }
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setTitle(R.string.clipboard_warning_title);
        builder.setMessage(R.string.clipboard_warning);
        builder.setPositiveButton(getString(R.string.paste), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int m) {
                choosePasteMode();
            }
        });
        builder.setNeutralButton(getString(R.string.share_title), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int m) {
                shareIntentTextDialog();
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
        if (imm != null) imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        requestFocusView();
    }

    private void doShowSoftKeyboard() {
        if (getCurrentEmulatorView() == null) return;
        Activity activity = this;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.showSoftInput(getCurrentEmulatorView(), InputMethodManager.SHOW_FORCED);
        requestFocusView();
    }

    private void doHideSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = getCurrentFocus();
        if (imm != null && view != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        requestFocusView();
    }

    private void requestFocusView() {
        EmulatorView view = getCurrentEmulatorView();
        if (view != null) {
            view.requestFocusFromTouch();
        }
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
            if (!mKeepScreenEnableAuto) showToast(toast);
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
                    final long timeoutMills = mSettings.getKeepScreenTime() * 60L * 1000L;
                    final long currentTimeMillis = System.currentTimeMillis();
                    if (keepScreen) {
                        if (currentTimeMillis >= mLastKeyPress + timeoutMills) {
                            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            if (!mKeepScreenEnableAuto) showToast(toast);
                        } else {
                            mKeepScreenHandler.postDelayed(this, timeoutMills - (currentTimeMillis - mLastKeyPress));
                        }
                    }
                }
            };
            mKeepScreenHandler.removeCallbacksAndMessages(null);
            long timeoutMills = mSettings.getKeepScreenTime() * 60L * 1000L;
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
     * Send a URL up to Android to be handled by a browser.
     *
     * @param link The URL to be opened.
     */
    private void execURL(String link) {
        Uri webLink = Uri.parse(link);
        Intent openLink = new Intent(Intent.ACTION_VIEW, webLink);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> handlers = pm.queryIntentActivities(openLink, 0);
        if (handlers.size() > 0)
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
        mEditText = findViewById(R.id.text_input);
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
        setEditTextViewSize();
        if (mViewFlipper != null) mViewFlipper.setEditTextView(visibility == View.VISIBLE);
    }

    private void doWarningEditTextView() {
        if (!mVimFlavor) return;
        doWarningDialog(this.getString(R.string.edit_text_view_warning_title), this.getString(R.string.edit_text_view_warning), "do_warning_edit_text_view", true);
    }

    final float SCALE_VIEW = (float) 1.28;

    private void setEditTextViewSize() {
        int size = 0;
        if (mEditTextView) {
            // FIXME: cannot get EditText size at first time.
            size = findViewById(R.id.oneline_text_box).getHeight();
            if (size == 0) {
                size = findViewById(R.id.view_function_bar).getHeight();
                if (size <= 0) {
                    final TextView textView = findViewById((R.id.text_input));
                    float sp = SCALE_VIEW * textView.getTextSize() * getApplicationContext().getResources().getDisplayMetrics().scaledDensity;
                    size = (int) Math.ceil(sp);
                }
            }
        }
        if (mViewFlipper != null) mViewFlipper.setEditTextViewSize(size);
    }

    private static int mFunctionBar = -1;

    private void setFunctionBar(int mode) {
        boolean focus = false;
        if (mEditText != null && mEditTextView) focus = mEditText.hasFocus();
        if (mode == 2) {
            mFunctionBar = mFunctionBar == 0 ? 1 : 0;
            if (mHideFunctionBar) mFunctionBar = 1;
            mHideFunctionBar = false;
        } else mFunctionBar = mode;
        if (mAlreadyStarted) updatePrefs();
        if (!focus && mEditText != null) {
            EmulatorView view = getCurrentEmulatorView();
            if (view != null) view.requestFocusFromTouch();
        }
    }

    private void setFunctionBarSize() {
        int size;
        size = findViewById(R.id.view_function_bar).getHeight();
        if (mFunctionBarId == 1) size += size;
        if (mViewFlipper != null) mViewFlipper.setFunctionBarSize(size);
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
            new FunctionKey("functionbar_esc", R.id.button_esc, false),
            new FunctionKey("functionbar_ctrl", R.id.button_ctrl, true),
            new FunctionKey("functionbar_alt", R.id.button_alt, false),
            new FunctionKey("functionbar_tab", R.id.button_tab, true),
            new FunctionKey("functionbar_up", R.id.button_up, false),
            new FunctionKey("functionbar_down", R.id.button_down, false),
            new FunctionKey("functionbar_left", R.id.button_left, false),
            new FunctionKey("functionbar_right", R.id.button_right, false),
            new FunctionKey("functionbar_page_up", R.id.button_page_up, false),
            new FunctionKey("functionbar_page_down", R.id.button_page_down, false),
            new FunctionKey("functionbar_backspace", R.id.button_backspace, false),
            new FunctionKey("functionbar_enter", R.id.button_enter, false),
            new FunctionKey("functionbar_i", R.id.button_i, false),
            new FunctionKey("functionbar_colon", R.id.button_colon, true),
            new FunctionKey("functionbar_slash", R.id.button_slash, false),
            new FunctionKey("functionbar_plus", R.id.button_plus, false),
            new FunctionKey("functionbar_equal", R.id.button_equal, false),
            new FunctionKey("functionbar_asterisk", R.id.button_asterisk, false),
            new FunctionKey("functionbar_pipe", R.id.button_pipe, false),
            new FunctionKey("functionbar_minus", R.id.button_minus, false),
            new FunctionKey("functionbar_vim_paste", R.id.button_vim_paste, true),
            new FunctionKey("functionbar_vim_yank", R.id.button_vim_yank, true),
            new FunctionKey("functionbar_softkeyboard", R.id.button_softkeyboard, false),
            new FunctionKey("functionbar_invert", R.id.button_invert, true),
            new FunctionKey("functionbar_menu", R.id.button_menu, true),
            new FunctionKey("functionbar_menu_hide", R.id.button_menu_hide, true),
            new FunctionKey("functionbar_menu_plus", R.id.button_menu_plus, false),
            new FunctionKey("functionbar_menu_minus", R.id.button_menu_minus, false),
            new FunctionKey("functionbar_menu_x", R.id.button_menu_x, false),
            new FunctionKey("functionbar_ime_toggle", R.id.button_ime_toggle, false),
            new FunctionKey("functionbar_menu_user", R.id.button_menu_user, true),
            new FunctionKey("functionbar_menu_quit", R.id.button_menu_quit, true),
            new FunctionKey("functionbar_next0", R.id.button_next_functionbar0, true),
            new FunctionKey("functionbar_next2", R.id.button_next_functionbar2, true),
            new FunctionKey("functionbar_prev", R.id.button_prev_functionbar, true),
            new FunctionKey("functionbar_prev2", R.id.button_prev_functionbar2, true),
            new FunctionKey("functionbar_m1", R.id.button_m1, true),
            new FunctionKey("functionbar_m2", R.id.button_m2, true),
            new FunctionKey("functionbar_m3", R.id.button_m3, true),
            new FunctionKey("functionbar_m4", R.id.button_m4, true),
            new FunctionKey("functionbar_m5", R.id.button_m5, true),
            new FunctionKey("functionbar_m6", R.id.button_m6, true),
            new FunctionKey("functionbar_m7", R.id.button_m7, true),
            new FunctionKey("functionbar_m8", R.id.button_m8, true),
            new FunctionKey("functionbar_m9", R.id.button_m9, true),
            new FunctionKey("functionbar_m10", R.id.button_m10, true),
            new FunctionKey("functionbar_m11", R.id.button_m11, true),
            new FunctionKey("functionbar_m12", R.id.button_m12, true),
            new FunctionKey("navigationbar_esc", R.id.button_navigation_esc, true),
            new FunctionKey("navigationbar_ctrl", R.id.button_navigation_ctrl, false),
            new FunctionKey("navigationbar_alt", R.id.button_navigation_alt, false),
            new FunctionKey("navigationbar_tab", R.id.button_navigation_tab, false),
            new FunctionKey("navigationbar_up", R.id.button_navigation_up, true),
            new FunctionKey("navigationbar_down", R.id.button_navigation_down, true),
            new FunctionKey("navigationbar_left", R.id.button_navigation_left, false),
            new FunctionKey("navigationbar_right", R.id.button_navigation_right, false),
            new FunctionKey("navigationbar_page_up", R.id.button_navigation_page_up, false),
            new FunctionKey("navigationbar_page_down", R.id.button_navigation_page_down, false),
            new FunctionKey("navigationbar_backspace", R.id.button_navigation_backspace, false),
            new FunctionKey("navigationbar_enter", R.id.button_navigation_enter, false),
            new FunctionKey("navigationbar_i", R.id.button_navigation_i, false),
            new FunctionKey("navigationbar_colon", R.id.button_navigation_colon, false),
            new FunctionKey("navigationbar_slash", R.id.button_navigation_slash, false),
            new FunctionKey("navigationbar_equal", R.id.button_navigation_equal, false),
            new FunctionKey("navigationbar_asterisk", R.id.button_navigation_asterisk, false),
            new FunctionKey("navigationbar_pipe", R.id.button_navigation_pipe, false),
            new FunctionKey("navigationbar_plus", R.id.button_navigation_plus, false),
            new FunctionKey("navigationbar_minus", R.id.button_navigation_minus, false),
            new FunctionKey("navigationbar_vim_paste", R.id.button_navigation_vim_paste, false),
            new FunctionKey("navigationbar_vim_yank", R.id.button_navigation_vim_yank, false),
            new FunctionKey("navigationbar_softkeyboard", R.id.button_navigation_softkeyboard, true),
            new FunctionKey("navigationbar_invert", R.id.button_navigation_invert, false),
            new FunctionKey("navigationbar_menu", R.id.button_navigation_menu, false),
            new FunctionKey("navigationbar_menu_hide", R.id.button_navigation_menu_hide, false),
            new FunctionKey("navigationbar_menu_plus", R.id.button_navigation_menu_plus, false),
            new FunctionKey("navigationbar_menu_minus", R.id.button_navigation_menu_minus, false),
            new FunctionKey("navigationbar_menu_x", R.id.button_navigation_menu_x, false),
            new FunctionKey("navigationbar_fn_toggle", R.id.button_navigation_fn_toggle, true),
            new FunctionKey("navigationbar_ime_toggle", R.id.button_navigation_ime_toggle, true),
            new FunctionKey("navigationbar_menu_user", R.id.button_navigation_menu_user, false),
            new FunctionKey("navigationbar_menu_quit", R.id.button_navigation_menu_quit, false),
    };

    private void setFunctionKeyListener() {
        for (FunctionKey fkey : FunctionKeys) {
            switch (fkey.resid) {
                case R.id.button_up:
                case R.id.button_down:
                case R.id.button_left:
                case R.id.button_right:
                case R.id.button_navigation_up:
                case R.id.button_navigation_down:
                case R.id.button_navigation_left:
                case R.id.button_navigation_right:
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
        Button button = findViewById(R.id.button_oneline_text_box_clear);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onelineTextBoxClear();
            }
        });

        int visibility = (mSettings.getOneLineTextBoxCr()) ? View.VISIBLE : View.GONE;
        button = findViewById(R.id.button_oneline_text_box_enter);
        button.setVisibility(visibility);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onelineTextBoxEnter(true);
            }
        });
        String label = getString(R.string.string_functionbar_enter);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) button.setText(label);
        button = findViewById(R.id.button_enter);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) button.setText(label);
        label = getString(R.string.string_functionbar_ime_toggle);
        button = findViewById(R.id.button_navigation_ime_toggle);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) button.setText(label);
        button = findViewById(R.id.button_ime_toggle);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) button.setText(label);
    }

    private boolean onelineTextBoxClear() {
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

    private boolean onelineTextBoxEsc() {
        if (mEditTextView) {
            if (mEditText != null && mEditText.isFocused()) {
                EmulatorView view = getCurrentEmulatorView();
                if (view != null) view.requestFocusFromTouch();
                if (mSettings != null && mSettings.getOneLineTextBoxEsc()) {
                    setEditTextView(0);
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    private boolean onelineTextBoxTab() {
        if (mEditTextView) {
            if (mEditText != null && mEditText.isFocused()) {
                sendKeyStrings("\t", false);
                return true;
            }
        }
        return false;
    }

    private boolean onelineTextBoxEnter(boolean force) {
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
        if (id == R.id.button_menu_plus) visibility = View.GONE;
        if (id == R.id.button_menu_minus) visibility = View.GONE;
        if (id == R.id.button_menu_x) visibility = View.GONE;
        if (id == R.id.button_navigation_menu_plus) visibility = View.GONE;
        if (id == R.id.button_navigation_menu_minus) visibility = View.GONE;
        if (id == R.id.button_navigation_menu_x) visibility = View.GONE;
        String label = prefs.getString(FKEY_LABEL + key, "");
        setFunctionBarButton(id, visibility, label);

        Button button = findViewById(R.id.button_oneline_text_box_enter);
        visibility = (mSettings.getOneLineTextBoxCr()) ? View.VISIBLE : View.GONE;
        button.setVisibility(visibility);

        setCursorDirectionLabel();

        visibility = mSettings.showFunctionBarNavigationButton() ? View.VISIBLE : View.GONE;
        LinearLayout layout = findViewById(R.id.virtual_navigation_bar);
        layout.setVisibility(visibility);
    }

    static int mFunctionBarId = 0;

    private void setFunctionKeyVisibility() {
        int visibility;
        if (mHideFunctionBar) {
            visibility = View.GONE;
            findViewById(R.id.view_function_bar).setVisibility(visibility);
            findViewById(R.id.view_function_bar2).setVisibility(visibility);
            setFunctionBarSize();
            mViewFlipper.setFunctionBar(false);
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        for (FunctionKey fkey : FunctionKeys) {
            setFunctionKeyVisibility(prefs, fkey.key, fkey.resid, fkey.defValue);
        }

        visibility = (mFunctionBar == 1) ? View.VISIBLE : View.GONE;
        findViewById(R.id.view_function_bar).setVisibility(visibility);
        findViewById(R.id.view_function_bar1).setVisibility(visibility);
        visibility = (mFunctionBar == 1 && mFunctionBarId == 1) ? View.VISIBLE : View.GONE;
        findViewById(R.id.view_function_bar2).setVisibility(visibility);
        setFunctionBarSize();
        mViewFlipper.setFunctionBar(mFunctionBar == 1);
    }

    @SuppressLint("NewApi")
    private void setFunctionBarButton(int id, int visibility, String label) {
        Button button = findViewById(id);
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
        setScreenFitFunctionBarShortLabel();
    }

    private void setScreenFitFunctionBarShortLabel() {
        View view = getCurrentEmulatorView();
        if (view != null) view.post(new Thread(new Runnable() {
            public void run() {
                setShortButtonLabel(R.id.button_navigation_esc, "Esc");
                setShortButtonLabel(R.id.button_navigation_ctrl, "Ctrl");
                setShortButtonLabel(R.id.button_navigation_tab, "Tab");
                setShortButtonLabel(R.id.button_navigation_alt, "Alt");
            }
        }));
    }

    private void setShortButtonLabel(int id, String label) {
        Button button = findViewById(id);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        if (button != null) {
            float textSize = button.getTextSize();
            Paint paint = new Paint();
            paint.setTextSize(textSize);
            int padding = button.getPaddingLeft() + button.getPaddingRight();
            int buttonMaxWidth = button.getWidth() - padding;
            float textWidth = paint.measureText(label);
            if (textWidth > buttonMaxWidth) {
                label = label.substring(0, 1);
            }
            button.setText(label);
        }
    }

    public void onClick(View v) {
        EmulatorView view = getCurrentEmulatorView();
        switch (v.getId()) {
            case R.id.button_navigation_ime_toggle:
            case R.id.button_ime_toggle:
                doToggleSoftKeyboard();
                break;
            case R.id.button_navigation_esc:
            case R.id.button_esc:
                if (view.getControlKeyState() != 0 || (getInvertCursorDirection() != getDefaultInvertCursorDirection())) {
                    mInvertCursorDirection = getDefaultInvertCursorDirection();
                    setCursorDirectionLabel();
                    view.setControlKeyState(0);
                    break;
                }
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_ESCAPE);
                break;
            case R.id.button_navigation_ctrl:
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
            case R.id.button_navigation_alt:
            case R.id.button_alt:
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_ALT_LEFT);
                break;
            case R.id.button_navigation_tab:
            case R.id.button_tab:
                if (onelineTextBoxTab()) break;
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_TAB);
                break;
            case R.id.button_navigation_up:
            case R.id.button_navigation_down:
            case R.id.button_navigation_left:
            case R.id.button_navigation_right:
            case R.id.button_up:
            case R.id.button_down:
            case R.id.button_left:
            case R.id.button_right:
                int state = view.getControlKeyState();
                boolean invert = getInvertCursorDirection();
                if ((!invert && v.getId() == R.id.button_up) ||
                        (!invert && v.getId() == R.id.button_navigation_up) ||
                        (invert && v.getId() == R.id.button_navigation_left) ||
                        (invert && v.getId() == R.id.button_left)) {
                    doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_UP);
                } else if ((!invert && v.getId() == R.id.button_down) ||
                        (!invert && v.getId() == R.id.button_navigation_down) ||
                        (invert && v.getId() == R.id.button_navigation_right) ||
                        (invert && v.getId() == R.id.button_right)) {
                    doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_DOWN);
                } else if ((!invert && v.getId() == R.id.button_left) ||
                        (!invert && v.getId() == R.id.button_navigation_left) ||
                        (invert && v.getId() == R.id.button_navigation_up) ||
                        (invert && v.getId() == R.id.button_up)) {
                    doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_LEFT);
                } else if ((!invert && v.getId() == R.id.button_right) ||
                        (!invert && v.getId() == R.id.button_navigation_right) ||
                        (invert && v.getId() == R.id.button_navigation_down) ||
                        (invert && v.getId() == R.id.button_down)) {
                    doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_RIGHT);
                }
                if (state == RELEASED && mSettings.getCursorDirectionControlMode() == 1) {
                    view.setControlKeyState(UNPRESSED);
                    mInvertCursorDirection = getDefaultInvertCursorDirection();
                    setCursorDirectionLabel();
                }
                break;
            case R.id.button_navigation_page_up:
            case R.id.button_page_up:
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_PAGE_UP);
                break;
            case R.id.button_navigation_page_down:
            case R.id.button_page_down:
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_PAGE_DOWN);
                break;
            case R.id.button_navigation_backspace:
            case R.id.button_backspace:
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_DEL);
                break;
            case R.id.button_navigation_enter:
            case R.id.button_enter:
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_ENTER);
                break;
            case R.id.button_navigation_i:
            case R.id.button_i:
                sendKeyStrings("i", false);
                if (!mHaveFullHwKeyboard) doShowSoftKeyboard();
                break;
            case R.id.button_navigation_colon:
            case R.id.button_colon:
                sendKeyStrings(":", false);
                break;
            case R.id.button_navigation_slash:
            case R.id.button_slash:
                sendKeyStrings("/", false);
                break;
            case R.id.button_navigation_equal:
            case R.id.button_equal:
                sendKeyStrings("=", false);
                break;
            case R.id.button_navigation_asterisk:
            case R.id.button_asterisk:
                sendKeyStrings("*", false);
                break;
            case R.id.button_navigation_pipe:
            case R.id.button_pipe:
                sendKeyStrings("|", false);
                break;
            case R.id.button_navigation_plus:
            case R.id.button_plus:
                sendKeyStrings("+", false);
                break;
            case R.id.button_navigation_minus:
            case R.id.button_minus:
                sendKeyStrings("-", false);
                break;
            case R.id.button_navigation_vim_paste:
            case R.id.button_vim_paste:
                sendKeyStrings("\"*p", false);
                break;
            case R.id.button_navigation_vim_yank:
            case R.id.button_vim_yank:
                sendKeyStrings("\"*yy" + "\u001b", false);
                break;
            case R.id.button_navigation_menu_plus:
            case R.id.button_menu_plus:
                doSendActionBarKey(view, mSettings.getActionBarPlusKeyAction());
                break;
            case R.id.button_navigation_menu_minus:
            case R.id.button_menu_minus:
                doSendActionBarKey(view, mSettings.getActionBarMinusKeyAction());
                break;
            case R.id.button_navigation_menu_x:
            case R.id.button_menu_x:
                doSendActionBarKey(view, mSettings.getActionBarXKeyAction());
                break;
            case R.id.button_navigation_menu_user:
            case R.id.button_menu_user:
                doSendActionBarKey(view, mSettings.getActionBarUserKeyAction());
                break;
            case R.id.button_navigation_menu_quit:
            case R.id.button_menu_quit:
                doSendActionBarKey(view, mSettings.getActionBarQuitKeyAction());
                break;
            case R.id.button_navigation_softkeyboard:
            case R.id.button_softkeyboard:
                doSendActionBarKey(view, mSettings.getActionBarIconKeyAction());
                break;
            case R.id.button_navigation_invert:
            case R.id.button_invert:
                doSendActionBarKey(view, mSettings.getActionBarInvertKeyAction());
                break;
            case R.id.button_navigation_menu:
            case R.id.button_menu:
                openOptionsMenu();
                break;
            case R.id.button_navigation_fn_toggle:
            case R.id.button_navigation_menu_hide:
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
        if (!SCOPED_STORAGE && isExternalStorageDocument(uri)) {
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
                path = uri.toString().replaceFirst("content://", "/");
            }
            if (path != null) {
                path = "/" + path.replaceAll("\\%(2F|3A|3B|3D|0A)", "/");
                String fname = new File(path).getName();
                if (displayName != null && !(fname == null || fname.equals(displayName))) {
                    path = path + "/" + displayName;
                }
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

    public void onDebugButtonClicked(final View arg0) {
    }

    private boolean mOnExtraButtonClicked = false;

    public void onExtraButtonClicked(View arg0) {
        Intent intent = new Intent(this, ExtraContents.class);
        startActivity(intent);
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
        ImageView iv = view.findViewById(R.id.iv_image_dialog);
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
            try {
                Instrumentation inst = new Instrumentation();
                if (params[0] == KEYEVENT_SENDER_SHIFT_SPACE) {
                    inst.sendKeySync(new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 1, KeyEvent.META_SHIFT_ON));
                } else if (params[0] == KEYEVENT_SENDER_ALT_SPACE) {
                    inst.sendKeySync(new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 1, KeyEvent.META_ALT_ON));
                } else {
                    inst.sendKeyDownUpSync(params[0]);
                }
            } catch (Exception e) {
                // Do nothing
            }
            return null;
        }
    }

}

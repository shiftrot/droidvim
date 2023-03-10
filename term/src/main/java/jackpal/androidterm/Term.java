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

import static android.provider.DocumentsContract.Document;
import static android.provider.DocumentsContract.deleteDocument;
import static jackpal.androidterm.StaticConfig.FLAVOR_TERMINAL;
import static jackpal.androidterm.StaticConfig.FLAVOR_VIM;
import static jackpal.androidterm.StaticConfig.SCOPED_STORAGE;
import static jackpal.androidterm.TermVimInstaller.copyScript;
import static jackpal.androidterm.TermVimInstaller.shell;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;
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
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

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
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.emulatorview.UpdateCallback;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompat;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompatFactory;
import jackpal.androidterm.emulatorview.compat.EscCmd;
import jackpal.androidterm.emulatorview.compat.KeycodeConstants;
import jackpal.androidterm.util.SessionList;
import jackpal.androidterm.util.TermSettings;

/**
 * A terminal emulator activity.
 */

public class Term extends AppCompatActivity implements UpdateCallback, SharedPreferences.OnSharedPreferenceChangeListener, OnClickListener {
    public static final int REQUEST_CHOOSE_WINDOW = 1;
    public static final int REQUEST_FILE_PICKER = 2;
    public static final int REQUEST_FILE_DELETE = 3;
    public static final int REQUEST_VOICE_INPUT = 5;
    public static final int REQUEST_DOCUMENT_TREE = 10;
    public static final int REQUEST_COPY_DOCUMENT_TREE_TO_HOME = 11;
    public static final int REQUEST_COPY_DOCUMENT_TREE_BACKUP_HOME = 12;
    public static final int REQUEST_COPY_DOCUMENT_TREE_RESTORE_TO_HOME = 13;
    public static final int REQUEST_WEBVIEW_ACTIVITY = 15;
    public static final int REQUEST_HTML_LOG_ACTIVITY = REQUEST_WEBVIEW_ACTIVITY + 1;
    public static final int WEBVIEW_DEFAULT_FONT_SIZE = 140;
    private static final String WEBVIEW_FONT_SIZE = "mWebViewFontSize";
    private static final String WEBVIEW_HTML_LOG_FONT_SIZE = "mHtmlLogWebViewFontSize";
    public static final String EXTRA_WINDOW_ID = "jackpal.androidterm.window_id";
    public static final int REQUEST_STORAGE = 10000;
    public static final int REQUEST_STORAGE_DELETE = 10001;
    public static final int REQUEST_STORAGE_CREATE = 10002;
    public static final int REQUEST_FOREGROUND_SERVICE_PERMISSION = 10003;
    public static final int REQUEST_NOTIFICATIONS = 10004;
    public static final String SHELL_ESCAPE = "([ *?\\[{`$&%#'\"|!<;])";
    private static final String FKEY_LABEL = "fkey_label";
    private static final int FKEY_MAX = 12;
    private static final String[] mCmd_FKEY = new String[FKEY_MAX];
    private static final int VIEW_FLIPPER = R.id.view_flipper;
    private static final int PASTE_ID = 0;
    private static final int COPY_ALL_ID = 1;
    private static final int SELECT_TEXT_ID = 2;
    private static final int SEND_CONTROL_KEY_ID = 3;
    private static final int SEND_FN_KEY_ID = 4;
    private static final int SEND_FUNCTION_BAR_ID = 5;
    private static final int SEND_MENU_ID = 6;
    private static final int UNPRESSED = 0;
    private static final int PRESSED = 1;
    private static final int RELEASED = 2;
    private static final int USED = 3;
    private static final int LOCKED = 4;
    private static final String APP_DROPBOX = "com.dropbox.android";
    private static final String APP_GOOGLEDRIVE = "com.google.android.apps.docs";
    private static final String APP_ONEDRIVE = "com.microsoft.skydrive";
    private String APP_FILER;
    public static final int TERMINAL_MODE_DISABLE = 0x00;
    public static final int TERMINAL_MODE_ENABLE = 0x01;
    public static final int TERMINAL_MODE_BASH = 0x02;
    public static final int TERMINAL_MODE_PROOT = 0x04;
    public static int mTerminalMode = TERMINAL_MODE_DISABLE;
    public static final String TERMINAL_MODE_FILE = "/.terminal.mode";
    private static final String mSyncFileObserverFile = "SyncFileObserver.json";
    private static final String HASH_ALGORITHM = "SHA-1";
    private static final int KEYEVENT_SENDER_SHIFT_SPACE = -1;
    private static final int KEYEVENT_SENDER_ALT_SPACE = -2;
    private static int mTheme = -1;
    private static boolean mFirstInputtype = true;
    private static int mOrientation = -1;
    private static int mFunctionBarId = 0;
    private static SyncFileObserver mSyncFileObserver = null;
    private static final Random mRandom = new Random();
    private static boolean mInvertCursorDirection = false;
    private static boolean mDefaultInvertCursorDirection = false;
    private static boolean mUninstall = false;
    private static boolean mEditTextView = false;
    private static int mFunctionBar = -1;
    private static int mSenderKeyEvent = KEYEVENT_SENDER_SHIFT_SPACE;
    private static final Runnable keyEventSenderAction = () -> {
        KeyEventSender sender = new KeyEventSender();
        sender.execute(mSenderKeyEvent);
    };
    private final Handler mKeepScreenHandler = new Handler();
    private long mLastKeyPress = System.currentTimeMillis();
    private ArrayList<String> mFilePickerItems;
    private boolean mFirst = true;

    private List<FunctionKey> mFunctionKeys = new ArrayList<>();
    private String FUNCTIONBAR_UP = "▲";
    private String FUNCTIONBAR_DOWN = "▼";
    private String FUNCTIONBAR_RIGHT = "▶";
    private String FUNCTIONBAR_LEFT = "◀";

    private TermViewFlipper mViewFlipper;
    private SessionList mTermSessions;
    private TermSettings mSettings;
    private boolean mAlreadyStarted = false;
    private boolean mStopServiceOnFinish = false;
    private Intent TSIntent;
    private int onResumeSelectWindow = -1;
    private ComponentName mPrivateAlias;
    private OnBackPressedCallback mOnBackPressedCallback;
    private OnBackInvokedCallback mOnBackInvokedCallback;

    private TermService mTermService;
    private boolean mHaveFullHwKeyboard = false;
    private boolean mUseKeyboardShortcuts;
    private boolean mVolumeAsCursor = false;
    private final Handler mHandler = new Handler();
    private int mPrevHaveFullHwKeyboard = -1;
    private boolean mHideFunctionBar = false;
    private boolean mFatalTroubleShooting = false;
    private boolean mKeepScreenEnableAuto = false;
    private final View.OnKeyListener mKeyListener = new View.OnKeyListener() {
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            onLastKey();
            return keyboardShortcuts(keyCode, event);
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

    };
    private int mOnelineTextBox = -1;
    private EditText mEditText;
    private ServiceConnection mTSConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TermDebug.LOG_TAG, "Bound to TermService");
            TermService.TSBinder binder = (TermService.TSBinder) service;
            mTermService = binder.getService();
            populateViewFlipper();
        }

        public void onServiceDisconnected(ComponentName arg0) {
            mTermService = null;
        }
    };

    public void showSnackbar(final String message) {
        Snackbar snackbar = Snackbar.make(findViewById(R.id.term_coordinator_layout_top), message, Snackbar.LENGTH_LONG);
        View snackbarView = snackbar.getView();
        TextView tv = snackbarView.findViewById(R.id.snackbar_text);
        tv.setMaxLines(2);
        snackbar.show();
    }

    public static void showToast(final Toast toast) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mTheme == 2) {
            View v = toast.getView();
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    View c = g.getChildAt(i);
                    if (c instanceof TextView) {
                        ((TextView) c).setTextColor(Color.DKGRAY);
                        c.setBackgroundColor(Color.argb(60, 255, 255, 255));
                    }
                }
            }
        }
        toast.show();
    }

    public static File getScratchCacheDir(AppCompatActivity activity) {
        int sdcard = TermService.getSDCard(activity.getApplicationContext());
        String cacheDir = TermService.getCacheDir(activity.getApplicationContext(), sdcard);
        return new File(cacheDir + "/scratch");
    }

    static SyncFileObserver restoreSyncFileObserver(AppCompatActivity activity) {
        saveSyncFileObserver();
        File dir = getScratchCacheDir(activity);
        AppCompatActivity prevActivity = null;
        if (mSyncFileObserver != null) prevActivity = mSyncFileObserver.getActivity();
        if (prevActivity == null || prevActivity.isDestroyed()) {
            mSyncFileObserver = new SyncFileObserver(dir.getAbsolutePath());
            mSyncFileObserver.setActivity(activity);
        }
        SyncFileObserver.setBackupFilesDir(TermService.getHOME());
        File sfofile = new File(dir.getAbsolutePath(), mSyncFileObserverFile);
        mSyncFileObserver.restoreHashMap(sfofile);
        mSyncFileObserver.restoreStartWatching();
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

    protected static TermSession createTermSession(Context context, TermSettings settings, String initialCommand) throws IOException {
        GenericTermSession session = new ShellTermSession(settings, initialCommand);
        // XXX We should really be able to fetch this from within TermSession
        session.setProcessExitMessage(context.getString(R.string.process_exit_message));

        return session;
    }

    static public void setUninstallExtraContents(boolean uninstall) {
        mUninstall = uninstall;
    }

    static String getArch() {
        return TermService.getArch();
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
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("NewApi")
    public static String getOpenDocumentPath(Uri uri, Cursor cursor) {
        if (uri == null || cursor == null) return null;

        String displayName = null;
        try {
            int index;
            cursor.moveToFirst();
            index = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME);
            if (index != -1) displayName = cursor.getString(index);
        } catch (Exception e) {
            // do nothing
        }

        String path;
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
                path = "/" + path;
                String fname = new File(path).getName();
                if (displayName != null && !fname.equals(displayName)) {
                    path = path + "/" + displayName;
                }
                path = path.replaceAll("[:|]", "-");
                path = path.replaceAll("//+", "/");
            }
        }
        path = shortenPath(path);
        return path;
    }

    public static String shortenPath(String srcPath) {
        if (srcPath == null) return null;
        String HASH_ALGORITHM = "SHA-1";
        String HASH_FORMAT_STRING = "%040x";
        try {
            String fname = new File(srcPath).getName();
            if (fname.equals("")) throw new FileNotFoundException(srcPath);

            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = md.digest(srcPath.getBytes());
            String dir = String.format(HASH_FORMAT_STRING, new BigInteger(1, hash));
            dir = new File(dir).getPath();
            String head = srcPath.replaceAll("^/", "");
            head = head.replaceAll("/.*$", "");
            if (!head.equals("")) dir = new File(head, dir).getPath();
            String path = "/" + new File(dir, fname).getPath();
            path = path.replaceAll("//+", "/");
            return path;
        } catch (Exception e) {
            return srcPath;
        }
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isInternalPrivateStorageDocument(Uri uri) {
        return (BuildConfig.APPLICATION_ID + ".storage.documents").equals(uri.getAuthority());
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
        NetworkInfo info = null;
        if (cm != null) info = cm.getActiveNetworkInfo();
        if (info != null) {
            return info.isConnected();
        }
        return false;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        mSettings.readPrefs(sharedPreferences);
        setDrawerButtons();
        if (TermSettings.AMBIWIDTH_KEY.equals(s)) {
            TermVimInstaller.setAmbiWidthToVimrc(mSettings.getAmbiWidth());
        }
        if (s != null && (s.startsWith("function_key_label_m") || s.startsWith("function_key_cmd_m"))) {
            setFunctionKey();
        }

        if (TermSettings.FAST_CURSOR_MODE.equals(s)) {
            setFunctionKey();
        }

        if (TermSettings.BACKACTION_KEY.equals(s)) {
            setOnBackPressedCallbackEnabled(mSettings.getBackKeyAction() != TermSettings.BACK_KEY_DEFAULT);
        }

        if (TermSettings.CURSORSTYLE_KEY.equals(s)) {
            EmulatorView.setCursorHeight(mSettings.getCursorStyle());
            recreate();
        }

        if (TermSettings.THEME_KEY.equals(s)
                || TermSettings.COLOR_KEY.equals(s)
                || TermSettings.STATUSBAR_ICON_KEY.equals(s)) {
            recreate();
        }
    }

    private void setForceNormalInputModeToPhysicalKeyboard(EmulatorView view) {
        setForceNormalInputModeToPhysicalKeyboard(view, false);
    }

    private void setForceNormalInputModeToPhysicalKeyboard(EmulatorView view, boolean forceDefault) {
        if (view != null) {
            boolean forceNormal = mHaveFullHwKeyboard && mSettings.getForceNormalInputModeToPhysicalKeyboard();
            if (forceNormal) {
                view.setImeShortcutsAction(EmulatorView.SHORTCUTS_ACTION_50);
            } else if (forceDefault) {
                view.setImeShortcutsAction(mSettings.getImeDefaultInputtype());
            }
        }
    }

    private void onLastKey() {
        mLastKeyPress = System.currentTimeMillis();
        if (mKeepScreenEnableAuto) {
            boolean keepScreen = (getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
            if (!keepScreen) doToggleKeepScreen();
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Log.v(TermDebug.LOG_TAG, "onCreate");

        mPrivateAlias = new ComponentName(this, RemoteInterface.PRIVACT_ACTIVITY_ALIAS);

        if (icicle == null)
            onNewIntent(getIntent());

        mTerminalMode = TERMINAL_MODE_DISABLE;
        File terminalModeFile = new File(getFilesDir() + TERMINAL_MODE_FILE);
        if (terminalModeFile.exists()) {
            try (BufferedReader in = new BufferedReader(new FileReader(terminalModeFile))) {
                String line;
                if ((line = in.readLine()) != null) mTerminalMode = Integer.parseInt(line);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (!mAlreadyStarted) EmulatorView.setTextScale(1.0f);

        final SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSettings = new TermSettings(getResources(), mPrefs);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

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
        setFunctionKey();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mHaveFullHwKeyboard = checkHaveFullHwKeyboard(getResources().getConfiguration());
        setSoftInputMode(mHaveFullHwKeyboard);

        if (mFunctionBar == -1) mFunctionBar = mSettings.showFunctionBar() ? 1 : 0;
        if (mFunctionBar == 1) setFunctionBar(mFunctionBar);
        if (mOnelineTextBox == -1) mOnelineTextBox = mSettings.showOnelineTextBox() ? 1 : 0;
        initOnelineTextBox(mOnelineTextBox);

        setOnBackPressedCallback();
        setOnBackPressedCallbackEnabled(mSettings.getBackKeyAction() != TermSettings.BACK_KEY_DEFAULT);

        updatePrefs();
        setDrawerButtons();
        restoreSyncFileObserver(this);
        TermPreferences.setAppPickerList(this);
        if (TermService.TermServiceState < 1) {
            EmulatorView.setCursorHeight(mSettings.getCursorStyle());
            TermService.TermServiceState = 1;
        }
        mAlreadyStarted = true;
    }

    private void setOnBackPressedCallback() {
        if (mOnBackPressedCallback == null) {
            mOnBackPressedCallback = new OnBackPressedCallback(false) {
                @Override
                public void handleOnBackPressed() {
                    if (!backkey()) finish();
                }
            };
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (mOnBackInvokedCallback == null) {
                mOnBackInvokedCallback = () -> {
                    if (mOnBackPressedCallback != null && mOnBackPressedCallback.isEnabled()) {
                        mOnBackPressedCallback.handleOnBackPressed();
                    }
                };
            }
        } else {
            getOnBackPressedDispatcher().addCallback(this, mOnBackPressedCallback);
        }
    }

    private void setOnBackPressedCallbackEnabled(boolean enabled) {
        if (mOnBackPressedCallback != null) mOnBackPressedCallback.setEnabled(enabled);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OnBackInvokedDispatcher invokedDispatcher = getOnBackInvokedDispatcher();
            if (invokedDispatcher != null) {
                invokedDispatcher.unregisterOnBackInvokedCallback(mOnBackInvokedCallback);
                if (enabled) invokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, mOnBackInvokedCallback);
            }
        }
    }

    private static long getAvailableSize(String path) {
        long size = -1;

        if (path != null) {
            StatFs fs = new StatFs(path);
            long blockSize = fs.getBlockSizeLong();
            long availableBlockSize = fs.getAvailableBlocksLong();
            size = (blockSize * availableBlockSize);
        }
        return size;
    }

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

    private void setExtraButton() {
        Button button = findViewById(R.id.drawer_extra_button);
        int visibilty = View.VISIBLE;
        button.setVisibility(visibilty);
    }

    private void setDebugButton() {
        if (!BuildConfig.DEBUG) return;
        Button button = findViewById(R.id.drawer_debug_button);
        button.setVisibility(View.VISIBLE);

        if (mSettings.getColorTheme() % 2 == 0)
            button.setBackgroundResource(R.drawable.extra_button_dark);
    }

    private void setDrawerButtons() {
        PackageManager pm = this.getApplicationContext().getPackageManager();
        APP_FILER = TermPreferences.getFilerApplicationId();
        String defaultAppButton = getString(R.string.external_app_button);
        LinkedList<ExternalApp> mExternalApps = new LinkedList<>();
        mExternalApps.add(new ExternalApp(R.id.drawer_files_button, APP_FILER, getString(R.string.app_files), mSettings.getFilerAppButtonMode()));
        if (FLAVOR_VIM) {
            mExternalApps.add(new ExternalApp(R.id.drawer_app_button        , mSettings.getExternalAppId(), defaultAppButton               , mSettings.getExternalAppButtonMode()));
            mExternalApps.add(new ExternalApp(R.id.drawer_dropbox_button    , APP_DROPBOX                 , getString(R.string.dropbox)    , mSettings.getDropboxFilePicker()    ));
            mExternalApps.add(new ExternalApp(R.id.drawer_googledrive_button, APP_GOOGLEDRIVE             , getString(R.string.googledrive), mSettings.getGoogleDriveFilePicker()));
            mExternalApps.add(new ExternalApp(R.id.drawer_onedrive_button   , APP_ONEDRIVE                , getString(R.string.onedrive)   , mSettings.getOneDriveFilePicker()   ));
        }
        mFilePickerItems = new ArrayList<>();
        for (ExternalApp app : mExternalApps) {
            int visibility = (app.action > 0) ? View.VISIBLE : View.GONE;
            Button button = findViewById(app.button);
            button.setVisibility(visibility);
            if (visibility == View.VISIBLE) {
                try {
                    findViewById(app.button).setOnClickListener(v -> {
                        getDrawer().closeDrawers();
                        if (app.appId.equals(APP_FILER)) {
                            if (FLAVOR_VIM) {
                                final Runnable runFiler = () -> launchExternalApp(2, APP_FILER);
                                final String WARNING_ID_FILE_MANAGER = "file_manager_app_warning";
                                String message = getString(R.string.google_file_chooser_warning_message) + getString(R.string.google_filer_app_warning_message);
                                doWarningDialogRun(null, message, WARNING_ID_FILE_MANAGER, false, runFiler);
                            } else {
                                launchExternalApp(2, APP_FILER);
                            }
                        } else if (isAppInstalled(app.appId) || APP_DROPBOX.equals(app.appId) || APP_GOOGLEDRIVE.equals(app.appId) || APP_ONEDRIVE.equals(app.appId)) {
                            launchExternalApp(app.action, app.appId);
                        } else {
                            externalApp();
                        }
                    });
                    if (defaultAppButton.equals(app.label)) {
                        PackageInfo packageInfo = pm.getPackageInfo(app.appId, 0);
                        String label = packageInfo.applicationInfo.loadLabel(pm).toString();
                        button.setText(label);
                    } else {
                        button.setText(app.label);
                        if (!isAppInstalled(app.appId)) button.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    button.setText(defaultAppButton);
                    button.setVisibility(View.GONE);
                }
            }
            if ((isAppInstalled(app.appId) && app.action > 0)
                    && (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q || app.appId.equals(APP_FILER))) {
                mFilePickerItems.add(app.appId);
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            if (FLAVOR_VIM) mFilePickerItems.add(getString(R.string.delete_file));
        }
        Button button;
        if (FLAVOR_VIM) {
            button = findViewById(R.id.drawer_storage_button);
            button.setVisibility(View.VISIBLE);
        }
        button = findViewById(R.id.drawer_createfile_button);
        button.setVisibility(View.VISIBLE);
        mFilePickerItems.add(getString(R.string.clear_cache));
        mFilePickerItems.add(getString(R.string.create_symlinks));
        mFilePickerItems.add(getString(R.string.backup_restore));
        if (FLAVOR_VIM) mFilePickerItems.add(getString(R.string.menu_edit_vimrc));
        setExtraButton();
        findViewById(R.id.drawer_extra_button).setOnClickListener(v -> {
            getDrawer().closeDrawers();
            onExtraButtonClicked(v);
        });
        findViewById(R.id.drawer_preference_button).setOnClickListener(v -> {
            getDrawer().closeDrawers();
            doPreferences();
        });
        setDebugButton();
        findViewById(R.id.drawer_debug_button).setOnClickListener(v -> {
            getDrawer().closeDrawers();
            onDebugButtonClicked(v);
        });

        findViewById(R.id.drawer_storage_button).setOnClickListener(v -> {
            getDrawer().closeDrawers();
            filePicker();
        });
        findViewById(R.id.drawer_createfile_button).setOnClickListener(v -> {
            getDrawer().closeDrawers();
            storageMenu(true);
        });
        findViewById(R.id.drawer_clear_cache_button).setOnClickListener(v -> {
            getDrawer().closeDrawers();
            confirmClearCache();
        });
        findViewById(R.id.drawer_menu_button).setOnClickListener(v -> {
            getDrawer().closeDrawers();
            openOptionsMenu();
        });
        findViewById(R.id.drawer_quit_button).setOnClickListener(v -> {
            getDrawer().closeDrawers();
            if (mSettings.getInitialCommand().matches("(.|\n)*(^|\n)-?vim\\.app(.|\n)*")) {
                sendKeyStrings(AppCommand.QUIT, true);
            } else {
                confirmCloseWindow();
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

    private boolean isAppInstalled(String packageName) {
        if ("".equals(packageName)) return false;
        if (TermPreferences.getFilerApplicationId().equals(packageName)) return true;
        PackageManager packageManager = this.getApplicationContext().getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(packageName);
        return (intent != null);
    }

    private boolean intentMainActivity(String packageName) {
        if (packageName == null || packageName.equals("")) return false;
        try {
            PackageManager pm = this.getApplicationContext().getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                throw new NullPointerException();
            }
            startActivity(intent);
        } catch (Exception e) {
            if (packageName.equals(APP_FILER)) {
                if (launchDocumentsuiActivity()) return true;
            }
            alert(packageName + "\n" + getString(R.string.external_app_activity_error));
            return true;
        }
        return true;
    }

    private boolean launchDocumentsuiActivity() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setType("*/*");
        String[] activities = {
                "com.android.documentsui.files.FilesActivity",
                "com.android.documentsui.DocumentsActivity",
        };
        intent = addDocumentsuiClassName(this.getApplicationContext(), intent, activities);
        if (intent != null) {
            try {
                startActivity(intent);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        try {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setType("*/*");
            startActivity(intent);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    static public Intent addDocumentsuiClassName(Context context, Intent intent) {
        final String[] activities = {
                "com.android.documentsui.picker.PickActivity",
                "com.android.documentsui.files.FilesActivity",
                "com.android.documentsui.DocumentsActivity",
        };
        return addDocumentsuiClassName(context, intent, activities);
    }

    static public Intent addDocumentsuiClassName(Context context, Intent intent, String[] activities) {
        String[] packageNames = {
                "com.google.android.documentsui",
                "com.android.documentsui",
        };
        for (String packageName : packageNames) {
            for (String activity : activities) {
                try {
                    intent.setClassName(packageName, activity);
                    PackageManager pm = context.getPackageManager();
                    List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
                    if (apps.size() > 0) return intent;
                } catch (Exception e) {
                    // Do nothing
                }
            }
        }
        return null;
    }

    static public Intent getDocumentsuiIntent(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return intent;
        return addDocumentsuiClassName(context, intent);
    }

    private void externalApp() {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setMessage(R.string.external_app_id_error);
        bld.setPositiveButton(android.R.string.ok, (dialog, id) -> {
            dialog.dismiss();
            doPreferences();
        });
        bld.create().show();
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
                alert(getString(R.string.activity_not_found));
                return false;
            }
        }
        return true;
    }

    private void confirmClearCache() {
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.confirm_clear_cache_message);
        final Runnable clearCache = () -> {
            if (mSyncFileObserver != null) mSyncFileObserver.clearCache();
            if (mTermService != null) mTermService.clearTMPDIR();
            String cacheDir = TermService.getCACHE_DIR();
            TermVimInstaller.deleteFileOrFolder(new File(cacheDir, "tmp"));
            TermVimInstaller.deleteFileOrFolder(new File(cacheDir, "vim"));
            new File(cacheDir, "tmp").mkdirs();
            new File(cacheDir, "vim").mkdirs();
        };
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            mHandler.post(clearCache);
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private DrawerLayout getDrawer() {
        return findViewById(R.id.drawer_layout);
    }

    @SuppressLint("NewApi")
    private void permissionCheck() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        manageNotificationPermission();
        manageExternalStoragePermission();
    }

    private void manageNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (!notificationManager.areNotificationsEnabled()) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            }
        }
    }

    private void manageExternalStoragePermission() {
        if (SCOPED_STORAGE) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) return;
            String key = "manage_storage_permission";
            boolean warning = getPrefBoolean(Term.this, key, true);
            if (!warning) return;
            Runnable whenDone = () -> {
                try {
                    Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
                    startActivity(intent);
                } catch (Exception e){
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            };
            doWarningDialogRun(getString(R.string.manage_external_storage_permission), getString(R.string.manage_external_storage_permission_summary), key, false, whenDone);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
            }
        }
    }

    @Override
    @SuppressLint("NewApi")
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_NOTIFICATIONS:
                for (int i = 0; i < permissions.length; i++) {
                    if (permissions[i].equals(Manifest.permission.POST_NOTIFICATIONS)) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            runOnUiThread(() -> {
                                AlertDialog.Builder bld = new AlertDialog.Builder(Term.this);
                                bld.setIcon(android.R.drawable.ic_dialog_alert);
                                bld.setTitle(getString(R.string.title_notifications_permission));
                                bld.setMessage(getString(R.string.permission_granted_suggestion));
                                bld.setPositiveButton(android.R.string.ok, (dialog, id) -> dialog.dismiss());
                                AlertDialog dialog = bld.create();
                                dialog.show();
                            });
                        }
                    }
                }
                break;
            case REQUEST_STORAGE:
                for (int i = 0; i < permissions.length; i++) {
                    if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            TermVimInstaller.setupStorageSymlinks(this);
                            runOnUiThread(() -> {
                                AlertDialog.Builder bld = new AlertDialog.Builder(Term.this);
                                bld.setIcon(android.R.drawable.ic_dialog_alert);
                                bld.setMessage(R.string.permission_granted_suggestion);
                                bld.setPositiveButton(android.R.string.ok, (dialog, id) -> dialog.dismiss());
                                AlertDialog dialog = bld.create();
                                dialog.show();
                            });
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
                            String permission = permissions[i];
                            if (shouldShowRequestPermissionRationale(permission)) {
                                showSnackbar(getString(R.string.storage_permission_error));
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

    @Override
    protected void onStart() {
        super.onStart();
        String nativeLibraryDir = this.getApplicationContext().getApplicationInfo().nativeLibraryDir;
        if (new File(nativeLibraryDir + "/libjackpal-termexec2.so").exists()) {
            doOnStart();
        } else {
            final String title = "Native library not found";
            String message = "The possible causes are:\n\n";
            message += " - Installed using an apk file extracted from another device.\n";
            message += " - You are using a custom ROM and have different native library settings applied than what the Play Store expects.\n";
            message += "\n\n";
            message += "If you think your device is okay, tap \"Continue\" button.\nIf the problem persists, please contact us.";

            final String warningKey = "native_library_check";
            boolean warning = getPrefBoolean(Term.this, warningKey, true);
            if (!warning) {
                doOnStart();
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setTitle(title);
            builder.setMessage(message);
            LayoutInflater inflater = LayoutInflater.from(this);
            View view = inflater.inflate(R.layout.alert_checkbox, null);
            builder.setView(view);
            final CheckBox cb = view.findViewById(R.id.dont_show_again);
            cb.setChecked(false);
            builder.setPositiveButton(getString(R.string.button_continue), (d, m) -> {
                if (cb.isChecked()) {
                    setPrefBoolean(Term.this, warningKey, false);
                }
                doOnStart();
            });
            builder.setNeutralButton(R.string.quit, (dialog, id) -> doExitShell());
            AlertDialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.requestFocus();
        }
    }

    private void doOnStart() {
        if (!bindTermService(TSIntent, mTSConnection, BIND_AUTO_CREATE)) {
            final String MESSAGE = getString(R.string.faild_to_bind_to_termservice);
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            bld.setIcon(android.R.drawable.ic_dialog_alert);
            bld.setTitle(MESSAGE);
            bld.setMessage(R.string.please_restart);
            bld.setPositiveButton(R.string.quit, (dialog, id) -> {
                dialog.dismiss();
                throw new IllegalStateException(MESSAGE);
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

    private void setupStorageSymlinks() {
        if (TermVimInstaller.setupStorageSymlinks(this)) {
            alert("Symbolic links are created.");
        } else {
            alert("ERORR : Symbolic links are not created.");
        }
    }

    private void editVimrc() {
        final Runnable editVimrc = this::chooseEditVimFiles;
        String message = getString(R.string.edit_vimrc_message);
        // message += getString(R.string.edit_vimrc_message_symboliclink);
        // message += getString(R.string.edit_vimrc_message_file_manager);
        // message += getString(R.string.edit_vimrc_message_backup_restore);
        // message += getString(R.string.edit_vimrc_message_change_home);
        doWarningDialogRun(getString(R.string.menu_edit_vimrc), message, "menu_edit_vimrc", false, editVimrc);
    }

    private void backupFromHome() {
        ASFUtils.documentTreePicker(this, REQUEST_COPY_DOCUMENT_TREE_BACKUP_HOME, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    private void restoreToHome() {
        ASFUtils.documentTreePicker(this, REQUEST_COPY_DOCUMENT_TREE_RESTORE_TO_HOME, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    private void backupAndRestoreHome() {
        ArrayList<String> itemList = new ArrayList<>();
        itemList.add(getString(R.string.backup_home_directory));
        itemList.add(getString(R.string.restore_home_directory));
        if (SCOPED_STORAGE) {
            itemList.add(getString(R.string.backup_home_to_appextfiles));
            itemList.add(getString(R.string.restore_home_from_appextfiles));
        }
        final String[] items = itemList.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.backup_restore))
                .setItems(items, (dialog, which) -> {
                    final AlertDialog.Builder b = new AlertDialog.Builder(Term.this);
                    b.setIcon(android.R.drawable.ic_dialog_info);
                    b.setMessage(items[which]);
                    b.setPositiveButton(android.R.string.ok, (dialog1, id) -> {
                        dialog1.dismiss();
                        if (getString(R.string.backup_home_directory).equals(items[which])) {
                            backupFromHome();
                        } else if (getString(R.string.restore_home_directory).equals(items[which])) {
                            restoreToHome();
                        } else if (getString(R.string.backup_home_to_appextfiles).equals(items[which])) {
                            showSnackbar(getString(R.string.message_please_wait));
                            sendKeyStrings(":echo system(\"cphome backup\")" + "\r", true);
                        } else if (getString(R.string.restore_home_from_appextfiles).equals(items[which])) {
                            showSnackbar(getString(R.string.message_please_wait));
                            sendKeyStrings(":echo system(\"cphome restore\")" + "\r", true);
                        }
                    });
                    b.setNegativeButton(android.R.string.no, (dialog12, which1) -> {
                        dialog12.dismiss();
                        backupAndRestoreHome();
                    });
                    b.show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void populateViewFlipper() {
        if (mTermService != null) {
            mTermSessions = mTermService.getSessions();

            if (mTermSessions.size() == 0) {
                try {
                    mTermSessions.add(createTermSession());
                } catch (IOException e) {
                    showSnackbar("Failed to start terminal session");
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

    private void destroyAppWarning() {
        if (mUninstall) {
            doExitShell();
            return;
        }
        boolean first = TermVimInstaller.ScopedStorageWarning;
        TermVimInstaller.ScopedStorageWarning = false;

        String title = null;
        String message = null;
        String key = null;
        boolean doNotShowAgain = false;
        boolean warning;

        if (SCOPED_STORAGE) {
            key = "scoped_storage_warning_backup";
            warning = getPrefBoolean(Term.this, key, true);
            if (first || (warning && mRandom.nextInt(100) == 1)) {
                title = getString(R.string.scoped_storage_warning_title);
                message = getString(R.string.scoped_storage_uninstall_warning_message);
                message += "\n - " + TermService.getAPPBASE();
                message += "\n - (Internal / SD Card)/Android/data/" + BuildConfig.APPLICATION_ID;
            }
        }

        if (message == null) {
            key = "low_storage_check_" + BuildConfig.VERSION_CODE;
            warning = getPrefBoolean(Term.this, key, true);
            try {
                if (warning) {
                    final long LOW_STORAGE_WARNING_SIZE = (long) (0.4 * 1024 * 1024 * 1024);
                    long mStorageAvailableSize = getAvailableSize(Environment.getDataDirectory().getPath());
                    if (mStorageAvailableSize < LOW_STORAGE_WARNING_SIZE) {
                        String manufacturer = Build.MANUFACTURER;
                        String model = Build.MODEL;
                        title = getString(R.string.low_strage_warning_title);
                        message = getString(R.string.low_strage_warning_message);
                        doNotShowAgain = true;
                    }
                }
            } catch (Exception e) {
                // Do nothing
            }
        }

        if (message == null) {
            doExitShell();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setTitle(title);
        builder.setMessage(message);
        if (!first) {
            LayoutInflater inflater = LayoutInflater.from(this);
            View view = inflater.inflate(R.layout.alert_checkbox, null);
            builder.setView(view);
            final CheckBox cb = view.findViewById(R.id.dont_show_again);
            final String warningKey = key;
            cb.setChecked(doNotShowAgain);
            builder.setPositiveButton(android.R.string.yes, (d, m) -> {
                if (cb.isChecked()) {
                    setPrefBoolean(Term.this, warningKey, false);
                }
                doExitShell();
            });
        } else {
            builder.setPositiveButton(android.R.string.yes, (d, m) -> doExitShell());
        }
        if (key.equals("scoped_storage_warning_backup") && isAppInstalled(APP_FILER)) {
            builder.setNeutralButton(getString(R.string.app_files), (d, m) -> {
                intentMainActivity(APP_FILER);
                doExitShell();
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
            mFirstInputtype = true;
            mFunctionBar = -1;
            mOrientation = -1;
            if (FLAVOR_VIM && mSyncFileObserver != null) {
                mSyncFileObserver.clearOldCache();
                saveSyncFileObserver();
            }
            mKeepScreenHandler.removeCallbacksAndMessages(null);
            try {
                stopService(TSIntent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        new File(getClipboardFile()).delete();
        File cacheDir = new File(getIntentCacheDir());
        shell("rm -rf " + cacheDir.getAbsolutePath());
        shell("rm " + TermService.getAPPFILES() + "/proot.err");
        mTermService = null;
        mTSConnection = null;
        super.onDestroy();
    }

    @SuppressLint("NewApi")
    private void restart() {
        recreate();
    }

    private TermSession createTermSession() throws IOException {
        TermSettings settings = mSettings;
        TermSession session = createTermSession(this, settings, getInitialCommand());
        session.setFinishCallback(mTermService);
        return session;
    }

    private String getInitialCommand() {
        String cmd = mSettings.getInitialCommand();
        cmd = mTermService.getInitialCommand(cmd, (mFirst && mTermService.getSessions().size() == 0));
        boolean doInstall = TermVimInstaller.doInstallVim;
        if (FLAVOR_TERMINAL) {
            doInstall = !TermVimInstaller.getTermInstallStatus(this);
        }
        if (doInstall) {
            StringBuilder _postCmd = new StringBuilder();
            String[] list = cmd.split("\n");
            StringBuilder preCmd = new StringBuilder();
            for (String str : list) {
                if (str.matches("^(bash|-?vim.app).*")) {
                    _postCmd.append(str).append("\n");
                } else {
                    preCmd.append(str).append("\n");
                }
            }
            final String postCmd = _postCmd.toString();
            cmd = preCmd.toString();
            TermVimInstaller.installVim(Term.this, () -> {
                if (getCurrentTermSession() != null) {
                    sendKeyStrings(postCmd, false);
                } else {
                    ShellTermSession.setPostCmd(postCmd);
                }
                permissionCheck();
                if (FLAVOR_VIM) openDrawerAfterInstall();
            });
        } else {
            permissionCheck();
        }
        mFirst = false;
        return cmd;
    }

    static public void showProgressRing(final DrawerLayout layout, final ProgressBar progressBar) {
        try {
            if (layout == null || progressBar == null) return;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 0);
            progressBar.setLayoutParams(params);
            layout.addView(progressBar);
            progressBar.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            // Do nothing
        }
    }

    static public void dismissProgressRing(final DrawerLayout layout, final ProgressBar progressBar) {
        try {
            if (layout != null) layout.removeView(progressBar);
        } catch (Exception e) {
            // Do nothing
        }
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
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.alert_checkbox, null);
        builder.setView(view);
        final CheckBox cb = view.findViewById(R.id.dont_show_again);
        final String warningKey = key;
        cb.setChecked(dontShowAgain);
        builder.setPositiveButton(getString(R.string.button_continue), (d, m) -> {
            if (cb.isChecked()) {
                setPrefBoolean(Term.this, warningKey, false);
            }
            if (whenDone != null) whenDone.run();
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positive.requestFocus();
    }

    private TermView createEmulatorView(TermSession session) {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        TermView emulatorView = new TermView(this, session, metrics);

        emulatorView.setExtGestureListener(new EmulatorViewGestureListener(emulatorView));
        emulatorView.setDoubleTapListener(new EmulatorViewDoubleTapListener(emulatorView));
        emulatorView.setScaleGestureListener(new EmulatorViewScaleGestureListener(emulatorView));
        emulatorView.setOnKeyListener(mKeyListener);
        registerForContextMenu(emulatorView);

        if (mFirstInputtype) {
            String defime = Settings.Secure.getString(this.getApplicationContext().getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            EmulatorView.setDefaultIME(defime);
            EmulatorView.setIMEInputTypeDefault(mSettings.getImeDefaultInputtype());
            emulatorView.setImeShortcutsAction(mSettings.getImeDefaultInputtype());
            mFirstInputtype = false;
        }
        if (mHaveFullHwKeyboard) setForceNormalInputModeToPhysicalKeyboard(emulatorView);
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
        mVolumeAsCursor = mSettings.getVolumeAsCursor();

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
        EmulatorView.setCursorBlinkDefault(mSettings.getCursorBlink());
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
        int o = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
        if (orientation == 0) {
            o = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        } else if (orientation == 1) {
            o = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        } else if (orientation == 2) {
            o = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        if (o != ActivityInfo.SCREEN_ORIENTATION_NOSENSOR) setRequestedOrientation(o);
        mKeepScreenEnableAuto = mSettings.getKeepScreenAtStartup();
    }

    private int getOrientation() {
        if (mOrientation == -1) return mSettings.getScreenOrientation();
        return mOrientation;
    }

    private void doScreenMenu() {
        String screenLockItem;
        final boolean keepScreen = ((getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
        if (keepScreen) {
            screenLockItem = getString(R.string.disable_keepscreen);
        } else {
            screenLockItem = getString(R.string.enable_keepscreen);
        }
        final String[] items = {getString(R.string.copy_share_current_screen), getString(R.string.copy_share_screen_buffer), screenLockItem, getString(R.string.dialog_title_orientation_preference), getString(R.string.reset)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.screen))
                .setItems(items, (dialog, which) -> {
                    if (getString(R.string.dialog_title_orientation_preference).equals(items[which])) {
                        setCurrentOrientation();
                    } else if (getString(R.string.copy_share_current_screen).equals(items[which])) {
                        String strings = Objects.requireNonNull(getCurrentEmulatorView()).getTranscriptCurrentText();
                        showTextInWebview("html_text", strings);
                    } else if (getString(R.string.copy_share_screen_buffer).equals(items[which])) {
                        doHideSoftKeyboard();
                        String strings = Objects.requireNonNull(getCurrentEmulatorView()).getTranscriptText().trim();
                        showTextInWebview("html_log", strings);
                    } else if ((getString(R.string.disable_keepscreen).equals(items[which])) || (getString(R.string.enable_keepscreen).equals(items[which]))) {
                        if (keepScreen) mKeepScreenEnableAuto = false;
                        doToggleKeepScreen();
                        if (!keepScreen) mKeepScreenEnableAuto = true;
                    } else if (getString(R.string.reset).equals(items[which])) {
                        doResetTerminal();
                        updatePrefs();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doWindowMenu() {
        final String[] items = {getString(R.string.new_window), getString(R.string.close_window)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.menu_window))
                .setItems(items, (dialog, which) -> {
                    if (getString(R.string.new_window).equals(items[which])) {
                        doCreateNewWindow();
                    } else if (getString(R.string.close_window).equals(items[which])) {
                        confirmCloseWindow();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setCurrentOrientation() {
        final String[] items = getResources().getStringArray(R.array.entries_orientation_preference);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_orientation_preference))
                .setItems(items, (dialog, which) -> {
                    mOrientation = which;
                    updatePrefs();
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
        value = mPrefs.getBoolean("CtrlSpaceToShell", false);
        v.setPreIMEShortcut("CtrlSpaceToShell", value);
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
        super.onPause();

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
    }

    @Override
    public void onResume() {
        super.onResume();

        EmulatorView v = (EmulatorView) mViewFlipper.getCurrentView();
        if (v != null) {
            v.updateSize(true);
        }
        if (mSyncFileObserver != null) {
            mSyncFileObserver.setActivity(this);
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

        super.onStop();
    }

    private boolean checkHaveFullHwKeyboard(Configuration c) {
        boolean haveFullHwKeyboard = (c.keyboard == Configuration.KEYBOARD_QWERTY) &&
                (c.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO);
        if (mPrevHaveFullHwKeyboard == -1 || (haveFullHwKeyboard != (mPrevHaveFullHwKeyboard == 1))) {
            mHideFunctionBar = haveFullHwKeyboard && mSettings.getAutoHideFunctionbar();
            if (haveFullHwKeyboard) doWarningHwKeyboard();
        }
        mPrevHaveFullHwKeyboard = haveFullHwKeyboard ? 1 : 0;
        return haveFullHwKeyboard;
    }

    private void doWarningHwKeyboard() {
        if (!FLAVOR_VIM) return;
        doWarningDialog(null, getString(R.string.keyboard_warning), "do_warning_physical_keyboard", false);
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
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
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
        if (!FLAVOR_VIM) menu.removeItem(R.id.menu_share_text);
        if (!FLAVOR_VIM) menu.removeItem(R.id.menu_edit_vimrc);
        if (!FLAVOR_VIM) menu.removeItem(R.id.menu_reload);
        if (!FLAVOR_VIM) menu.removeItem(R.id.menu_tutorial);
        if (!FLAVOR_VIM) menu.removeItem(R.id.menu_drawer);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item;
        boolean visibility;
        item = menu.findItem(R.id.menu_toggle_soft_keyboard);
        visibility = (mSettings.getBackKeyAction() != TermSettings.BACK_KEY_TOGGLE_IME);
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
        } else if (id == R.id.menu_screen) {
            doScreenMenu();
        } else if (id == R.id.menu_window) {
            doWindowMenu();
        } else if (id == R.id.menu_share_text) {
            shareIntentTextDialog();
        } else if (id == R.id.menu_toggle_soft_keyboard) {
            doToggleSoftKeyboard();
        } else if (id == R.id.menu_toggle_function_bar) {
            setNavigationBar(2);
        } else if (id == R.id.menu_tutorial) {
            sendKeyStrings(":Vimtutor\r", true);
        } else if (id == R.id.menu_disable_keepscreen) {
            mKeepScreenEnableAuto = false;
            doToggleKeepScreen();
        } else if (id == R.id.menu_edit_vimrc) {
            editVimrc();
        } else if (id == R.id.menu_toggle_text_box) {
            setEditTextView(2);
        } else if (id == R.id.menu_drawer) {
            storageMenu(false);
        } else if (id == R.id.menu_reload) {
            fileReload();
        } else if (id == R.id.action_help) {
            String url = getString(R.string.help_url);
            try {
                Intent openHelp = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(openHelp);
            } catch (Exception e) {
                Intent intent = new Intent(this, WebViewActivity.class);
                intent.putExtra("url", url);
                WebViewActivity.setFontSize(new PrefValue(this).getInt(WEBVIEW_FONT_SIZE, WEBVIEW_DEFAULT_FONT_SIZE));
                startActivityForResult(intent, REQUEST_WEBVIEW_ACTIVITY);
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

    private boolean doSendActionBarFKey(EmulatorView view, int key, String cmd) {
        if ("".equals(cmd)) return doSendActionBarKey(view, key);
        cmd = cmd.replaceAll("\n", "\r");
        cmd = cmd.replaceAll("<CR>$", "\r");
        sendKeyStrings(cmd, false);
        return true;
    }

    private static final int KEY_ACTION_0    = 0;
    private static final int KEY_ACTION_999  = 999;
    private static final int KEY_ACTION_1002 = 1002;
    private static final int KEY_ACTION_1006 = 1006;
    private static final int KEY_ACTION_1247 = 1247;
    private static final int KEY_ACTION_1249 = 1249;
    private static final int KEY_ACTION_1250 = 1250;
    private static final int KEY_ACTION_1251 = 1251;
    private static final int KEY_ACTION_1252 = 1252;
    private static final int KEY_ACTION_1253 = 1253;
    private static final int KEY_ACTION_1254 = 1254;
    private static final int KEY_ACTION_1255 = 1255;
    private static final int KEY_ACTION_1256 = 1256;
    private static final int KEY_ACTION_1257 = 1257;
    private static final int KEY_ACTION_1260 = 1260;
    private static final int KEY_ACTION_1261 = 1261;
    private static final int KEY_ACTION_1300 = 1300;
    private static final int KEY_ACTION_1351 = 1351;
    private static final int KEY_ACTION_1354 = 1354;
    private static final int KEY_ACTION_1355 = 1355;
    private static final int KEY_ACTION_1356 = 1356;
    private static final int KEY_ACTION_1357 = 1357;
    private static final int KEY_ACTION_1358 = 1358;
    private static final int KEY_ACTION_1360 = 1360;
    private static final int KEY_ACTION_1361 = 1361;
    private static final int KEY_ACTION_1362 = 1362;
    private static final int KEY_ACTION_1363 = 1363;
    private static final int KEY_ACTION_1364 = 1364;
    private static final int KEY_ACTION_1365 = 1365;

    private boolean doSendActionBarKey(EmulatorView view, int key) {
        if (view == null) return false;
        if (key == KEY_ACTION_999) {
            // do nothing
        } else if (key == KEY_ACTION_1002) {
            doToggleSoftKeyboard();
        } else if (key == KEY_ACTION_1006) {
            EmulatorView.setTextScale(1.0f);
            view.setFontSize();
        } else if (key == KEY_ACTION_1249) {
            doPaste();
        } else if (key == KEY_ACTION_1250) {
            doCreateNewWindow();
        } else if (key == KEY_ACTION_1251) {
            if (mTermSessions != null) {
                if (mSettings.getInitialCommand().matches("(.|\n)*(^|\n)-?vim\\.app(.|\n)*")) {
                    sendKeyStrings(AppCommand.QUIT, true);
                } else {
                    confirmCloseWindow();
                }
            }
        } else if (key == KEY_ACTION_1252) {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        } else if (key == KEY_ACTION_1253) {
            if (mSettings.getInitialCommand().matches("(.|\n)*(^|\n)-?vim\\.app(.|\n)*")) {
                sendKeyStrings(AppCommand.QUIT, true);
            } else {
                confirmCloseWindow();
            }
        } else if (key == KEY_ACTION_1254) {
            view.sendFnKeyCode();
        } else if (key == KeycodeConstants.KEYCODE_ALT_LEFT) {
            view.sendAltKeyCode();
        } else if (key == KeycodeConstants.KEYCODE_CTRL_LEFT) {
            view.sendControlKeyCode();
        } else if (key == KEY_ACTION_1247) {
            sendKeyStrings(":", false);
        } else if (key == KEY_ACTION_1255) {
            setFunctionBar(2);
        } else if (key == KEY_ACTION_1256) {
            setNavigationBar(2);
        } else if (key == KEY_ACTION_1257) {
            VoiceInput.start(Term.this, REQUEST_VOICE_INPUT);
        } else if (key == KEY_ACTION_1260) {
            int action = mSettings.getImeShortcutsAction();
            if (action == KEY_ACTION_0) {
                doToggleSoftKeyboard();
            } else if (action == KEY_ACTION_1261) {
                doEditTextFocusAction();
            } else {
                setEditTextAltCmd();
                view.doImeShortcutsAction();
            }
            return true;
        } else if (key == KEY_ACTION_1261) {
            doEditTextFocusAction();
        } else if (key == KEY_ACTION_1360 || (key >= KEY_ACTION_1351 && key <= KEY_ACTION_1354)) {
            if (setEditTextAltCmd()) return true;
            view.doImeShortcutsAction(key - KEY_ACTION_1300);
        } else if (key == KEY_ACTION_1361) {
            keyEventSender(KEYEVENT_SENDER_SHIFT_SPACE);
        } else if (key == KEY_ACTION_1362) {
            keyEventSender(KEYEVENT_SENDER_ALT_SPACE);
        } else if (key == KEY_ACTION_1363) {
            mInvertCursorDirection = !mInvertCursorDirection;
            mDefaultInvertCursorDirection = mInvertCursorDirection;
            setCursorDirectionLabel();
        } else if (key == KEY_ACTION_1364) {
            if (getInvertCursorDirection() != getDefaultInvertCursorDirection()) {
                mInvertCursorDirection = getDefaultInvertCursorDirection();
                setCursorDirectionLabel();
            }
        } else if (key == KEY_ACTION_1365) {
            sendVimIminsertKey();
        } else if (key == KEY_ACTION_1355) {
            toggleDrawer();
        } else if (key == KEY_ACTION_1356) {
            sendKeyStrings(":tabnew\r", true);
            openDrawer();
        } else if (key == KEY_ACTION_1357) {
            setFunctionBar(2);
        } else if (key == KEY_ACTION_1358) {
            setCurrentOrientation();
        } else if (key == KeycodeConstants.KEYCODE_ESCAPE) {
            view.restartInputGoogleIme();
            if (onelineTextBoxEsc()) return true;
            dispatchKeyEventUD(key);
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
            dispatchKeyEventUD(key);
            view.setControlKeyState(state);
        }
        return true;
    }

    void sendVimIminsertKey() {
        EmulatorView view = getCurrentEmulatorView();
        if (view == null) return;
        // send <C-^>
        TermSession session = getCurrentTermSession();
        if (session != null) session.write(30);
    }

    private boolean getInvertCursorDirection() {
        return mInvertCursorDirection;
    }

    private boolean getDefaultInvertCursorDirection() {
        return mDefaultInvertCursorDirection;
    }

    private void setCursorDirectionLabel() {
        if (!getInvertCursorDirection()) {
            ((Button) findViewById(FunctionKey.getResourceId("functionbar_right"))).setText(FUNCTIONBAR_RIGHT);
            ((Button) findViewById(FunctionKey.getResourceId("functionbar_left"))).setText(FUNCTIONBAR_LEFT);
            ((Button) findViewById(FunctionKey.getResourceId("functionbar_up"))).setText(FUNCTIONBAR_UP);
            ((Button) findViewById(FunctionKey.getResourceId("functionbar_down"))).setText(FUNCTIONBAR_DOWN);
            ((Button) findViewById(FunctionKey.getResourceId("navigationbar_right"))).setText(FUNCTIONBAR_RIGHT);
            ((Button) findViewById(FunctionKey.getResourceId("navigationbar_left"))).setText(FUNCTIONBAR_LEFT);
            ((Button) findViewById(FunctionKey.getResourceId("navigationbar_up"))).setText(FUNCTIONBAR_UP);
            ((Button) findViewById(FunctionKey.getResourceId("navigationbar_down"))).setText(FUNCTIONBAR_DOWN);
        } else {
            ((Button) findViewById(FunctionKey.getResourceId("functionbar_right"))).setText(FUNCTIONBAR_DOWN);
            ((Button) findViewById(FunctionKey.getResourceId("functionbar_left"))).setText(FUNCTIONBAR_UP);
            ((Button) findViewById(FunctionKey.getResourceId("functionbar_up"))).setText(FUNCTIONBAR_LEFT);
            ((Button) findViewById(FunctionKey.getResourceId("functionbar_down"))).setText(FUNCTIONBAR_RIGHT);
            ((Button) findViewById(FunctionKey.getResourceId("navigationbar_right"))).setText(FUNCTIONBAR_DOWN);
            ((Button) findViewById(FunctionKey.getResourceId("navigationbar_left"))).setText(FUNCTIONBAR_UP);
            ((Button) findViewById(FunctionKey.getResourceId("navigationbar_up"))).setText(FUNCTIONBAR_LEFT);
            ((Button) findViewById(FunctionKey.getResourceId("navigationbar_down"))).setText(FUNCTIONBAR_RIGHT);
        }
    }

    private void openDrawerAfterInstall() {
        runOnUiThread(this::openDrawer);
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
            doWarningDialog(null, getString(R.string.switch_windows_warning), "switch_window", false);
        } catch (IOException e) {
            String mes = "Failed to create a session";
            showSnackbar(mes);
        }
    }

    private void confirmCloseWindow() {
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.confirm_window_close_message);
        final Runnable closeWindow = this::doCloseWindow;
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            mHandler.post(closeWindow);
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
        try {
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
            if (apps.size() < 1) {
                alert(getString(R.string.storage_intent_error));
                return false;
            }
        } catch (Exception e) {
            alert(getString(R.string.storage_intent_error));
            return false;
        }
        return true;
    }

    private void filePicker() {
        final String WARNING_ID_FILE_PICKER = "google_storage_filer";
        final String mruCommand = mSettings.getMRUCommand();
        final LinkedList<SyncFileObserverMru> list = mSyncFileObserver.getMRU();
        final Runnable runFiler = this::doFilePicker;

        ArrayList<String> itemArray = new ArrayList<>();
        itemArray.add(getString(R.string.use_file_chooser));
        String mru = mruCommand.equals("MRU") ? getString(R.string.use_mru_cache) : getString(R.string.use_mru);
        boolean hasMruFile = !((mruCommand.equals("") || list == null || list.size() == 0));
        if (FLAVOR_VIM && hasMruFile) itemArray.add(mru);
        if (FLAVOR_VIM && hasMruFile && mruCommand.equals("MRU")) itemArray.add(getString(R.string.use_clear_mru_cache));
        if (FLAVOR_VIM) itemArray.add(getString(R.string.create_file));
        final String[] items = itemArray.toArray(new String[0]);
        new AlertDialog.Builder(this).setItems(items, (dialog, which) -> {
            String item = items[which];
            if (item.equals(getString(R.string.use_file_chooser))) {
                doWarningDialogRun(null, getString(R.string.google_file_chooser_warning_message), WARNING_ID_FILE_PICKER, false, runFiler);
            } else if (item.equals(getString(R.string.use_mru))) {
                sendKeyStrings(mruCommand + "\r", true);
            } else if (item.equals(getString(R.string.use_mru_cache))) {
                chooseExternalFileMru();
            } else if (item.equals(getString(R.string.use_clear_mru_cache))) {
                confirmClearCache();
            } else if (item.equals(getString(R.string.create_file))) {
                fileCreate();
            }
        }).setNegativeButton(android.R.string.cancel, null)
                .setTitle(getString(R.string.file_chooser))
                .show();
    }

    private void doFilePicker() {
        if (SCOPED_STORAGE || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intentFilePicker();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_FILE_PICKER);
        } else {
            intentFilePicker();
        }
    }

    private void chooseExternalFileMru() {
        final LinkedList<SyncFileObserverMru> mruList = mSyncFileObserver.getMRU();
        final LinkedList<SyncFileObserverMru> list = new LinkedList<>();
        int MRU_FILES = 8;
        for (SyncFileObserverMru mru : mruList) {
            list.add(mru);
            if (list.size() == MRU_FILES) break;
        }
        if (MRU_FILES > list.size()) MRU_FILES = list.size();
        if (MRU_FILES == 0) {
            filePicker();
            return;
        }
        final String[] items = new String[MRU_FILES];
        for (int i = 0; i < MRU_FILES; i++) {
            String item = list.get(i).getPath();
            item = new File(item).getName();
            items[i] = item;
        }
        new AlertDialog.Builder(this).setItems(items, (dialog, which) -> {
            String item = items[which];
            if (item == null) {
                // do nothing
            } else {
                if (mSyncFileObserver != null) {
                    Uri uri = list.get(which).getUri();
                    String path = list.get(which).getPath();
                    if (mSyncFileObserver.putUriAndLoad(uri, path)) {
                        doShellIntentCommand(path, mSettings.getIntentCommand());
                        return;
                    }
                }
                final AlertDialog.Builder b = new AlertDialog.Builder(Term.this);
                b.setIcon(android.R.drawable.ic_dialog_alert);
                String message = getString(R.string.storage_url_error);
                message += "\n\n" + SyncFileObserver.ErrorState;
                b.setMessage(message);
                b.setPositiveButton(getString(android.R.string.ok), (dialog1, id) -> {
                    SyncFileObserverMru mru = list.get(which);
                    mSyncFileObserver.remove(mru);
                    filePicker();
                });
                b.setNegativeButton(android.R.string.no, (dialogInterface, i) -> chooseExternalFileMru());
                b.show();
            }
        }).setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> filePicker())
                .setTitle(getString(R.string.use_mru_cache))
                .show();
    }

    private void doShellIntentCommand(String path, String cmd) {
        if (path == null || path.equals("")) return;
        if (cmd == null) cmd = "";
        String intentCommand = cmd;
        if (FLAVOR_VIM) {
            if (intentCommand.equals("")) intentCommand = ":e ";
        } else {
            if (intentCommand.equals("")) {
                intentCommand = new File(TermService.getAPPFILES() + "/usr/bin/bash").canExecute() ? "bash" : "sh";
            }
        }
        path = path.replaceAll(SHELL_ESCAPE, "\\\\$1");
        path = intentCommand + " " + path + "\r";
        boolean CMD_ESC = intentCommand.startsWith(":");
        sendKeyStrings(path, CMD_ESC);
    }

    private void documentTreePicker(int requestCode, int flags) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent = getDocumentsuiIntent(this.getApplicationContext(), intent);
        intent.addFlags(flags);
        doStartActivityForResult(intent, requestCode);
    }

    private void doStartActivityForResult(Intent intent, int requestCode) {
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        intent.putExtra("android.content.extra.FANCY", true);
        intent.putExtra("android.content.extra.SHOW_FILESIZE", true);
        startActivityForResult(intent, requestCode);
    }

    public void intentFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        if (mSettings.getUseBuiltInFilePicker()) intent = getDocumentsuiIntent(this.getApplicationContext(), intent);
        if (checkImplicitIntent(this, intent))
            doStartActivityForResult(intent, REQUEST_FILE_PICKER);
    }

    public void requestDocumentTreeWritePermission() {
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_info);
        b.setMessage(getString(R.string.open_document_tree_summary));
        final AppCompatActivity activity = this;
        b.setPositiveButton(android.R.string.ok, (dialog, id) -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent = getDocumentsuiIntent(activity.getApplicationContext(), intent);
            if (checkImplicitIntent(activity, intent))
                doStartActivityForResult(intent, REQUEST_DOCUMENT_TREE);
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void storageMenu(boolean drawer) {
        ArrayList<String> itemArray = new ArrayList<>();
        ArrayList<String> appIdArray = new ArrayList<>();
        if (!drawer) {
            itemArray.add(getString(R.string.file_chooser));
            appIdArray.add(itemArray.get(0));
        }

        PackageManager pm = this.getApplicationContext().getPackageManager();
        String launchApp = getString(R.string.launch_app);
        for (int i = 0; i < mFilePickerItems.size(); i++) {
            String id = mFilePickerItems.get(i);
            String label = id;
            try {
                PackageInfo packageInfo = pm.getPackageInfo(id, 0);
                label = String.format(launchApp, packageInfo.applicationInfo.loadLabel(pm).toString());
                if (drawer && !id.equals(APP_FILER)) continue;
            } catch (Exception e) {
                // Do nothing
            }
            if (id.equals(APP_FILER)) {
                label = getString(R.string.app_files);
            }
            itemArray.add(label);
            appIdArray.add(id);
        }
        final String[] items = itemArray.toArray(new String[0]);
        final String[] appId = appIdArray.toArray(new String[0]);
        new AlertDialog.Builder(this).setItems(items, (dialog, which) -> {
            String item = items[which];
            if (item == null) {
                // do nothing
            } else if (item.equals(getString(R.string.file_chooser))) {
                filePicker();
            } else if (getString(R.string.open_document_tree).equals(item)) {
                requestDocumentTreeWritePermission();
            } else if (getString(R.string.create_file).equals(item)) {
                fileCreate();
            } else if (getString(R.string.delete_file).equals(item)) {
                fileDelete();
            } else if (getString(R.string.clear_cache).equals(item)) {
                confirmClearCache();
            } else if (getString(R.string.create_symlinks).equals(item)) {
                setupStorageSymlinks();
            } else if (getString(R.string.backup_restore).equals(item)) {
                backupAndRestoreHome();
            } else if (getString(R.string.menu_edit_vimrc).equals(item)) {
                editVimrc();
            } else {
                intentMainActivity(appId[which]);
            }
        }).setNegativeButton(android.R.string.cancel, null)
                .setTitle(getString(R.string.storage_menu))
                .show();
    }

    private void fileDelete() {
        if (SCOPED_STORAGE) {
            doFileDelete();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_DELETE);
        } else {
            doFileDelete();
        }
    }

    private void doFileDelete() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent = getDocumentsuiIntent(this.getApplicationContext(), intent);
        if (checkImplicitIntent(this, intent))
            doStartActivityForResult(intent, REQUEST_FILE_DELETE);
    }

    private void confirmDelete(final Uri uri) {
        String path = UriToPath.getPath(this, uri);
        if (path == null) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null, null);
            path = getOpenDocumentPath(uri, cursor);
            if (path == null) {
                alert(getString(R.string.storage_read_error));
                return;
            }
        }
        String file = new File(path).getName();
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(file);
        b.setPositiveButton(getString(R.string.delete_file), (dialog, id) -> {
            try {
                deleteDocument(getContentResolver(), uri);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void fileCreate() {
        if (SCOPED_STORAGE) {
            doFileCreate();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_CREATE);
        } else {
            doFileCreate();
        }
    }

    private void doFileCreate() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, "Newfile.txt");
        intent = getDocumentsuiIntent(this.getApplicationContext(), intent);
        if (checkImplicitIntent(this, intent))
            doStartActivityForResult(intent, REQUEST_FILE_PICKER);
    }

    private void fileReload() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setTitle(getString(R.string.reload_file_title));

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
        builder.setPositiveButton(android.R.string.yes, (d, m) -> {
            String encoding = textView.getText().toString();
            String cmd = ":e!";
            if (!encoding.equals("")) cmd += " ++enc=" + encoding;
            sendKeyStrings(cmd + "\r", true);
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

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        switch (request) {
            case REQUEST_VOICE_INPUT:
                if (result == RESULT_OK && data != null) {
                    ArrayList<String> candidates = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (candidates.size() > 0) {
                        String str = candidates.get(0);
                        TermSession session = getCurrentTermSession();
                        if (session == null) return;
                        session.write(str);
                    }
                }
                break;
            case REQUEST_WEBVIEW_ACTIVITY:
            case REQUEST_HTML_LOG_ACTIVITY:
                int webViewSize = WebViewActivity.getFontSize();
                String fontSizeId = request == REQUEST_WEBVIEW_ACTIVITY ? WEBVIEW_FONT_SIZE : WEBVIEW_HTML_LOG_FONT_SIZE;
                PrefValue pv = new PrefValue(this);
                if (webViewSize != pv.getInt(fontSizeId, WEBVIEW_DEFAULT_FONT_SIZE)) {
                    pv.setInt(fontSizeId, webViewSize);
                }
                break;
            case REQUEST_DOCUMENT_TREE:
                if (result == RESULT_OK && data != null) {
                    try {
                        Uri uri = data.getData();
                        final int takeFlags =
                                (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);
                        alert(getString(R.string.request_approved));
                    } catch (Exception e) {
                        alert(getString(R.string.request_approved) + "\n\n" + e);
                        break;
                    }
                }
                break;
            case REQUEST_COPY_DOCUMENT_TREE_TO_HOME:
                if (result == RESULT_OK && data != null) {
                }
                break;
            case REQUEST_COPY_DOCUMENT_TREE_BACKUP_HOME:
            case REQUEST_COPY_DOCUMENT_TREE_RESTORE_TO_HOME:
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        switch (request) {
                            case REQUEST_COPY_DOCUMENT_TREE_BACKUP_HOME:
                                ASFUtils.backupToTreeUri(Term.this, uri, TermService.getHOME());
                                break;
                            case REQUEST_COPY_DOCUMENT_TREE_RESTORE_TO_HOME:
                                ASFUtils.restoreHomeFromTreeUri(Term.this, uri, TermService.getHOME());
                                break;
                        }
                    }
                }
                break;
            case REQUEST_FILE_DELETE:
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    confirmDelete(uri);
                }
                break;
            case REQUEST_FILE_PICKER:
                if (result == RESULT_OK && data != null) {
                    int items = 1;
                    Uri uri = data.getData();
                    ClipData clipData = data.getClipData();
                    if (clipData != null) items = clipData.getItemCount();
                    for (int i = 0; i < items; i++) {
                        if (clipData != null) {
                            ClipData.Item item = clipData.getItemAt(i);
                            uri = item.getUri();
                        }
                        String path = null;
                        path = UriToPath.getPath(this, uri);
                        if (path == null) {
                            try {
                                Cursor cursor = getContentResolver().query(uri, null, null, null, null, null);
                                if (mSyncFileObserver != null) {
                                    path = getOpenDocumentPath(uri, cursor);
                                    if (path == null) {
                                        alert(getString(R.string.storage_read_error));
                                        break;
                                    }
                                    String fname = new File(path).getName();
                                    if (mSyncFileObserver != null) {
                                        path = mSyncFileObserver.getObserverDir() + path;
                                        if (path.equals("") || !mSyncFileObserver.putUriAndLoad(uri, path)) {
                                            alert(fname + "\n" + getString(R.string.storage_read_error));
                                            break;
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Log.d("FilePicker", e.toString());
                                alert(getString(R.string.storage_read_error) + "\n" + e);
                                break;
                            }
                        }
                        if (path != null) {
                            doShellIntentCommand(path, mSettings.getIntentCommand());
                        }
                    }
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
        super.onNewIntent(intent);
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
                Objects.requireNonNull(getCurrentEmulatorView()).toggleSelectingText();
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
        if (mVolumeAsCursor) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                int key = keyCode == KeyEvent.KEYCODE_VOLUME_UP ? KeycodeConstants.KEYCODE_DPAD_UP : KeycodeConstants.KEYCODE_DPAD_DOWN;
                dispatchKeyEventUD(key);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void dispatchKeyEventUD(int keyCode) {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        dispatchKeyEvent(event);
        event = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
        dispatchKeyEvent(event);
    }

    private boolean mPressBackTwice = false;

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case EscCmd.VKEYCODE_0998:
                if (mTermSessions.size() > 1) {
                    return true;
                }
                // fall into next
            case EscCmd.VKEYCODE_0999:
                destroyAppWarning();
                return true;
            case EscCmd.VKEYCODE_1000:
                setFunctionBar(2);
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
                if (onelineTextBoxEsc()) return true;
                break;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (mVolumeAsCursor) return true;
                break;
            case KeyEvent.KEYCODE_MENU:
                openOptionsMenu();
                break;
            case EscCmd.VKEYCODE_0003:
                copyFileToClipboard(getClipboardFile());
                return true;
            case EscCmd.VKEYCODE_0004:
                setEditTextView(0);
                return true;
            case EscCmd.VKEYCODE_0005:
                setEditTextView(1);
                return true;
            case EscCmd.VKEYCODE_0006:
                setEditTextView(2);
                return true;
            case EscCmd.VKEYCODE_0007:
                String file = getStringFromFile(new File(getIntentFile()));
                file = file != null ? file.replaceAll("[\n\r]", "") : "";
                doAndroidIntent("share.file", file, null);
                return true;
            case EscCmd.VKEYCODE_0008:
                doAndroidIntent("share.text", getIntentFile(), null);
                return true;
            case EscCmd.VKEYCODE_1010:
                setFunctionBar(0);
                return true;
            case EscCmd.VKEYCODE_1011:
                setFunctionBar(1);
                return true;
            case EscCmd.VKEYCODE_1002:
                setFunctionBar(2);
                return true;
            case EscCmd.VKEYCODE_0033:
            case EscCmd.VKEYCODE_0333:
                if (!canPaste()) {
                    alert(getString(R.string.toast_clipboard_error));
                    return true;
                }
                copyClipboardToFile(getClipboardFile());
                if (keyCode == EscCmd.VKEYCODE_0333) sendKeyStrings(":ATEMod _paste\r", true);
                return true;
            case EscCmd.VKEYCODE_1006:
                doSendActionBarKey(getCurrentEmulatorView(), 1006);
                return true;
            case EscCmd.VKEYCODE_1007:
                return true;
            case EscCmd.VKEYCODE_1008:
                setupStorageSymlinks();
                return true;
            case EscCmd.VKEYCODE_1009:
                return true;
            case EscCmd.VKEYCODE_1364:
                doSendActionBarKey(getCurrentEmulatorView(), KEY_ACTION_1364);
                return true;
            case EscCmd.VKEYCODE_0063:
            case EscCmd.VKEYCODE_1365:
                sendVimIminsertKey();
                return true;
            case EscCmd.VKEYCODE_0056:
                doEditTextFocusAction();
                setEditTextInputType(50);
                return true;
            case EscCmd.VKEYCODE_0057:
                setEditTextViewFocus(0);
                return true;
            case EscCmd.VKEYCODE_0058:
                setEditTextViewFocus(1);
                return true;
            case EscCmd.VKEYCODE_0061:
                keyEventSender(KEYEVENT_SENDER_SHIFT_SPACE);
                return true;
            case EscCmd.VKEYCODE_0062:
                keyEventSender(KEYEVENT_SENDER_ALT_SPACE);
                return true;
            case EscCmd.VKEYCODE_0030:
                clearClipBoard();
                return true;
            case EscCmd.VKEYCODE_1001:
                AndroidIntent(getIntentFile());
                return true;
            case EscCmd.VKEYCODE_9998:
                fatalCrash();
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean backkey() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
            return true;
        }
        if (mHaveFullHwKeyboard && mSettings.getBackAsEscFlag()) {
            sendKeyStrings("\u001b", false);
            return true;
        }
        int backAction = mSettings.getBackKeyAction();
        if (!mPressBackTwice) {
            if (backAction == TermSettings.BACK_KEY_STOPS_SERVICE) {
                String message = getString(R.string.press_back_again);
                int length = Snackbar.LENGTH_SHORT;
                Snackbar snackbar = Snackbar.make(findViewById(R.id.term_coordinator_layout_bottom), message, length);
                View snackbarView = snackbar.getView();
                TextView tv = snackbarView.findViewById(R.id.snackbar_text);
                tv.setMaxLines(2);
                snackbar.addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar snackbar, int event) {
                        if (event == Snackbar.Callback.DISMISS_EVENT_TIMEOUT) {
                            mPressBackTwice = false;
                        }
                    }

                    @Override
                    public void onShown(Snackbar snackbar) {
                        mPressBackTwice = true;
                    }
                });
                snackbar.show();
                return true;
            }
        }
        switch (backAction) {
            case TermSettings.BACK_KEY_SENDS_ESC:
                sendKeyStrings("\u001b", false);
                return true;
            case TermSettings.BACK_KEY_SENDS_TAB:
                sendKeyStrings("\u0009", false);
                return true;
            case TermSettings.BACK_KEY_STOPS_SERVICE:
                mStopServiceOnFinish = true;
                finish();
            case TermSettings.BACK_KEY_CLOSES_WINDOW:
                doSendActionBarKey(getCurrentEmulatorView(), KEY_ACTION_1251);
                return true;
            case TermSettings.BACK_KEY_CLOSES_ACTIVITY:
                finish();
                return true;
            case TermSettings.BACK_KEY_TOGGLE_IME:
                doToggleSoftKeyboard();
                return true;
            case TermSettings.BACK_KEY_DEFAULT:
            default:
                return false;
        }
    }

    private void doUninstallExtraContents() {
        recoveryDelete();
        mUninstall = false;
    }

    static public void recoveryDelete() {
        shell("rm -rf " + TermService.getAPPFILES() + "/bin");
        shell("rm -rf " + TermService.getAPPFILES() + "/usr");
        shell("rm " + TermService.getAPPFILES() + "/bin/vim");
        shell("rm " + TermService.getAPPFILES() + "/bin/vim.default");
        shell("rm " + TermService.getAPPFILES() + "/bin/vim.python");
        shell("rm -rf " + TermService.getVIMRUNTIME() + "/pack/shiftrot");
        shell("rm " + TermService.getVersionFilesDir() + "/proot.err");
        shell("rm " + TermService.getVersionFilesDir() + "/vimrc");
        shell("rm " + TermService.getVersionFilesDir() + "/version");
        shell("rm " + TermService.getVersionFilesDir() + "/version.*");
        shell("rm -rf " + TermService.getCACHE_DIR() + "/apt");
        shell("rm -rf " + TermService.getCACHE_DIR() + "/tmp");
        shell("rm -rf " + TermService.getCACHE_DIR() + "/vim");
        mUninstall = false;
    }

    private void fatalCrash() {
        try {
            setUninstallExtraContents(false);
            mUninstall = false;
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            bld.setIcon(android.R.drawable.ic_dialog_alert);
            bld.setTitle(getString(R.string.crash_title) + " (" + getArch() + ")");
            String message = getString(R.string.crash_message);
            File file = new File(TermService.getAPPFILES() + "/proot.err");
            if (file.exists()) {
                message += "\n\n";
                message += getString(R.string.proot_error_message);
            }

            if (isInExternalStorage()) {
                message += "\n\n";
                message += getString(R.string.security_app_in_sdcard_message);
            }
            bld.setMessage(message);
            bld.setPositiveButton(getString(android.R.string.ok), (dialog, m) -> {
                dialog.dismiss();
                fatalCrashQuit();
            });
            bld.setNeutralButton(getString(R.string.crash_trouble_shooting_button), (dialog, m) -> {
                dialog.dismiss();
                troubleShooting(true);
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

    private boolean isInExternalStorage() {
        if (!TermService.getAPPFILES().startsWith("/data/")) return true;
        return !ASFUtils.isSymlink(new File(TermService.getAPPFILES() + "/usr/bin/" + TermVimInstaller.getArch()));
    }

    private void fatalCrashQuit() {
        mFatalTroubleShooting = true;
        setUninstallExtraContents(false);
        ArrayList<String> itemList = new ArrayList<>();
        itemList.add(getString(R.string.crash_quit_button));
        if (FLAVOR_VIM) itemList.add(getString(R.string.launch_default_vim));
        itemList.add(getString(R.string.quit_to_shell));
        final String[] items = itemList.toArray(new String[0]);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_choose))
                .setSingleChoiceItems(items, -1, (dialog, which) -> {
                    dialog.dismiss();
                    if (getString(R.string.launch_default_vim).equals(items[which])) {
                        if (new File(TermService.getAPPFILES() + "/bin/vim.default").canExecute()) {
                            sendKeyStrings("vim.app.default\r", false);
                        } else {
                            sendKeyStrings("vim.app\r", false);
                        }
                    } else if (getString(R.string.quit_to_shell).equals(items[which])) {
                        quitToShell();
                    } else if (getString(R.string.crash_quit_button).equals(items[which])) {
                        doCloseCrashWindow();
                    } else {
                        fatalCrash();
                    }
                })
                .setNegativeButton(getString(android.R.string.cancel), (dialog, m) -> {
                    dialog.dismiss();
                    fatalCrash();
                })
                .create();
        dlg.setCancelable(false);
        dlg.setCanceledOnTouchOutside(false);
        dlg.show();
    }

    private void quitToShell() {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_alert);
        String message = getString(R.string.quit_to_shell);
        bld.setMessage(message);
        bld.setPositiveButton(getString(android.R.string.ok), (dialog, id) -> alert(getString(R.string.shell_exit_command_message)));
        bld.setNegativeButton(getString(android.R.string.cancel), (dialog, id) -> fatalCrash());
        bld.create().show();
    }

    private void troubleShooting(boolean fatal) {
        mFatalTroubleShooting = fatal;
        final String[] items = {
                getString(R.string.revert_to_default_vim)};
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(getString(R.string.crash_trouble_shooting_button))
                .setSingleChoiceItems(items, -1, (dialog, which) -> {
                    dialog.dismiss();
                    if (getString(R.string.revert_to_default_vim).equals(items[which])) {
                        setUninstallExtraContents(true);
                        doCloseCrashWindow(getString(R.string.revert_to_default_vim));
                    }
                })
                .setNegativeButton(getString(android.R.string.cancel), (dialog, m) -> {
                    dialog.dismiss();
                    if (mFatalTroubleShooting) {
                        fatalCrash();
                    } else {
                        mUninstall = false;
                    }
                })
                .create();
        dlg.setCancelable(false);
        dlg.setCanceledOnTouchOutside(false);
        dlg.show();
    }

    private void doCloseCrashWindow() {
        doCloseCrashWindow(null);
    }

    private void doCloseCrashWindow(CharSequence message) {
        final AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_alert);
        bld.setCancelable(false);
        bld.setTitle(R.string.close_window);
        if (message != null) bld.setMessage(message);
        bld.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            if (mUninstall) doUninstallExtraContents();
            doCloseWindow();
        });
        bld.setNegativeButton(android.R.string.cancel, (dialog, id) -> {
            dialog.dismiss();
            fatalCrash();
        });
        AlertDialog dlg = bld.create();
        dlg.setCancelable(false);
        dlg.setCanceledOnTouchOutside(false);
        dlg.show();
    }

    private boolean doDoubleTapAction(MotionEvent me) {
        EmulatorView v = (EmulatorView) mViewFlipper.getCurrentView();
        if (v != null) {
            Resources resources = getApplicationContext().getResources();
            DisplayMetrics metrics = resources.getDisplayMetrics();
            float EDGE = (float) 54.0;
            float px = EDGE * (metrics.densityDpi / 160.0f);
            int size = (int) Math.ceil(px);

            int height = Objects.requireNonNull(getCurrentEmulatorView()).getVisibleHeight();
            int width = getCurrentEmulatorView().getVisibleWidth();
            int rightAction = mSettings.getRightDoubleTapAction();
            int leftAction = mSettings.getLeftDoubleTapAction();
            int bottomAction = mSettings.getBottomDoubleTapAction();
            int topAction = mSettings.getTopDoubleTapAction();

            // if (mFunctionBar == 1 && rightAction == 1261 && mEditTextView) rightAction = 999;
            // if (mFunctionBar == 1 && leftAction == 1261 && mEditTextView) leftAction = 999;
            // if (mFunctionBar == 1 && bottomAction == 1261 && mEditTextView) bottomAction = 999;
            if (rightAction != KEY_ACTION_999 && (me.getX() > (width - size))) {
                doSendActionBarKey(getCurrentEmulatorView(), rightAction);
            } else if (leftAction != KEY_ACTION_999 && (me.getX() < size)) {
                doSendActionBarKey(getCurrentEmulatorView(), leftAction);
            } else if (bottomAction != KEY_ACTION_999 && me.getY() > (height - size)) {
                doSendActionBarKey(getCurrentEmulatorView(), bottomAction);
            } else if (topAction != KEY_ACTION_999 && me.getY() < size + (size / 2.0f)) {
                EmulatorView.setTextScale(1.0f);
                v.setFontSize();
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doAndroidIntent(String str0, String str1, String str2) {
        if (str0 == null) return;
        String action = str0;
        if (action.equalsIgnoreCase("symlinks")) {
            setupStorageSymlinks();
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
                alert("Unknown activity:\n" + str1);
            }
            return;
        } else if (str1.matches("^%(w3m|open)%.*")) {
            str1 = str1.replaceFirst("%(w3m|open)%", "");
        } else if (action.equalsIgnoreCase("share.file")) {
            File file = new File(str1);
            if (!file.canRead()) {
                alert(getString(R.string.storage_read_error) + "\n" + str1);
                return;
            }
            action = "android.intent.action.VIEW";
        }
        if (str1.matches("'.*'")) {
            str1 = str1.replaceAll("^'|'$", "");
        }
        String MIME_HTML = MimeTypeMap.getSingleton().getMimeTypeFromExtension("html");
        String mime;
        String ext;
        int ch = str1.lastIndexOf('.');
        ext = (ch >= 0) ? str1.substring(ch + 1) : "";
        ext = ext.toLowerCase();
        ext = ext.replaceAll("(html?)#.*", "$1");
        String path = str1.replaceFirst("file://", "");
        path = path.replaceFirst("(.*\\.html?)#.*", "$1");
        if (str2 != null) {
            mime = str2;
        } else if (str1.matches("^(https?|ftp)://.*")) {
            Uri uri = Uri.parse(str1);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
            return;
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
                if (!mime.equals(MIME_HTML) && !(privateStorage || extFilesStorage) && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    uri = Uri.fromFile(file);
                } else {
                    if (mime.equals(MIME_HTML) && mSettings.getHtmlViewerMode() <= 1) {
                        try {
                            intent = new Intent(this, WebViewActivity.class);
                            intent.putExtra("url", file.toString());
                            WebViewActivity.setFontSize(new PrefValue(this).getInt(WEBVIEW_FONT_SIZE, WEBVIEW_DEFAULT_FONT_SIZE));
                            startActivityForResult(intent, REQUEST_WEBVIEW_ACTIVITY);
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

                        File cacheDir = new File(getIntentCacheDir());
                        File cache = new File(cacheDir + "/" + hash);
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
                            alert(getString(R.string.prefs_read_error_title) + "\n" + path);
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
                if (mSettings.getHtmlViewerMode() <= 1) {
                    intent = new Intent(this, WebViewActivity.class);
                    intent.putExtra("url", uri.toString());
                    PackageManager pm = this.getApplicationContext().getPackageManager();
                    if (intent.resolveActivity(pm) != null) {
                        WebViewActivity.setFontSize(new PrefValue(this).getInt(WEBVIEW_FONT_SIZE, WEBVIEW_DEFAULT_FONT_SIZE));
                        startActivityForResult(intent, REQUEST_WEBVIEW_ACTIVITY);
                    }
                } else {
                    intent.setAction(action);
                    PackageManager pm = this.getApplicationContext().getPackageManager();
                    if (intent.resolveActivity(pm) != null) startActivity(intent);
                    else alert("Unknown action:\n" + action);
                }
            } catch (Exception e) {
                alert(getString(R.string.storage_read_error));
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

    private String getHashString(String s) {
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance(HASH_ALGORITHM);
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();

            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                hexString.append(Integer.toHexString(0xFF & b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    private String getIntentFile() {
        return TermService.getAPPFILES() + "/.intent";
    }

    private String getClipboardFile() {
        return TermService.getAPPFILES() + "/.clipboard";
    }

    private String getIntentCacheDir() {
        try {
            return getApplicationContext().getCacheDir().toString() + "/intent";
        } catch (Exception e) {
            return "/data/data/" + BuildConfig.APPLICATION_ID + "/cache/intent";
        }
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
                CharBuffer cbuf = StandardCharsets.UTF_8.newDecoder().decode(bbuf);
                String str = cbuf.toString();
                ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                        .getManager(getApplicationContext());
                clip_setText(clip, str);
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

    private void clip_setText(ClipboardManagerCompat clip, String str) {
        clip.setText(str);
    }

    private void clearClipBoard() {
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        clip_setText(clip, "");
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
        return clip.hasText();
    }

    private void doPreferences() {
        mOrientation = -1;
        startActivity(new Intent(this, TermPreferences.class));
    }

    private void doResetTerminal() {
        recreate();
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

    private void shareIntentTextDialog() {
        final String[] items = {getString(R.string.share_buffer_text), getString(R.string.share_visual_text), getString(R.string.share_unnamed_text), getString(R.string.share_file)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.share_title))
                .setItems(items, (dialog, which) -> {
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
        try {
            if (!file.canRead()) return null;
            StringBuilder builder = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(file.toString()));
            String string = reader.readLine();
            while (string != null) {
                builder.append(string).append(System.getProperty("line.separator"));
                string = reader.readLine();
            }
            return builder.toString();
        } catch (Exception e) {
            alert(e.getMessage());
            return null;
        }
    }

    private void doShareIntentText(String text) {
        try {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, text);
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent, "Share"));
        } catch (Exception e) {
            complain("ShareIntent Text: " + e);
        }
    }

    private void doShareAll() {
        final String[] items = {getString(R.string.copy_screen_current), getString(R.string.copy_screen_buffer)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.share_screen_text))
                .setItems(items, (dialog, which) -> doCopyAll(which + 2))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doCopyAll() {
        final String[] items = {getString(R.string.copy_screen_current), getString(R.string.copy_screen_buffer)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.copy_screen))
                .setItems(items, (dialog, which) -> doCopyAll(which))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doCopyAll(int mode) {
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        String str;
        String mes;
        EmulatorView view = getCurrentEmulatorView();
        if (view == null) return;
        if (mode == 0) {
            str = view.getTranscriptCurrentText();
            clip_setText(clip, str);
            mes = getString(R.string.toast_clipboard);
        } else if (mode == 1) {
            str = view.getTranscriptText().trim();
            clip_setText(clip, str);
            mes = getString(R.string.toast_clipboard);
        } else if (mode == 2) {
            str = view.getTranscriptCurrentText();
            doShareIntentText(str);
            return;
        } else if (mode == 3) {
            str = view.getTranscriptText().trim();
            doShareIntentText(str);
            return;
        } else {
            return;
        }
        showSnackbar(mes);
    }

    private void showTextInWebview(String htmlTemplate, String strings) {
        strings = strings.replaceAll("&", "&amp;");
        strings = strings.replaceAll("<", "&lt;");
        strings = strings.replaceAll(">", "&gt;");
        strings = strings.replaceAll("\"", "&quot;");
        strings = strings.replaceAll(" ", "&nbsp;");
        strings = strings.replaceAll("\n", "<br />");
        String file = TermService.getTMPDIR() + "/html/text.html";
        int id = getResources().getIdentifier(htmlTemplate, "raw", getPackageName());
        copyScript(getResources().openRawResource(id), file, strings);
        Intent intent;
        intent = new Intent(this, WebViewActivity.class);
        intent.putExtra("url", file);
        WebViewActivity.setFontSize(new PrefValue(this).getInt(WEBVIEW_HTML_LOG_FONT_SIZE, 140));
        startActivityForResult(intent, REQUEST_HTML_LOG_ACTIVITY);
    }

    private void doPaste() {
        doWarningBeforePaste();
    }

    private void choosePasteMode() {
        final String[] items = {
                getString(R.string.paste_shell),
                getString(R.string.paste_vim)};

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.clipboard))
                .setSingleChoiceItems(items, -1, (dialog1, which) -> {
                    dialog1.cancel();
                    String item = items[which];
                    if (getString(R.string.paste_vim).equals(item)) {
                        sendKeyStrings("\"*p", true);
                    } else if (getString(R.string.paste_shell).equals(item)) {
                        doTermPaste();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void chooseTermClipboard() {
        final String[] items = {
                getString(R.string.copy_to_clipboard),
                getString(R.string.paste_shell)};

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.clipboard))
                .setSingleChoiceItems(items, -1, (dialog1, which) -> {
                    dialog1.cancel();
                    String item = items[which];
                    if (getString(R.string.copy_to_clipboard).equals(item)) {
                        doScreenMenu();
                    } else if (getString(R.string.paste_shell).equals(item)) {
                        doTermPaste();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doCopyText() {
        String strings;
        EmulatorView view = getCurrentEmulatorView();
        if (view != null) {
            if (FLAVOR_VIM) {
                strings = view.getTranscriptCurrentText();
            } else {
                strings = view.getTranscriptText().trim();
            }
            showTextInWebview("html_text", strings);
        }
    }

    private void doTermPaste() {
        if (!canPaste()) {
            alert(getString(R.string.toast_clipboard_error));
            return;
        }
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        CharSequence paste = clip.getText();
        if (paste == null) return;
        TermSession session = getCurrentTermSession();
        if (session != null) session.write(paste.toString());
    }

    private void doWarningBeforePaste() {
        if (!FLAVOR_VIM) {
            chooseTermClipboard();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setTitle(R.string.clipboard_warning_title);
        builder.setMessage(R.string.clipboard_warning);
        builder.setPositiveButton(getString(R.string.paste), (d, m) -> {
            // choosePasteMode();
            doTermPaste();
        });
        builder.setNeutralButton(getString(R.string.copy_text), (d, m) -> doCopyText());
        builder.setNegativeButton(android.R.string.no, null);
        builder.create().show();
    }

    private void doSendControlKey() {
        Objects.requireNonNull(getCurrentEmulatorView()).sendControlKey();
    }

    private void doSendFnKey() {
        Objects.requireNonNull(getCurrentEmulatorView()).sendFnKey();
    }

    private void doRestartSoftKeyboard() {
        EmulatorView view = getCurrentEmulatorView();
        if (view != null) view.restartInput(true);
    }

    private void doToggleSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        requestFocusView();
    }

    private void doShowSoftKeyboard() {
        if (getCurrentEmulatorView() == null) return;
        InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
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

    private void doToggleKeepScreen() {
        boolean keepScreen = (getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
        if (keepScreen) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mKeepScreenHandler.removeCallbacksAndMessages(null);
            if (!mKeepScreenEnableAuto) showSnackbar(getString(R.string.keepscreen_deacitvated));
        } else {
            final int timeout = mSettings.getKeepScreenTime();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            String mes = String.format(getString(R.string.keepscreen_notice), timeout);
            if (!mKeepScreenEnableAuto) alert(mes);
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
                            if (!mKeepScreenEnableAuto)
                                showSnackbar(getString(R.string.keepscreen_deacitvated));
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

    static public void setPrefBoolean(Context context, String key, boolean value) {
        PrefValue pv = new PrefValue(context);
        pv.setBoolean(key, value);
    }

    static public boolean getPrefBoolean(Context context, String key, boolean defValue) {
        PrefValue pv = new PrefValue(context);
        return pv.getBoolean(key, defValue);
    }

    static public void setPrefString(Context context, String key, String value) {
        PrefValue pv = new PrefValue(context);
        pv.setString(key, value);
    }

    static public String getPrefString(Context context, String key, String defValue) {
        PrefValue pv = new PrefValue(context);
        return pv.getString(key, defValue);
    }

    private void initOnelineTextBox(int mode) {
        mEditText = findViewById(R.id.text_input);
        mEditText.setText("");
        setEditTextView(mode);
        mEditText.setInputType(EditorInfo.TYPE_CLASS_TEXT);
        mEditText.setOnEditorActionListener((v, actionId, event) -> {
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
        });
        mEditText.setOnKeyListener((view, keyCode, event) -> {
            onLastKey();
            if (keyCode == KeycodeConstants.KEYCODE_TAB) {
                return true;
            }
            int shortcut = EmulatorView.getPreIMEShortcutsStatus(keyCode, event);
            if (shortcut == EmulatorView.PREIME_SHORTCUT_ACTION) {
                int action = mSettings.getImeShortcutsAction();
                if (action == KEY_ACTION_0) {
                    doToggleSoftKeyboard();
                } else if (action == KEY_ACTION_1261) {
                    doEditTextFocusAction();
                } else if (action == KEY_ACTION_1361) {
                    keyEventSender(KEYEVENT_SENDER_SHIFT_SPACE);
                } else if (action == KEY_ACTION_1362) {
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
        });
    }

    private void setEditTextInputType(int action) {
        int inputType;
        switch (action) {
            case 51:
                inputType = EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
                break;
            case 52:
                inputType = EditorInfo.TYPE_TEXT_VARIATION_URI;
                break;
            case 53:
                inputType = EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
                EmulatorView view = getCurrentEmulatorView();
                if (view != null) inputType = view.getNoSuggestionModeIMEInputType();
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
        if (!FLAVOR_VIM) return;
        doWarningDialog(getString(R.string.edit_text_view_warning_title), getString(R.string.edit_text_view_warning), "do_warning_edit_text_view", true);
    }

    private void setNavigationBar(int mode) {
        if (mode < 2) mHideFunctionBar = (mode == 0);
        else mHideFunctionBar = findViewById(R.id.view_navigation_bar).getVisibility() != View.GONE;
        setFunctionKeyVisibility();
    }

    private void setFunctionBar(int mode) {
        boolean focus = false;
        if (mEditText != null && mEditTextView) focus = mEditText.hasFocus();
        if (mode == 2) {
            mFunctionBar = mFunctionBar == 0 ? 1 : 0;
        } else mFunctionBar = mode;
        if (mAlreadyStarted) updatePrefs();
        if (!focus && mEditText != null) {
            EmulatorView view = getCurrentEmulatorView();
            if (view != null) view.requestFocusFromTouch();
        }
    }

    private void setFunctionKey() {
        final String UP = FUNCTIONBAR_UP;
        final String DOWN = FUNCTIONBAR_DOWN;
        final String RIGHT = FUNCTIONBAR_RIGHT;
        final String LEFT = FUNCTIONBAR_LEFT;
        final String FN_UP = getString(R.string.string_functionbar_fn_up);
        final String FN_DOWN = getString(R.string.string_functionbar_fn_down);
        final String PAGE_UP = getString(R.string.string_functionbar_page_up);
        final String PAGE_DOWN = getString(R.string.string_functionbar_page_down);
        final String FN_TOGGLE = getString(R.string.string_functionbar_fn_toggle);
        final String BACKSPACE = getString(R.string.string_functionbar_backspace);
        final String ENTER = getString(R.string.string_functionbar_enter);
        final String OPEN_FILE = getString(R.string.string_functionbar_open_file);
        final String NEW_FILE = getString(R.string.string_functionbar_new_file);
        final String IME_TOGGLE = getString(R.string.string_functionbar_ime_toggle);
        final String SOFTKEYBOARD = getString(R.string.string_functionbar_dia);
        final String VOICE_INPUT = getString(R.string.string_functionbar_voice_input);
        final String MENU_QUIT = getString(R.string.string_functionbar_quit);
        final String MENU = "≡";

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String[] fKey = new String[mCmd_FKEY.length];
        for (int i = 0; i < mCmd_FKEY.length; i++) {
            String id = String.valueOf(i+1);
            fKey[i] = prefs.getString("function_key_label_m" + id, "F" + id);
            mCmd_FKEY[i]  = prefs.getString("function_key_cmd_m" + id, "");
        }

        Resources res = getResources();
        mFunctionKeys = new ArrayList<>();
        mFunctionKeys.add(new FunctionKey(R.id.button_next_functionbar0 , "functionbar_next0"          , FN_UP        , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_next_functionbar2 , "functionbar_next2"          , FN_UP        , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_prev_functionbar  , "functionbar_prev"           , FN_DOWN      , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_prev_functionbar2 , "functionbar_prev2"          , FN_DOWN      , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_esc               , "functionbar_esc"            , "Esc"        , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_ctrl              , "functionbar_ctrl"           , "Ctrl"       , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_alt               , "functionbar_alt"            , "Alt"        , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_tab               , "functionbar_tab"            , "Tab"        , true ));
        mFunctionKeys.add(new FunctionKey(R.id.button_colon             , "functionbar_colon"          , ":"          , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_1        , "functionbar_esc2"           , "Esc"        , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_2        , "functionbar_ctrl2"          , "Ctrl"       , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_3        , "functionbar_alt2"           , "Alt"        , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_4        , "functionbar_tab2"           , "Tab"        , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_5        , "functionbar_up"             , UP           , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_6        , "functionbar_down"           , DOWN         , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_7        , "functionbar_left"           , LEFT         , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_8        , "functionbar_right"          , RIGHT        , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_9        , "functionbar_backspace"      , BACKSPACE    , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_10       , "functionbar_enter"          , ENTER        , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_11       , "functionbar_page_up"        , PAGE_UP      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_12       , "functionbar_page_down"      , PAGE_DOWN    , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_13       , "functionbar_colon2"         , ":"          , false  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_14       , "functionbar_slash"          , "/"          , res.getBoolean(R.bool.pref_functionbar_slash_default) ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_15       , "functionbar_plus"           , "+"          , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_16       , "functionbar_minus"          , "-"          , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_17       , "functionbar_equal"          , "="          , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_18       , "functionbar_asterisk"       , "*"          , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_19       , "functionbar_pipe"           , "|"          , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_20       , "functionbar_f1"             , fKey[0]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_21       , "functionbar_f2"             , fKey[1]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_22       , "functionbar_f3"             , fKey[2]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_23       , "functionbar_f4"             , fKey[3]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_24       , "functionbar_f5"             , fKey[4]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_25       , "functionbar_f6"             , fKey[5]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_26       , "functionbar_f7"             , fKey[6]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_27       , "functionbar_f8"             , fKey[7]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_28       , "functionbar_f9"             , fKey[8]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_29       , "functionbar_f10"            , fKey[9]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_30       , "functionbar_f11"            , fKey[10]     , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_31       , "functionbar_f12"            , fKey[11]     , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_32       , "functionbar_invert"         , "○"         , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_33       , "functionbar_menu_user"      , "□"         , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_34       , "functionbar_menu_x"         , "×"         , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_35       , "functionbar_menu_plus"      , "＋"         , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_36       , "functionbar_menu_minus"     , "－"         , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_37       , "functionbar_softkeyboard"   , SOFTKEYBOARD , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_38       , "functionbar_open_file"      , OPEN_FILE    , res.getBoolean(R.bool.pref_functionbar_open_file_default) ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_39       , "functionbar_new_file"       , NEW_FILE     , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_40       , "functionbar_voice_input"    , VOICE_INPUT  , res.getBoolean(R.bool.pref_functionbar_voice_input_default) ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_41       , "functionbar_ime_toggle"     , IME_TOGGLE   , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_42       , "functionbar_vim_paste"      , "\"*p"       , res.getBoolean(R.bool.pref_functionbar_vim_paste_default) ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_43       , "functionbar_vim_yank"       , "\"*yy"      , res.getBoolean(R.bool.pref_functionbar_vim_yank_default) ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_44       , "functionbar_menu_quit"      , MENU_QUIT    , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_45       , "functionbar_menu"           , MENU         , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_function_46       , "functionbar_menu_hide"      , "∇"         , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_m1                , "functionbar_m1"             , fKey[0]      , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_m2                , "functionbar_m2"             , fKey[1]      , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_m3                , "functionbar_m3"             , fKey[2]      , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_m4                , "functionbar_m4"             , fKey[3]      , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_m5                , "functionbar_m5"             , fKey[4]      , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_m6                , "functionbar_m6"             , fKey[5]      , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_m7                , "functionbar_m7"             , fKey[6]      , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_m8                , "functionbar_m8"             , fKey[7]      , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_m9                , "functionbar_m9"             , fKey[8]      , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_m10               , "functionbar_m10"            , fKey[9]      , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_m11               , "functionbar_m11"            , fKey[10]     , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_m12               , "functionbar_m12"            , fKey[11]     , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_1      , "navigationbar_esc"          , "Esc"        , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_2      , "navigationbar_ctrl"         , "Ctrl"       , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_3      , "navigationbar_alt"          , "Alt"        , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_4      , "navigationbar_tab"          , "Tab"        , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_5      , "navigationbar_up"           , UP           , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_6      , "navigationbar_down"         , DOWN         , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_7      , "navigationbar_left"         , LEFT         , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_8      , "navigationbar_right"        , RIGHT        , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_9      , "navigationbar_backspace"    , BACKSPACE    , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_10     , "navigationbar_enter"        , ENTER        , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_11     , "navigationbar_page_up"      , PAGE_UP      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_12     , "navigationbar_page_down"    , PAGE_DOWN    , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_13     , "navigationbar_colon"        , ":"          , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_14     , "navigationbar_slash"        , "/"          , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_15     , "navigationbar_plus"         , "+"          , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_16     , "navigationbar_minus"        , "-"          , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_17     , "navigationbar_equal"        , "="          , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_18     , "navigationbar_asterisk"     , "*"          , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_19     , "navigationbar_pipe"         , "|"          , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_20     , "navigationbar_f1"           , fKey[0]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_21     , "navigationbar_f2"           , fKey[1]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_22     , "navigationbar_f3"           , fKey[2]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_23     , "navigationbar_f4"           , fKey[3]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_24     , "navigationbar_f5"           , fKey[4]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_25     , "navigationbar_f6"           , fKey[5]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_26     , "navigationbar_f7"           , fKey[6]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_27     , "navigationbar_f8"           , fKey[7]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_28     , "navigationbar_f9"           , fKey[8]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_29     , "navigationbar_f10"          , fKey[9]      , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_30     , "navigationbar_f11"          , fKey[10]     , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_31     , "navigationbar_f12"          , fKey[11]     , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_32     , "navigationbar_invert"       , "○"         , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_33     , "navigationbar_menu_user"    , "□"         , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_34     , "navigationbar_menu_x"       , "×"         , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_35     , "navigationbar_menu_plus"    , "＋"         , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_36     , "navigationbar_menu_minus"   , "－"         , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_37     , "navigationbar_softkeyboard" , SOFTKEYBOARD , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_38     , "navigationbar_open_file"    , OPEN_FILE    , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_39     , "navigationbar_new_file"     , NEW_FILE     , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_40     , "navigationbar_voice_input"  , VOICE_INPUT  , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_41     , "navigationbar_vim_paste"    , "\"*p"       , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_42     , "navigationbar_vim_yank"     , "\"yy"       , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_43     , "navigationbar_menu_quit"    , MENU_QUIT    , true  ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_44     , "navigationbar_menu"         , MENU         , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_45     , "navigationbar_fn_toggle"    , FN_TOGGLE    , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_46     , "navigationbar_menu_hide"    , "∇"         , false ));
        mFunctionKeys.add(new FunctionKey(R.id.button_navigation_47     , "navigationbar_ime_toggle"   , IME_TOGGLE   , true  ));

        for (FunctionKey fkey : mFunctionKeys) {
            Button button = findViewById(fkey.resId);
            button.setText(fkey.label);
            switch (fkey.prefId) {
                case "functionbar_up":
                case "functionbar_down":
                case "functionbar_left":
                case "functionbar_right":
                case "functionbar_page_up":
                case "functionbar_page_down":
                case "navigationbar_up":
                case "navigationbar_down":
                case "navigationbar_left":
                case "navigationbar_right":
                case "navigationbar_page_up":
                case "navigationbar_page_down":
                    int interval = mSettings.getFastCursorMode() ? 30 : 60;
                    findViewById(fkey.resId).setOnTouchListener(new RepeatListener(400, interval, Term.this));
                    switch (fkey.prefId) {
                        case "functionbar_up":
                        case "navigationbar_up":
                            FUNCTIONBAR_UP = fkey.label;
                            break;
                        case "functionbar_down":
                        case "navigationbar_down":
                            FUNCTIONBAR_DOWN = fkey.label;
                            break;
                        case "functionbar_left":
                        case "navigationbar_left":
                            FUNCTIONBAR_LEFT = fkey.label;
                            break;
                        case "functionbar_right":
                        case "navigationbar_right":
                            FUNCTIONBAR_RIGHT = fkey.label;
                            break;
                        default:
                            break;
                    }
                    break;
                case "functionbar_softkeyboard":
                case "navigationbar_softkeyboard":
                case "navigationbar_ime_toggle":
                    button.setOnClickListener(this);
                    button.setOnLongClickListener(v -> {
                        VoiceInput.start(Term.this, REQUEST_VOICE_INPUT);
                        return true;
                    });
                    break;
                default:
                    button.setOnClickListener(this);
                    break;
            }
        }
        Button button = findViewById(R.id.button_oneline_text_box_clear);
        button.setOnClickListener(v -> onelineTextBoxClear());

        int visibility = (mSettings.getOneLineTextBoxCr()) ? View.VISIBLE : View.GONE;
        button = findViewById(R.id.button_oneline_text_box_enter);
        button.setVisibility(visibility);
        button.setOnClickListener(v -> onelineTextBoxEnter(true));

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
                int key = KeycodeConstants.KEYCODE_ESCAPE;
                EmulatorView view = getCurrentEmulatorView();
                if (view != null) view.requestFocusFromTouch();
                if (mSettings != null && mSettings.getOneLineTextBoxEsc()) {
                    setEditTextView(0);
                    dispatchKeyEventUD(key);
                    return false;
                }
                dispatchKeyEventUD(key);
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

    private void setFunctionKeyVisibility(SharedPreferences prefs, String key, int id, boolean defValue) {
        int visibility = prefs.getBoolean(key, defValue) ? View.VISIBLE : View.GONE;
        if (key.equals("functionbar_menu_plus")) visibility = View.GONE;
        if (key.equals("functionbar_menu_minus")) visibility = View.GONE;
        if (key.equals("functionbar_menu_x")) visibility = View.GONE;
        if (key.equals("navigationbar_menu_plus")) visibility = View.GONE;
        if (key.equals("navigationbar_menu_minus")) visibility = View.GONE;
        if (key.equals("navigationbar_menu_x")) visibility = View.GONE;
        String label = prefs.getString(FKEY_LABEL + key, "");
        boolean rotation = key.startsWith("function");
        if (key.startsWith("functionbar_prev") || key.startsWith("functionbar_next"))
            rotation = false;
        setFunctionBarButton(id, visibility, label, rotation);

        Button button = findViewById(R.id.button_oneline_text_box_enter);
        visibility = (mSettings.getOneLineTextBoxCr()) ? View.VISIBLE : View.GONE;
        button.setVisibility(visibility);

        setCursorDirectionLabel();
    }

    private void setFunctionKeyVisibility() {
        int visibility;
        if (mHideFunctionBar) {
            visibility = View.GONE;
            findViewById(R.id.view_function_bar).setVisibility(visibility);
            findViewById(R.id.view_function_bar2).setVisibility(visibility);
            findViewById(R.id.view_navigation_bar).setVisibility(visibility);
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        for (FunctionKey fkey : mFunctionKeys) {
            setFunctionKeyVisibility(prefs, fkey.prefId, fkey.resId, fkey.defValue);
        }

        visibility = (mFunctionBar == 1) ? View.VISIBLE : View.GONE;
        findViewById(R.id.view_function_bar).setVisibility(visibility);
        findViewById(R.id.view_function_bar1).setVisibility(visibility);
        visibility = (mFunctionBar == 1 && mFunctionBarId == 1) ? View.VISIBLE : View.GONE;
        findViewById(R.id.view_function_bar2).setVisibility(visibility);

        visibility = mSettings.showFunctionBarNavigationButton() ? View.VISIBLE : View.GONE;
        LinearLayout layout = findViewById(R.id.view_navigation_bar);
        layout.setVisibility(visibility);
    }

    @SuppressLint("NewApi")
    private void setFunctionBarButton(int id, int visibility, String label, boolean rotation) {
        Button button = findViewById(id);
        if (!label.equals("")) button.setText(label);
        button.setVisibility(visibility);
        if (rotation) {

            float dp = (float) mSettings.getFunctionKeyWidth();
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                dp *= 1.25f;
            }
            int px = (int) Math.ceil(dp * getResources().getDisplayMetrics().density);
            button.setMinWidth(px);
        }
        button.setAllCaps(false);
        setScreenFitFunctionBarShortLabel();
    }

    private void setScreenFitFunctionBarShortLabel() {
        View view = getCurrentEmulatorView();
        if (view != null) view.post(new Thread(() -> {
            setShortButtonLabel("navigationbar_esc", "Esc", "E");
            setShortButtonLabel("navigationbar_ctrl", "Ctrl", "C");
            setShortButtonLabel("navigationbar_tab", "Tab", "T");
            setShortButtonLabel("navigationbar_alt", "Alt", "A");
            setShortButtonLabel("navigationbar_vim_paste", "\"*p", "p");
            setShortButtonLabel("navigationbar_vim_yank", "\"*yy", "y");
        }));
    }

    private void setShortButtonLabel(String resId, String label, String shortLabel) {
        Button button = findViewById(FunctionKey.getResourceId(resId));
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
                label = shortLabel;
            }
            button.setText(label);
        }
    }

    public void onClick(View v) {
        EmulatorView view = getCurrentEmulatorView();
        if (view == null) return;
        String prefId = FunctionKey.getPreferenceId(v.getId());
        if (prefId == null) return;
        switch (prefId) {
            case "navigationbar_open_file":
            case "functionbar_open_file":
                filePicker();
                break;
            case "navigationbar_new_file":
            case "functionbar_new_file":
                fileCreate();
                break;
            case "functionbar_voice_input":
            case "navigationbar_voice_input":
                VoiceInput.start(Term.this, REQUEST_VOICE_INPUT);
                break;
            case "navigationbar_ime_toggle":
            case "functionbar_ime_toggle":
                doToggleSoftKeyboard();
                break;
            case "navigationbar_esc":
            case "functionbar_esc":
            case "functionbar_esc2":
                if (view.getControlKeyState() != 0 || view.getAltKeyState() != 0 || (getInvertCursorDirection() != getDefaultInvertCursorDirection())) {
                    mInvertCursorDirection = getDefaultInvertCursorDirection();
                    setCursorDirectionLabel();
                    view.setControlKeyState(0);
                    view.setAltKeyState(0);
                    break;
                }
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_ESCAPE);
                break;
            case "navigationbar_ctrl":
            case "functionbar_ctrl":
            case "functionbar_ctrl2":
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
            case "navigationbar_alt":
            case "functionbar_alt":
            case "functionbar_alt2":
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_ALT_LEFT);
                break;
            case "navigationbar_tab":
            case "functionbar_tab":
            case "functionbar_tab2":
                if (onelineTextBoxTab()) break;
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_TAB);
                break;
            case "navigationbar_up":
            case "navigationbar_down":
            case "navigationbar_left":
            case "navigationbar_right":
            case "functionbar_up":
            case "functionbar_down":
            case "functionbar_left":
            case "functionbar_right":
                int state = view.getControlKeyState();
                boolean invert = getInvertCursorDirection();
                String resStr = FunctionKey.getPreferenceId(v.getId());
                if ((!invert && "functionbar_up".equals(resStr)) ||
                        (!invert && "navigationbar_up".equals(resStr)) ||
                        (invert && "navigationbar_left".equals(resStr)) ||
                        (invert && "functionbar_left".equals(resStr))) {
                    doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_UP);
                } else if ((!invert && "functionbar_down".equals(resStr)) ||
                        (!invert && "navigationbar_down".equals(resStr)) ||
                        (invert && "navigationbar_right".equals(resStr)) ||
                        (invert && "functionbar_right".equals(resStr))) {
                    doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_DOWN);
                } else if ((!invert && "functionbar_left".equals(resStr)) ||
                        (!invert && "navigationbar_left".equals(resStr)) ||
                        (invert && "navigationbar_up".equals(resStr)) ||
                        (invert && "functionbar_up".equals(resStr))) {
                    doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_LEFT);
                } else if ((!invert && "functionbar_right".equals(resStr)) ||
                        (!invert && "navigationbar_right".equals(resStr)) ||
                        (invert && "navigationbar_down".equals(resStr)) ||
                        (invert && "functionbar_down".equals(resStr))) {
                    doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_RIGHT);
                }
                if (state == RELEASED && mSettings.getCursorDirectionControlMode() == 1) {
                    view.setControlKeyState(UNPRESSED);
                    mInvertCursorDirection = getDefaultInvertCursorDirection();
                    setCursorDirectionLabel();
                }
                break;
            case "navigationbar_page_up":
            case "functionbar_page_up":
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_PAGE_UP);
                break;
            case "navigationbar_page_down":
            case "functionbar_page_down":
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_PAGE_DOWN);
                break;
            case "navigationbar_backspace":
            case "functionbar_backspace":
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_DEL);
                break;
            case "navigationbar_enter":
            case "functionbar_enter":
                doSendActionBarKey(view, KeycodeConstants.KEYCODE_ENTER);
                break;
            case "navigationbar_i":
            case "functionbar_i":
                sendKeyStrings("i", false);
                if (!mHaveFullHwKeyboard) doShowSoftKeyboard();
                break;
            case "navigationbar_colon":
            case "functionbar_colon":
            case "functionbar_colon2":
                sendKeyStrings(":", false);
                break;
            case "navigationbar_slash":
            case "functionbar_slash":
                sendKeyStrings("/", false);
                break;
            case "navigationbar_equal":
            case "functionbar_equal":
                sendKeyStrings("=", false);
                break;
            case "navigationbar_asterisk":
            case "functionbar_asterisk":
                sendKeyStrings("*", false);
                break;
            case "navigationbar_pipe":
            case "functionbar_pipe":
                sendKeyStrings("|", false);
                break;
            case "navigationbar_plus":
            case "functionbar_plus":
                sendKeyStrings("+", false);
                break;
            case "navigationbar_minus":
            case "functionbar_minus":
                sendKeyStrings("-", false);
                break;
            case "navigationbar_vim_paste":
            case "functionbar_vim_paste":
                sendKeyStrings("\"*p", false);
                break;
            case "navigationbar_vim_yank":
            case "functionbar_vim_yank":
                sendKeyStrings("\"*yy" + "\u001b", false);
                break;
            case "navigationbar_menu_plus":
            case "functionbar_menu_plus":
                doSendActionBarKey(view, mSettings.getActionBarPlusKeyAction());
                break;
            case "navigationbar_menu_minus":
            case "functionbar_menu_minus":
                doSendActionBarKey(view, mSettings.getActionBarMinusKeyAction());
                break;
            case "navigationbar_menu_x":
            case "functionbar_menu_x":
                doSendActionBarKey(view, mSettings.getActionBarXKeyAction());
                break;
            case "navigationbar_menu_user":
            case "functionbar_menu_user":
                doSendActionBarKey(view, mSettings.getActionBarUserKeyAction());
                break;
            case "navigationbar_menu_quit":
            case "functionbar_menu_quit":
                doSendActionBarKey(view, mSettings.getActionBarQuitKeyAction());
                break;
            case "navigationbar_softkeyboard":
            case "functionbar_softkeyboard":
                doSendActionBarKey(view, mSettings.getActionBarIconKeyAction());
                break;
            case "navigationbar_invert":
            case "functionbar_invert":
                doSendActionBarKey(view, mSettings.getActionBarInvertKeyAction());
                break;
            case "navigationbar_menu":
            case "functionbar_menu":
                openOptionsMenu();
                break;
            case "navigationbar_fn_toggle":
            case "functionbar_menu_hide":
                setFunctionBar(2);
                break;
            case "navigationbar_menu_hide":
                setNavigationBar(0);
                break;
            case "functionbar_next0":
            case "functionbar_prev":
            case "functionbar_prev2":
            case "functionbar_next2":
                mFunctionBarId = mFunctionBarId == 0 ? 1 : 0;
                setFunctionKeyVisibility();
                break;
            case "functionbar_f1":
            case "functionbar_m1":
            case "navigationbar_f1":
                doSendActionBarFKey(view, KeycodeConstants.KEYCODE_F1, mCmd_FKEY[0]);
                break;
            case "functionbar_f2":
            case "functionbar_m2":
            case "navigationbar_f2":
                doSendActionBarFKey(view, KeycodeConstants.KEYCODE_F2, mCmd_FKEY[1]);
                break;
            case "functionbar_f3":
            case "functionbar_m3":
            case "navigationbar_f3":
                doSendActionBarFKey(view, KeycodeConstants.KEYCODE_F3, mCmd_FKEY[2]);
                break;
            case "functionbar_f4":
            case "functionbar_m4":
            case "navigationbar_f4":
                doSendActionBarFKey(view, KeycodeConstants.KEYCODE_F4, mCmd_FKEY[3]);
                break;
            case "functionbar_f5":
            case "functionbar_m5":
            case "navigationbar_f5":
                doSendActionBarFKey(view, KeycodeConstants.KEYCODE_F5, mCmd_FKEY[4]);
                break;
            case "navigationbar_f6":
            case "functionbar_f6":
            case "functionbar_m6":
                doSendActionBarFKey(view, KeycodeConstants.KEYCODE_F6, mCmd_FKEY[5]);
                break;
            case "functionbar_f7":
            case "functionbar_m7":
            case "navigationbar_f7":
                doSendActionBarFKey(view, KeycodeConstants.KEYCODE_F7, mCmd_FKEY[6]);
                break;
            case "functionbar_f8":
            case "functionbar_m8":
            case "navigationbar_f8":
                doSendActionBarFKey(view, KeycodeConstants.KEYCODE_F8, mCmd_FKEY[7]);
                break;
            case "functionbar_f9":
            case "functionbar_m9":
            case "navigationbar_f9":
                doSendActionBarFKey(view, KeycodeConstants.KEYCODE_F9, mCmd_FKEY[8]);
                break;
            case "functionbar_f10":
            case "functionbar_m10":
            case "navigationbar_f10":
                doSendActionBarFKey(view, KeycodeConstants.KEYCODE_F10, mCmd_FKEY[9]);
                break;
            case "functionbar_f11":
            case "functionbar_m11":
            case "navigationbar_f11":
                doSendActionBarFKey(view, KeycodeConstants.KEYCODE_F11, mCmd_FKEY[10]);
                break;
            case "functionbar_f12":
            case "functionbar_m12":
            case "navigationbar_f12":
                doSendActionBarFKey(view, KeycodeConstants.KEYCODE_F12, mCmd_FKEY[11]);
                break;
        }
    }

    boolean existsPlayStore() {
        return false;
    }

    public void onDebugButtonClicked(final View arg0) {
    }

    public void onExtraButtonClicked(View arg0) {
        doExtraContentsAction(this, arg0);
    }

    private void doExtraContentsAction(final AppCompatActivity activity, final View arg0) {
        String[] standardItems = {
            getString(R.string.extra_contents_action_clean)
        };

        final String[] items;
        items = standardItems;
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_extra_contents_dialog_title))
            .setSingleChoiceItems(items, -1, (dialog1, which) -> {
                dialog1.cancel();
                String item = items[which];
                if (getString(R.string.extra_contents_action_clean).equals(item)) {
                    uninstallExtraContents(activity, arg0);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void uninstallExtraContents(final AppCompatActivity activity, final View arg0) {
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        final AlertDialog.Builder r = new AlertDialog.Builder(activity.getApplicationContext());
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.confirm_uninstall_extra_contents);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            mUninstall = true;
            shell("rm " + TermService.getVersionFilesDir() + "/version");
            shell("rm -rf " + TermService.getVIMRUNTIME());
            if (mSettings.getInitialCommand().matches("(.|\n)*(^|\n)-?vim\\.app(.|\n)*")) {
                sendKeyStrings(AppCommand.QUIT, true);
            } else {
                doExitShell();
            }
        });
        b.setNegativeButton(android.R.string.no, (dialog, id) -> {
            dialog.dismiss();
            doExtraContentsAction(activity, arg0);
        });
        b.show();
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
        alert(null, message);
    }

    void alert(String title, String message) {
        try {
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            if (title != null) {
                bld.setTitle(title);
                bld.setIcon(android.R.drawable.ic_dialog_alert);
            }
            bld.setMessage(message);
            bld.setPositiveButton(android.R.string.ok, null);
            Log.d(TermDebug.LOG_TAG, "Showing alert dialog: " + message);
            bld.create().show();
        } catch (Exception e) {
            // Do nothing
        }
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

    private static class KeyEventSender {
        private int event;

        private class AsyncRunnable implements Runnable {
            Handler handler = new Handler(Looper.getMainLooper());

            @Override
            public void run() {
                try {
                    Instrumentation inst = new Instrumentation();
                    if (event == KEYEVENT_SENDER_SHIFT_SPACE) {
                        inst.sendKeySync(new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 1, KeyEvent.META_SHIFT_ON));
                    } else if (event == KEYEVENT_SENDER_ALT_SPACE) {
                        inst.sendKeySync(new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 1, KeyEvent.META_ALT_ON));
                    } else {
                        inst.sendKeyDownUpSync(event);
                    }
                } catch (Exception e) {
                    // Do nothing
                }
                handler.post(() -> onPostExecute());
            }
        }

        void onPreExecute() {
        }

        void execute(int senderKeyEvent) {
            event = senderKeyEvent;
            onPreExecute();
            ExecutorService executorService  = Executors.newSingleThreadExecutor();
            executorService.submit(new AsyncRunnable());
        }

        void onPostExecute() {
        }
    }

    private class EmulatorViewGestureListener extends SimpleOnGestureListener {
        private final int mDeltaColumnsEdge = 3;
        private final EmulatorView view;
        private float mDeltaColumnsReminder;

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

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            EmulatorView view = getCurrentEmulatorView();
            if (view == null) return false;
            if (Math.abs(distanceX) < Math.abs(distanceY)) return false;
            if (((int) e1.getY() < view.getVisibleHeight() / mDeltaColumnsEdge) && (mTermSessions.size() > 1)) return false;

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
                if (mTermSessions.size() == 1) return false;
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

        public EmulatorViewDoubleTapListener(EmulatorView view) {
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

    private int mTextPinch = 0;

    private class EmulatorViewScaleGestureListener extends SimpleOnScaleGestureListener {
        private final EmulatorView view;

        public EmulatorViewScaleGestureListener(EmulatorView view) {
            this.view = view;
        }

        @Override
        public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
            if (!mSettings.getPinchInOut()) return true;
            float scaleFactor = scaleGestureDetector.getScaleFactor();
            if (scaleFactor > 1.0f) {
                mTextPinch += 1;
            } else if (scaleFactor < 1.0f) {
                mTextPinch -= 1;
            }
            final float TEXT_SCALE_STEP = 0.025f;
            final int THRESHOLD = 1;
            if (mTextPinch - THRESHOLD >= 0) {
                mTextPinch -= THRESHOLD;
                EmulatorView.setTextScale(EmulatorView.getTextScale() * (1.0f + TEXT_SCALE_STEP));
                view.setFontSize();
            } else if (mTextPinch + THRESHOLD <= 0) {
                mTextPinch += THRESHOLD;
                EmulatorView.setTextScale(EmulatorView.getTextScale() * (1.0f - TEXT_SCALE_STEP));
                view.setFontSize();
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
            mTextPinch = 0;
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {
        }
    }

    private static class FunctionKey {
        private final static HashMap<String, String> preferenceMap = new HashMap<>();
        private final static HashMap<String, String> resourceMap = new HashMap<>();
        public String label;
        public String prefId;
        public int resId;
        public boolean defValue;

        static String getPreferenceId(int id) {
            String res = String.valueOf(id);
            if (preferenceMap.containsKey(res)) {
                return preferenceMap.get(res);
            }
            return null;
        }

        static int getResourceId(String id) {
            if (resourceMap.containsKey(id)) {
                String str = resourceMap.get(id);
                if (str != null) return Integer.parseInt(str);
                else return -1;
            }
            return -1;
        }

        FunctionKey(int resourceId, String preferenceId, String labelStr, boolean defaultValue) {
            label = labelStr;
            prefId = preferenceId;
            resId = resourceId;
            defValue = defaultValue;
            resourceMap.put(preferenceId, String.valueOf(resourceId));
            preferenceMap.put(String.valueOf(resourceId), preferenceId);
        }
    }

    private static class ExternalApp {
        public String appId;
        public String label;
        public int action;
        public int button;

        ExternalApp(int buttonId, String applicationId, String applicationName, int actionMode) {
            appId = applicationId;
            label = applicationName;
            action = actionMode;
            button = buttonId;
        }
    }

}

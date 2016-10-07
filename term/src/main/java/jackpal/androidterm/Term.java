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
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import jackpal.androidterm.compat.ActionBarCompat;
import jackpal.androidterm.compat.ActivityCompat;
import jackpal.androidterm.compat.AndroidCompat;
import jackpal.androidterm.compat.MenuItemCompat;
import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.emulatorview.UpdateCallback;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompat;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompatFactory;
import jackpal.androidterm.emulatorview.compat.KeycodeConstants;
import jackpal.androidterm.util.SessionList;
import jackpal.androidterm.util.TermSettings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.text.Collator;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A terminal emulator activity.
 */

public class Term extends Activity implements UpdateCallback, SharedPreferences.OnSharedPreferenceChangeListener, OnClickListener {
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

    private final static int SELECT_TEXT_ID = 0;
    private final static int COPY_ALL_ID = 1;
    private final static int PASTE_ID = 2;
    private final static int SEND_CONTROL_KEY_ID = 3;
    private final static int SEND_FN_KEY_ID = 4;
    private final static int SEND_FUNCTION_BAR_ID = 5;
    private final static int SEND_MENU_ID = 6;

    private boolean mAlreadyStarted = false;
    private boolean mStopServiceOnFinish = false;

    private Intent TSIntent;

    public static final int REQUEST_CHOOSE_WINDOW = 1;
    public static final String EXTRA_WINDOW_ID = "jackpal.androidterm.window_id";
    private int onResumeSelectWindow = -1;
    private ComponentName mPrivateAlias;

    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;
    // Available on API 12 and later
    private static final int WIFI_MODE_FULL_HIGH_PERF = 3;

    private boolean mBackKeyPressed;

    private static final String ACTION_PATH_BROADCAST = "jackpal.androidterm.broadcast.APPEND_TO_PATH";
    private static final String ACTION_PATH_PREPEND_BROADCAST = "jackpal.androidterm.broadcast.PREPEND_TO_PATH";
    private static final String PERMISSION_PATH_BROADCAST = "jackpal.androidterm.permission.APPEND_TO_PATH";
    private static final String PERMISSION_PATH_PREPEND_BROADCAST = "jackpal.androidterm.permission.PREPEND_TO_PATH";
    private int mPendingPathBroadcasts = 0;
    private BroadcastReceiver mPathReceiver;
    // Available on API 12 and later
    private static final int FLAG_INCLUDE_STOPPED_PACKAGES = 0x20;

    private TermService mTermService;
    private ServiceConnection mTSConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TermDebug.LOG_TAG, "Bound to TermService");
            TermService.TSBinder binder = (TermService.TSBinder) service;
            mTermService = binder.getService();
            if (mPendingPathBroadcasts <= 0) {
                populateViewFlipper();
                populateWindowList();
            }
        }

        public void onServiceDisconnected(ComponentName arg0) {
            mTermService = null;
        }
    };

    private ActionBarCompat mActionBar;
    private int mActionBarMode = TermSettings.ACTION_BAR_MODE_NONE;

    private WindowListAdapter mWinListAdapter;

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        mSettings.readPrefs(sharedPreferences);
    }

    private class WindowListActionBarAdapter extends WindowListAdapter implements UpdateCallback {
        // From android.R.style in API 13
        private static final int TextAppearance_Holo_Widget_ActionBar_Title = 0x01030112;

        public WindowListActionBarAdapter(SessionList sessions) {
            super(sessions);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView label = new TextView(Term.this);
            String title = getSessionTitle(position, getString(R.string.window_title, position + 1));
            label.setText(title);
            if (AndroidCompat.SDK >= 13) {
                label.setTextAppearance(Term.this, TextAppearance_Holo_Widget_ActionBar_Title);
            } else {
                label.setTextAppearance(Term.this, android.R.style.TextAppearance_Medium);
            }
            return label;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return super.getView(position, convertView, parent);
        }

        public void onUpdate() {
            notifyDataSetChanged();
            mActionBar.setSelectedNavigationItem(mViewFlipper.getDisplayedChild());
        }
    }

    private ActionBarCompat.OnNavigationListener mWinListItemSelected = new ActionBarCompat.OnNavigationListener() {
        public boolean onNavigationItemSelected(int position, long id) {
            int oldPosition = mViewFlipper.getDisplayedChild();
            if (position != oldPosition) {
                if (position >= mViewFlipper.getChildCount()) {
                    mViewFlipper.addView(createEmulatorView(mTermSessions.get(position)));
                }
                mViewFlipper.setDisplayedChild(position);
                if (mActionBarMode >= TermSettings.ACTION_BAR_MODE_HIDES) {
                    mActionBar.hide();
                }
            }
            return true;
        }
    };

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

            //Check for link at tap location
            String link = view.getURLat(e.getX(), e.getY());
            if(link != null)
                execURL(link);
            else
                doUIToggle((int) e.getX(), (int) e.getY(), view.getVisibleWidth(), view.getVisibleHeight());
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float absVelocityX = Math.abs(velocityX);
            float absVelocityY = Math.abs(velocityY);
            if (absVelocityX > Math.max(1000.0f, 2.0 * absVelocityY)) {
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
                return false;
            }
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
            if (keyCode == KeyEvent.KEYCODE_BACK && mActionBarMode >= TermSettings.ACTION_BAR_MODE_HIDES && mActionBar != null && mActionBar.isShowing()) {
                /* We need to intercept the key event before the view sees it,
                   otherwise the view will handle it before we get it */
                onKeyUp(keyCode, event);
                return true;
            } else {
                return false;
            }
        }
    };

    private Handler mHandler = new Handler();

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

        boolean vimflavor = this.getPackageName().matches(".*vim.androidterm.*");

        if (!vimflavor && mSettings.doPathExtensions()) {
            mPathReceiver = new BroadcastReceiver() {
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
                        populateWindowList();
                    }
                }
            };

            Intent broadcast = new Intent(ACTION_PATH_BROADCAST);
            if (AndroidCompat.SDK >= 12) {
                broadcast.addFlags(FLAG_INCLUDE_STOPPED_PACKAGES);
            }
            mPendingPathBroadcasts++;
            sendOrderedBroadcast(broadcast, PERMISSION_PATH_BROADCAST, mPathReceiver, null, RESULT_OK, null, null);

            if (mSettings.allowPathPrepend()) {
                broadcast = new Intent(broadcast);
                broadcast.setAction(ACTION_PATH_PREPEND_BROADCAST);
                mPendingPathBroadcasts++;
                sendOrderedBroadcast(broadcast, PERMISSION_PATH_PREPEND_BROADCAST, mPathReceiver, null, RESULT_OK, null, null);
            }
        }

        TSIntent = new Intent(this, TermService.class);
        startService(TSIntent);

        if (AndroidCompat.SDK >= 11) {
            int theme = mSettings.getColorTheme();
            int actionBarMode = mSettings.actionBarMode();
            mActionBarMode = actionBarMode;
            switch (actionBarMode) {
            case TermSettings.ACTION_BAR_MODE_ALWAYS_VISIBLE:
                if (theme == 0) {
                    setTheme(R.style.Theme_Holo);
                } else {
                    setTheme(R.style.Theme_Holo_Light);
                }
                break;
            case TermSettings.ACTION_BAR_MODE_HIDES+1:
            case TermSettings.ACTION_BAR_MODE_HIDES:
                if (theme == 0) {
                    setTheme(R.style.Theme_Holo_ActionBarOverlay);
                } else {
                    setTheme(R.style.Theme_Holo_Light_ActionBarOverlay);
                }
                break;
            }
        } else {
            mActionBarMode = TermSettings.ACTION_BAR_MODE_ALWAYS_VISIBLE;
        }

        setContentView(R.layout.term_activity);
        mViewFlipper = (TermViewFlipper) findViewById(VIEW_FLIPPER);
        setFunctionKeyListener();

        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TermDebug.LOG_TAG);
        WifiManager wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        int wifiLockMode = WifiManager.WIFI_MODE_FULL;
        if (AndroidCompat.SDK >= 12) {
            wifiLockMode = WIFI_MODE_FULL_HIGH_PERF;
        }
        mWifiLock = wm.createWifiLock(wifiLockMode, TermDebug.LOG_TAG);

        ActionBarCompat actionBar = ActivityCompat.getActionBar(this);
        if (actionBar != null) {
            mActionBar = actionBar;
            actionBar.setNavigationMode(ActionBarCompat.NAVIGATION_MODE_LIST);
            actionBar.setDisplayOptions(0, ActionBarCompat.DISPLAY_SHOW_TITLE);
            if (mActionBarMode >= TermSettings.ACTION_BAR_MODE_HIDES) {
                actionBar.hide();
            }
        }

        mHaveFullHwKeyboard = checkHaveFullHwKeyboard(getResources().getConfiguration());
        setSoftInputMode(mHaveFullHwKeyboard);

        if (mFunctionBar == -1) mFunctionBar = mSettings.showFunctionBar() ? 1 : 0;
        if (mFunctionBar == 1) setFunctionBar(mFunctionBar);

        updatePrefs();
        permissionCheckExternalStorage();
        mAlreadyStarted = true;
    }

    public static final int REQUEST_STORAGE = 1;

    @SuppressLint("NewApi")
    void permissionCheckExternalStorage() {
        if (AndroidCompat.SDK < 23) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    @Override
    @SuppressLint("NewApi")
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_STORAGE) {
            if (permissions.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
                    }
                }
            } else if (permissions.equals(Manifest.permission.READ_EXTERNAL_STORAGE))  {
                // do something
            } else {
//                Toast.makeText(this, "permission does not granted", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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

        if (!bindService(TSIntent, mTSConnection, BIND_AUTO_CREATE)) {
            throw new IllegalStateException("Failed to bind to TermService!");
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
            if (!mHaveFullHwKeyboard) {
                doShowSoftKeyboard();
            }
        }
    }

    private void populateWindowList() {
        if (mActionBar == null) {
            // Not needed
            return;
        }
        if (mTermSessions != null) {
            int position = mViewFlipper.getDisplayedChild();
            if (mWinListAdapter == null) {
                mWinListAdapter = new WindowListActionBarAdapter(mTermSessions);

                mActionBar.setListNavigationCallbacks(mWinListAdapter, mWinListItemSelected);
            } else {
                mWinListAdapter.setSessions(mTermSessions);
            }
            mViewFlipper.addCallback(mWinListAdapter);

            mActionBar.setSelectedNavigationItem(position);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);

        if (mStopServiceOnFinish) {
            stopService(TSIntent);
            mFunctionBar = -1;
        }
        mTermService = null;
        mTSConnection = null;
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        if (mWifiLock.isHeld()) {
            mWifiLock.release();
        }
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
        mFirst = false;
        return cmd;
    }

    private TermView createEmulatorView(TermSession session) {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        TermView emulatorView = new TermView(this, session, metrics);

        emulatorView.setExtGestureListener(new EmulatorViewGestureListener(emulatorView));
        emulatorView.setOnKeyListener(mKeyListener);
        registerForContextMenu(emulatorView);

        return emulatorView;
    }

    private TermSession getCurrentTermSession() {
        SessionList sessions = mTermSessions;
        if (sessions == null) {
            return null;
        } else {
            return sessions.get(mViewFlipper.getDisplayedChild());
        }
    }

    private EmulatorView getCurrentEmulatorView() {
        return (EmulatorView) mViewFlipper.getCurrentView();
    }

    private void updatePrefs() {
        ActivityCompat.invalidateOptionsMenu(this);
        mUseKeyboardShortcuts = mSettings.getUseKeyboardShortcutsFlag();

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        setFunctionKeyVisibility();
        mViewFlipper.updatePrefs(mSettings);

        for (View v : mViewFlipper) {
            ((EmulatorView) v).setDensity(metrics);
            ((TermView) v).updatePrefs(mSettings);
            setPreIMEShortsuts((EmulatorView) v);
            if (mSettings.useCookedIME() == false) {
                ((EmulatorView) v).setIMECtrlBeginBatchEditDisable(false);
            }
        }

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
            if (desiredFlag != (params.flags & FULLSCREEN) || (AndroidCompat.SDK >= 11 && mActionBarMode != mSettings.actionBarMode())) {
                if (mAlreadyStarted) {
                    // Can't switch to/from fullscreen after
                    // starting the activity.
                    restart();
                } else {
                    win.setFlags(desiredFlag, FULLSCREEN);
                    if (mActionBarMode >= TermSettings.ACTION_BAR_MODE_HIDES) {
                        if (mActionBar != null) {
                            mActionBar.hide();
                        }
                    }
                }
            }
        }

        int orientation = mSettings.getScreenOrientation();
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
    }

    private void setPreIMEShortsuts(EmulatorView v) {
        final SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean value = mPrefs.getBoolean("AltGrave", false);
        v.setPreIMEShortcut("AltGrave", value);
        value = mPrefs.getBoolean("AltEsc", false);
        v.setPreIMEShortcut("AltEsc", value);
        value = mPrefs.getBoolean("CtrlSpace", false);
        v.setPreIMEShortcut("CtrlSpace", value);
        value = mPrefs.getBoolean("ZENKAKU_HANKAKU", false);
        v.setPreIMEShortcut("ZENKAKU_HANKAKU", value);
        value = mPrefs.getBoolean("GRAVE", false);
        v.setPreIMEShortcut("GRAVE", value);
        value = mPrefs.getBoolean("SWITCH_CHARSET", false);
        v.setPreIMEShortcut("SWITCH_CHARSET", value);
        v.setPreIMEShortcutsAction(mSettings.getImeShortcutsAction());
    }

    @Override
    public void onPause() {
        super.onPause();

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
                imm.hideSoftInputFromWindow(token, 0);
            }
        }.start();
    }

    @Override
    protected void onStop() {
        mViewFlipper.onPause();
        if (mTermSessions != null) {
            mTermSessions.removeCallback(this);

            if (mWinListAdapter != null) {
                mTermSessions.removeCallback(mWinListAdapter);
                mTermSessions.removeTitleChangedListener(mWinListAdapter);
                mViewFlipper.removeCallback(mWinListAdapter);
            }
        }

        mViewFlipper.removeAllViews();

        unbindService(mTSConnection);

        super.onStop();
    }

    private boolean checkHaveFullHwKeyboard(Configuration c) {
        return (c.keyboard == Configuration.KEYBOARD_QWERTY) &&
            (c.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO);
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

        if (mWinListAdapter != null) {
            // Force Android to redraw the label in the navigation dropdown
            mWinListAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (mSettings.getActionBarPlusKeyAction() != 999) {
            MenuItemCompat.setShowAsAction(menu.findItem(R.id.menu_plus), MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
        } else {
            menu.removeItem(R.id.menu_plus);
        }
//        if (mSettings.getActionBarMinusKeyAction() != 999) {
//            MenuItemCompat.setShowAsAction(menu.findItem(R.id.menu_minus), MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
//        } else {
//            menu.removeItem(R.id.menu_minus);
//        }
        if (mSettings.getActionBarXKeyAction() != 999) {
            MenuItemCompat.setShowAsAction(menu.findItem(R.id.menu_x), MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
        } else {
            menu.removeItem(R.id.menu_x);
        }
//        if (mSettings.getActionBarUserKeyAction() != 999) {
//            MenuItemCompat.setShowAsAction(menu.findItem(R.id.menu_user), MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
//        } else {
//            menu.removeItem(R.id.menu_user);
//        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_preferences) {
            doPreferences();
        } else if (id == R.id.menu_paste) {
            doPaste();
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
            startActivityForResult(new Intent(this, WindowList.class), REQUEST_CHOOSE_WINDOW);
        } else if (id == R.id.menu_reset) {
            doResetTerminal();
            Toast toast = Toast.makeText(this,R.string.reset_toast_notification,Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        } else if (id == R.id.menu_send_email) {
            doEmailTranscript();
        } else if (id == R.id.menu_special_keys) {
            doDocumentKeys();
        } else if (id == R.id.menu_toggle_soft_keyboard) {
            doToggleSoftKeyboard();
        } else if (id == R.id.menu_toggle_function_bar) {
            setFunctionBar(2);
        } else if (id == R.id.menu_toggle_wakelock) {
            doToggleWakeLock();
        } else if (id == R.id.menu_toggle_wifilock) {
            doToggleWifiLock();
        } else if  (id == R.id.action_help) {
            Intent openHelp = new Intent(Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.help_url)));
                startActivity(openHelp);
        }
        // Hide the action bar if appropriate
        if (mActionBarMode >= TermSettings.ACTION_BAR_MODE_HIDES) {
            mActionBar.hide();
        }
        return super.onOptionsItemSelected(item);
    }

    private static boolean mVimApp = false;
    private boolean doSendActionBarKey(EmulatorView view, int key) {
        if (key == 999) {
            // do nothing
        } else if (key == 1002) {

            doToggleSoftKeyboard();
        } else if (key == 1249) {
            doPaste();
        } else if (key == 1250) {
            doCreateNewWindow();
        } else if (key == 1251) {
            if (mVimApp && mSettings.getInitialCommand().matches("(.|\n)*(^|\n)-vim\\.app(.|\n)*") && mTermSessions.size() == 1) {
                sendKeyStrings(":confirm qa\r", true);
            } else {
                confirmCloseWindow();
            }
        } else if (key == 1252) {
            InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        } else if (key == 1253) {
            sendKeyStrings(":confirm qa\r", true);
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
        } else if (key > 0) {
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, key);
            dispatchKeyEvent(event);
            event = new KeyEvent(KeyEvent.ACTION_UP, key);
            dispatchKeyEvent(event);
        }
        return true;
    }

    private void sendKeyStrings(String str, boolean esc) {
        TermSession session = getCurrentTermSession();
        if (session != null) {
            if (esc == true) {
                KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeycodeConstants.KEYCODE_ESCAPE);
                dispatchKeyEvent(event);
            }
            session.write(str);
        }
        return;
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
        if (mTermSessions.size() == 1) {
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

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        switch (request) {
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
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem wakeLockItem = menu.findItem(R.id.menu_toggle_wakelock);
        MenuItem wifiLockItem = menu.findItem(R.id.menu_toggle_wifilock);
        if (mWakeLock.isHeld()) {
            wakeLockItem.setTitle(R.string.disable_wakelock);
        } else {
            wakeLockItem.setTitle(R.string.enable_wakelock);
        }
        if (mWifiLock.isHeld()) {
            wifiLockItem.setTitle(R.string.disable_wifilock);
        } else {
            wifiLockItem.setTitle(R.string.enable_wifilock);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
      super.onCreateContextMenu(menu, v, menuInfo);
      menu.setHeaderTitle(R.string.edit_text);
      menu.add(0, SELECT_TEXT_ID, 0, R.string.select_text);
      menu.add(0, COPY_ALL_ID, 0, R.string.copy_all);
      menu.add(0, PASTE_ID, 0, R.string.paste);
      // menu.add(0, SEND_CONTROL_KEY_ID, 0, R.string.send_control_key);
      // menu.add(0, SEND_FN_KEY_ID, 0, R.string.send_fn_key);
      menu.add(0, SEND_MENU_ID, 0, R.string.title_functionbar_menu);
      // menu.add(0, SEND_FUNCTION_BAR_ID, 0, R.string.toggle_function_bar);
      if (!canPaste()) {
          menu.getItem(PASTE_ID).setEnabled(false);
      }
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
        case 0xfffffffe:
            if (mTermSessions.size() > 1) {
                return true;
            }
            // fall into next
        case 0xffffffff:
            if (mTermSessions.size() == 1) {
                doHideSoftKeyboard();
            }
            doCloseWindow();
            return true;
        case 0xffff0000:
            setFunctionBar(2);
            return true;
        case KeyEvent.KEYCODE_BACK:
            if (AndroidCompat.SDK < 5) {
                if (!mBackKeyPressed) {
                    /* This key up event might correspond to a key down
                       delivered to another activity -- ignore */
                    return false;
                }
                mBackKeyPressed = false;
            }
            if (mActionBarMode >= TermSettings.ACTION_BAR_MODE_HIDES && mActionBar != null && mActionBar.isShowing()) {
                mActionBar.hide();
                return true;
            }
            switch (mSettings.getBackKeyAction()) {
            case TermSettings.BACK_KEY_STOPS_SERVICE:
                mStopServiceOnFinish = true;
            case TermSettings.BACK_KEY_CLOSES_ACTIVITY:
                finish();
                return true;
            case TermSettings.BACK_KEY_CLOSES_WINDOW:
                doCloseWindow();
                return true;
            case TermSettings.BACK_KEY_TOGGLE_IME:
                doToggleSoftKeyboard();
                return true;
            default:
                return false;
            }
        case KeyEvent.KEYCODE_MENU:
            if (mActionBar != null && !mActionBar.isShowing()) {
                mActionBar.show();
                return true;
            } else {
                return super.onKeyUp(keyCode, event);
            }
        case 0xfffffff1:
            copyFileToClipboard(mSettings.getHomePath()+"/.clipboard");
            return true;
        case 0xffffffe0:
            setFunctionBar(0);
            return true;
        case 0xffffffe1:
            setFunctionBar(1);
            return true;
        case 0xfffffff2:
            setFunctionBar(2);
            return true;
        case 0xfffffff3:
            openOptionsMenu();
            return true;
        case 0xfffffff4:
        case 0xfffffff5:
            copyClipboardToFile(mSettings.getHomePath()+"/.clipboard");
            if (keyCode == 0xfffffff5) sendKeyStrings(":ATEMod _paste\r", true);
            return true;
        case 0xfffffff6:
        case 0xfffffff7:
            mVimPaste = (keyCode == 0xfffffff6) ? true : false;
            return true;
        case 0xfffffff8:
        case 0xfffffff9:
            mVimApp = (keyCode == 0xfffffff8) ? true : false;
            return true;
        case 0xfffffffa:
            clearClipBoard();
            return true;
        case 0xfffffffb:
            doAndroidIntent(mSettings.getHomePath() + "/.intent");
            return true;
        default:
            return super.onKeyUp(keyCode, event);
        }
    }

    private void doAndroidIntent(String filename) {
        if (filename == null) return;
        String str[] = new String[3];
        try {
            File file = new File(filename);
            BufferedReader br = new BufferedReader(new FileReader(file));

            for (int i = 0; i < 3; i++) {
                str[i] = br.readLine();
                if (str[i] == null) break;
            }
            br.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        String action = str[0];
        if (action == null || str[1] == null) return;

        TermSession session = getCurrentTermSession();
        if (session != null) {
            if (action.equalsIgnoreCase("activity")) {
                try {
                    startActivity(new Intent(this, Class.forName(str[1])));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            } else if (action.matches("^.*(VIEW|EDIT).*")) {
                Intent intent = new Intent(action);

                String MIME;
                if (str[2] != null) {
                    MIME = str[2];
                } else {
                    int ch = str[1].lastIndexOf('.');
                    String ext = (ch >= 0) ?str[1].substring(ch + 1) : null;
                    MIME = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
                    if (MIME != null && !MIME.equals("")){
                         intent.setType(MIME);
                    } else {
                        Log.e("CreateIntent","MIME is Error");
                    }
                }
                intent.setDataAndType(Uri.parse(str[1]), MIME);
                startActivity(intent);
            }
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
                CharBuffer cbuf = Charset.forName("UTF-8").newDecoder().decode(bbuf);
                String str = cbuf.toString();
                ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                        .getManager(getApplicationContext());
                clip.setText(str);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void copyClipboardToFile(String filename) {
        if (filename == null) return;
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(filename);
            FileChannel fc = fos.getChannel();
            try {
                ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                        .getManager(getApplicationContext());
                if (clip.hasText()) {
                    ByteBuffer by = ByteBuffer.wrap(clip.getText().toString().getBytes());
                    fc.write(by);
                }
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
        if (clip.hasText()) {
            return true;
        }
        return false;
    }

    private void doPreferences() {
        startActivity(new Intent(this, TermPreferences.class));
    }

    private void doResetTerminal() {
        TermSession session = getCurrentTermSession();
        if (session != null) {
            session.reset();
        }
        getCurrentEmulatorView().reset();
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

    private void doCopyAll() {
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        clip.setText(getCurrentTermSession().getTranscriptText().trim());
    }

    private static boolean mVimPaste = false;
    private void doPaste() {
        if (mVimPaste && mSettings.getInitialCommand().matches("(.|\n)*(^|\n)-vim\\.app(.|\n)*") && mTermSessions.size() == 1) {
            sendKeyStrings("\"*p", false);
            return;
        }
        if (!canPaste()) {
            return;
        }
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        CharSequence paste = clip.getText();
        getCurrentTermSession().write(paste.toString());
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

    private void doToggleSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager)
            getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);

    }

    private void doShowSoftKeyboard() {
        if (getCurrentEmulatorView() == null) return;
        Activity activity = this;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(getCurrentEmulatorView(), InputMethodManager.SHOW_FORCED);
    }

    private void doHideSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager)
            getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
    }

    private void doToggleWakeLock() {
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        } else {
            mWakeLock.acquire();
        }
        ActivityCompat.invalidateOptionsMenu(this);
    }

    private void doToggleWifiLock() {
        if (mWifiLock.isHeld()) {
            mWifiLock.release();
        } else {
            mWifiLock.acquire();
        }
        ActivityCompat.invalidateOptionsMenu(this);
    }

    private void doToggleActionBar() {
        ActionBarCompat bar = mActionBar;
        if (bar == null) {
            return;
        }
        if (bar.isShowing()) {
            bar.hide();
        } else {
            bar.show();
        }
    }

    private void doUIToggle(int x, int y, int width, int height) {
        switch (mActionBarMode) {
        case TermSettings.ACTION_BAR_MODE_NONE:
            if (AndroidCompat.SDK >= 11 && (mHaveFullHwKeyboard || y < height / 2)) {
                openOptionsMenu();
                return;
            } else {
                doToggleSoftKeyboard();
            }
            break;
        case TermSettings.ACTION_BAR_MODE_HIDES+1:
        case TermSettings.ACTION_BAR_MODE_ALWAYS_VISIBLE:
            if (!mHaveFullHwKeyboard) {
                doToggleSoftKeyboard();
            }
            break;
        case TermSettings.ACTION_BAR_MODE_HIDES:
            if (mHaveFullHwKeyboard || y < height / 2) {
                doToggleActionBar();
                return;
            } else {
                doToggleSoftKeyboard();
            }
            break;
        }
        getCurrentEmulatorView().requestFocus();
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

    public boolean setDevBoolean(Context context, String key, boolean value) {
        SharedPreferences pref = context.getSharedPreferences("dev", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(key, value);
        editor.commit();
        return value;
    }

    public boolean getDevBoolean(Context context, String key, boolean defValue) {
        SharedPreferences pref = context.getSharedPreferences("dev", Context.MODE_PRIVATE);
        return pref.getBoolean(key, defValue);
    }

    private static int mFunctionBar = -1;
    private void setFunctionBar(int mode) {
        if (mode == 2) mFunctionBar = mFunctionBar == 0 ? 1 : 0;
        else mFunctionBar = mode;
        if (mAlreadyStarted) updatePrefs();
    }

    private void setFunctionBarSize() {
        int size = findViewById(R.id.view_function_bar).getHeight();
        if (mViewFlipper != null) mViewFlipper.setFunctionBarSize(size);
    }

    private void setFunctionKeyListener() {
        findViewById(R.id.button_esc  ).setOnClickListener(this);
        findViewById(R.id.button_ctrl ).setOnClickListener(this);
        findViewById(R.id.button_alt ).setOnClickListener(this);
        findViewById(R.id.button_tab  ).setOnClickListener(this);
        findViewById(R.id.button_up   ).setOnClickListener(this);
        findViewById(R.id.button_down ).setOnClickListener(this);
        findViewById(R.id.button_left ).setOnClickListener(this);
        findViewById(R.id.button_right).setOnClickListener(this);
        findViewById(R.id.button_backspace).setOnClickListener(this);
        findViewById(R.id.button_enter).setOnClickListener(this);
        findViewById(R.id.button_i).setOnClickListener(this);
        findViewById(R.id.button_colon).setOnClickListener(this);
        findViewById(R.id.button_slash).setOnClickListener(this);
        findViewById(R.id.button_equal).setOnClickListener(this);
        findViewById(R.id.button_asterisk).setOnClickListener(this);
        findViewById(R.id.button_pipe).setOnClickListener(this);
        findViewById(R.id.button_minus).setOnClickListener(this);
        findViewById(R.id.button_vim_paste).setOnClickListener(this);
        findViewById(R.id.button_vim_yank).setOnClickListener(this);
        findViewById(R.id.button_softkeyboard).setOnClickListener(this);
        findViewById(R.id.button_menu).setOnClickListener(this);
        findViewById(R.id.button_menu_hide).setOnClickListener(this);
        findViewById(R.id.button_menu_plus ).setOnClickListener(this);
        findViewById(R.id.button_menu_minus).setOnClickListener(this);
        findViewById(R.id.button_menu_x    ).setOnClickListener(this);
        findViewById(R.id.button_menu_user ).setOnClickListener(this);
        findViewById(R.id.button_menu_quit ).setOnClickListener(this);
    }

    private void setFunctionKeyVisibility() {
        int visibility;
        final SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        visibility = mPrefs.getBoolean("functionbar_esc", true) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_esc, visibility);
        visibility = mPrefs.getBoolean("functionbar_ctrl", true) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_ctrl, visibility);
        visibility = mPrefs.getBoolean("functionbar_alt", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_alt, visibility);
        visibility = mPrefs.getBoolean("functionbar_tab", true) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_tab, visibility);

        visibility = mPrefs.getBoolean("functionbar_up", true) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_up, visibility);
        visibility = mPrefs.getBoolean("functionbar_down", true) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_down, visibility);
        visibility = mPrefs.getBoolean("functionbar_left", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_left, visibility);
        visibility = mPrefs.getBoolean("functionbar_right", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_right, visibility);

        visibility = mPrefs.getBoolean("functionbar_backspace", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_backspace, visibility);
        visibility = mPrefs.getBoolean("functionbar_enter", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_enter, visibility);

        visibility = mPrefs.getBoolean("functionbar_i", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_i, visibility);
        visibility = mPrefs.getBoolean("functionbar_colon", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_colon, visibility);
        visibility = mPrefs.getBoolean("functionbar_slash", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_slash, visibility);
        visibility = mPrefs.getBoolean("functionbar_equal", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_equal, visibility);
        visibility = mPrefs.getBoolean("functionbar_asterisk", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_asterisk, visibility);
        visibility = mPrefs.getBoolean("functionbar_pipe", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_pipe, visibility);
        visibility = mPrefs.getBoolean("functionbar_minus", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_minus, visibility);
        visibility = mPrefs.getBoolean("functionbar_vim_paste", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_vim_paste, visibility);
        visibility = mPrefs.getBoolean("functionbar_vim_yank", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_vim_yank, visibility);

        visibility = mPrefs.getBoolean("functionbar_menu", true) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_menu, visibility);
        visibility = mPrefs.getBoolean("functionbar_softkeyboard", true) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_softkeyboard, visibility);
        visibility = mPrefs.getBoolean("functionbar_hide", true) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_menu_hide, visibility);

        visibility = View.GONE;
        // visibility = mPrefs.getBoolean("functionbar_menu_plus", false)  ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_menu_plus, visibility);
        // visibility = mPrefs.getBoolean("functionbar_menu_minus", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_menu_minus, visibility);
        // visibility = mPrefs.getBoolean("functionbar_menu_x", false) ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_menu_x, visibility);
        // visibility = mPrefs.getBoolean("functionbar_menu_user", false)  ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_menu_user, visibility);
        visibility = mPrefs.getBoolean("functionbar_menu_quit", true)  ? View.VISIBLE : View.GONE;
        setFunctionBarButton(R.id.button_menu_quit, visibility);

        setFunctionBarSize();
        visibility = mFunctionBar == 1 ? View.VISIBLE : View.GONE;
        findViewById(R.id.view_function_bar).setVisibility(visibility);
        mViewFlipper.setFunctionBar(mFunctionBar == 1);
    }

    @SuppressLint("NewApi")
    private void setFunctionBarButton(int id, int visibility) {
        Button button = (Button)findViewById(id);
        button.setVisibility(visibility);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int height = mSettings.getFontSize() * (int) (metrics.density * metrics.scaledDensity);
        button.setMinHeight(height);
        if (AndroidCompat.SDK >= 14) {
            button.setAllCaps(false);
        }
    }

    public void onClick(View v) {
        EmulatorView view = getCurrentEmulatorView();
        switch (v.getId()) {
        case R.id.button_esc:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_ESCAPE);
            break;
        case R.id.button_ctrl:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_CTRL_LEFT);
            break;
        case R.id.button_alt:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_ALT_LEFT);
            break;
        case R.id.button_tab:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_TAB);
            break;
        case R.id.button_up:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_UP);
            break;
        case R.id.button_down:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_DOWN);
            break;
        case R.id.button_left:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_LEFT);
            break;
        case R.id.button_right:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_DPAD_RIGHT);
            break;
        case R.id.button_backspace:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_DEL);
            break;
        case R.id.button_enter:
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_ENTER);
            break;
        case R.id.button_i:
            sendKeyStrings("i", false);
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
            sendKeyStrings("\"*yy", false);
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
        case R.id.button_menu:
            openOptionsMenu();
            break;
        case R.id.button_menu_hide:
            setFunctionBar(2);
            break;
        }
    }

}

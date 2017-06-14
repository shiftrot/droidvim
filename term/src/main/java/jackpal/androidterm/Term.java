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
import android.app.ProgressDialog;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.system.Os;
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
import jackpal.androidterm.util.IabHelper;
import jackpal.androidterm.util.IabResult;
import jackpal.androidterm.util.Inventory;
import jackpal.androidterm.util.Purchase;
import jackpal.androidterm.util.SessionList;
import jackpal.androidterm.util.TermSettings;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

    private final static int PASTE_ID = 0;
    private final static int COPY_ALL_ID = 1;
    private final static int SELECT_TEXT_ID = 2;
    private final static int SEND_CONTROL_KEY_ID = 3;
    private final static int SEND_FN_KEY_ID = 4;
    private final static int SEND_FUNCTION_BAR_ID = 5;
    private final static int SEND_MENU_ID = 6;

    private boolean mAlreadyStarted = false;
    private boolean mStopServiceOnFinish = false;

    private final static boolean FLAVOR_VIM = TermVimInstaller.FLAVOR_VIM;
    private static boolean mVimFlavor = FLAVOR_VIM;

    private Intent TSIntent;

    public static final int REQUEST_CHOOSE_WINDOW = 1;
    public static final int REQUEST_FILE_PICKER = 2;
    public static final int REQUEST_FILE_DELETE = 3;
    public static final int REQUEST_BILLING = 4;
    public static final int REQUEST_DROPBOX = 5;
    public static final int REQUEST_GOOGLEDRIVE = 6;
    public static final String EXTRA_WINDOW_ID = "jackpal.androidterm.window_id";
    private int onResumeSelectWindow = -1;
    private ComponentName mPrivateAlias;

    private boolean mBackKeyPressed;

    private static final String ACTION_PATH_BROADCAST = "jackpal.androidterm.broadcast.APPEND_TO_PATH";
    private static final String ACTION_PATH_PREPEND_BROADCAST = "jackpal.androidterm.broadcast.PREPEND_TO_PATH";
    private static final String PERMISSION_PATH_BROADCAST = "jackpal.androidterm.permission.APPEND_TO_PATH";
    private static final String PERMISSION_PATH_PREPEND_BROADCAST = "jackpal.androidterm.permission.PREPEND_TO_PATH";
    private int mPendingPathBroadcasts = 0;
    private BroadcastReceiver mPathReceiver;
    // Available on API 12 and later
    private static final int FLAG_INCLUDE_STOPPED_PACKAGES = 0x20;

    private static final String APP_DROPBOX     = "com.dropbox.android";
    private static final String APP_GOOGLEDRIVE = "com.google.android.apps.docs";
    private static final String APP_ONEDRIVE    = "com.microsoft.skydrive";

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
                label.setTextAppearance(Term.this, android.R.style.TextAppearance_Holo_Widget_ActionBar_Title);
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

    private class EmulatorViewDoubleTapListener implements GestureDetector.OnDoubleTapListener {
        private EmulatorView view;

        public EmulatorViewDoubleTapListener(EmulatorView view) {
            this.view = view;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            Log.w(TAG, "onSingleTapConfirmed");
            return false;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            Log.w(TAG, "onDoubleTapEvent");
            return doDoubleTapAction(e);
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            Log.w(TAG, "onDoubleTapEvent");
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

            if (!mVimFlavor && mSettings.allowPathPrepend()) {
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
        if (mOnelineTextBox == -1) mOnelineTextBox = mSettings.showOnelineTextBox() ? 1 : 0;
        initOnelineTextBox(mOnelineTextBox);

        updatePrefs();
        mIabHelperDisable = !existsPlayStore();
//        if (!mIabHelperDisable) iabSetup();
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
        int visibilty = View.VISIBLE;
        if (mIabHelperDisable || !FLAVOR_VIM) visibilty = View.GONE;
        button.setVisibility(visibilty);
        if (mIsPremium) {
            button.setText(getString(R.string.extra_button));
            button.setBackgroundResource(R.drawable.sidebar_button);
        } else {
            button.setText(getString(R.string.extra_button));
        }
    }

    private void setDebugButton() {
        if (!BuildConfig.DEBUG) return;
        Button button = (Button)findViewById(R.id.drawer_debug_button);
        button.setVisibility(View.VISIBLE);
    }

    ArrayList<String> mFilePickerItems;
    private void setDrawerButtons() {
        if (FLAVOR_VIM) {
            mFilePickerItems = new ArrayList<>();
            mFilePickerItems.add(this.getString(R.string.create_file));
            mFilePickerItems.add(this.getString(R.string.delete_file));
            Button button;
            if (isAppInstalled(APP_DROPBOX)) {
                button = (Button)findViewById(R.id.drawer_dropbox_button);
                button.setVisibility(View.VISIBLE);
                mFilePickerItems.add(this.getString(R.string.dropbox));
            }
            if (isAppInstalled(APP_GOOGLEDRIVE)) {
                button = (Button) findViewById(R.id.drawer_googledrive_button);
                button.setVisibility(View.VISIBLE);
                mFilePickerItems.add(this.getString(R.string.googledrive));
            }
            if (isAppInstalled(APP_ONEDRIVE)) {
                // button = (Button) findViewById(R.id.drawer_onedrive_button);
                // button.setVisibility(View.VISIBLE);
                mFilePickerItems.add(this.getString(R.string.onedrive));
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
        findViewById(R.id.drawer_extra_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                onExtraButtonClicked(v);
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
                getDrawer().closeDrawers();
                dropboxFilePicker(mSettings.getDropboxFilePicker());
            }
        });
        findViewById(R.id.drawer_googledrive_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                googleDriveFilePicker(mSettings.getGoogleDriveFilePicker());
            }
        });
        findViewById(R.id.drawer_onedrive_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getDrawer().closeDrawers();
                intentMainActivity(APP_ONEDRIVE);
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
                confirmClearCache(false);
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
                if (FLAVOR_VIM && mSettings.getInitialCommand().matches("(.|\n)*(^|\n)-vim\\.app(.|\n)*") && mTermSessions.size() == 1) {
                    sendKeyStrings(":confirm qa\r", true);
                } else {
                    confirmCloseWindow();
                }
            }
        });
    }

    private boolean isAppInstalled(String appPackage) {
        PackageManager packageManager = getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(appPackage);
        return (intent != null);
    }

    private void intentMainActivity(String app) {
        PackageManager pm = this.getApplicationContext().getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(app);
        if (intent == null) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.setAction("android.intent.category.LAUNCHER");
            switch (app) {
                case APP_DROPBOX:
                    intent.setClassName(APP_DROPBOX, "com.dropbox.android.activity.DropboxBrowser");
                    break;
                case APP_GOOGLEDRIVE:
                    intent.setClassName(APP_GOOGLEDRIVE, "com.google.android.apps.docs.app.NewMainProxyActivity");
                    break;
                case APP_ONEDRIVE:
                    intent.setClassName(APP_ONEDRIVE, "com.microsoft.skydrive.MainActivity");
                    break;
                default:
                    break;
            }
        }
        startActivity(intent);
    }

    @SuppressLint("NewApi")
    private int dropboxFilePicker(int mode) {
        if (mode == 0) {
            intentMainActivity(APP_DROPBOX);
        } else {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setPackage(APP_DROPBOX);
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_DROPBOX);
        }
        return 1;
    }

    @SuppressLint("NewApi")
    private int googleDriveFilePicker(int mode) {
        if (mode == 0) {
            intentMainActivity(APP_GOOGLEDRIVE);
        } else {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setPackage(APP_GOOGLEDRIVE);
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_GOOGLEDRIVE);
        }
        return 1;
    }

    private boolean installGit() {
        return true;
    }

    private void confirmClearCache(final boolean clearAll) {
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.confirm_clear_cache_message);
        final Runnable clearCache = new Runnable() {
            public void run() {
                if (mSyncFileObserver != null) mSyncFileObserver.clearCache();
                if (mTermService != null && clearAll) mTermService.clearTMPDIR();
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

    @SuppressLint("NewApi")
    void permissionCheckExternalStorage() {
        if (AndroidCompat.SDK < 23) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    @Override
    @SuppressLint("NewApi")
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
        case REQUEST_STORAGE:
            for (int i = 0; i < permissions.length ; i++) {
                if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        // do something
                    } else {
                        // Toast.makeText(this, "permission does not granted", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            break;
        case REQUEST_FILE_PICKER:
            for (int i = 0; i < permissions.length ; i++) {
                if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    intentFilePicker();
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

        if (!bindService(TSIntent, mTSConnection, BIND_AUTO_CREATE)) {
            throw new IllegalStateException("Failed to bind to TermService!");
        }
    }

    private static String RELOAD_STYLE_ACTION = "com.termux.app.reload_style";
    private static String PURCHASES_UPDATED = "com.android.vending.billing.PURCHASES_UPDATED";
    private final BroadcastReceiver mBroadcastReceiever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(RELOAD_STYLE_ACTION)) {
                String stringExtra = intent.getStringExtra(RELOAD_STYLE_ACTION);
                if (stringExtra.equals("storage")) {
                    setupStorageSymlinks(Term.this);
                    return;
                }
            } else if (action.equals(PURCHASES_UPDATED)) {
                if (mIabHelper != null) mIabHelper.queryInventoryAsync(mGotInventoryListener);
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
            if (!mHaveFullHwKeyboard) {
                doShowSoftKeyboard();
            }
        }
    }

    private void showAds(boolean show) {
        if (BuildConfig.DEBUG) return;
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

        saveSyncFileObserver();
        if (mStopServiceOnFinish) {
            stopService(TSIntent);
            mFunctionBar = -1;
        }
        mTermService = null;
        mTSConnection = null;
        if (mIabHelper != null) {
            mIabHelper.dispose();
            mIabHelper = null;
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
        if (!FLAVOR_VIM) {
            TermVimInstaller.doInstallTerm(Term.this);
            permissionCheckExternalStorage();
        } else if (TermVimInstaller.doInstallVim) {
            mIabHelperDisable = true;
            final boolean vimApp = cmd.replaceAll(".*\n", "").matches("vim.app\\s*");
            cmd = cmd.replaceAll("\n-?vim.app", "");
            TermVimInstaller.installVim(Term.this, new Runnable(){
                @Override
                public void run() {
                    if (vimApp) sendKeyStrings("vim.app\n", false);
                    permissionCheckExternalStorage();
                    mIabHelperDisable = !existsPlayStore();
                    if (!mIabHelperDisable) setExtraButton();
                }
            });
        } else {
            doWarningVimrc();
            permissionCheckExternalStorage();
        }
        mFirst = false;
        return cmd;
    }

    private void doWarningVimrc() {
        if (!mVimFlavor) return;
        final String home = TermService.getHOME();
        if (new File(home+"/.vimrc").exists()) return;
        doWarningDialog(this.getString(R.string.vimrc_warning_title), this.getString(R.string.vimrc_warning), "do_warning_vimrc");
    }

    private void doWarningDialog(String title, String message, String key) {
        boolean warning = getDevBoolean(Term.this, key, true);
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
        final CheckBox dontShowAgain = (CheckBox)view.findViewById(R.id.dont_show_again);
        final String warningKey = key;
        dontShowAgain.setChecked(true);
        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int m) {
                if (dontShowAgain.isChecked()) {
                    setDevBoolean(Term.this, warningKey, false);
                }
            }
        });
        builder.create().show();
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
            emulatorView.setImeShortcutsAction(mSettings.getmImeDefaultInputtype());
            mFirstInputtype = false;
        }
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

        setEditTextVisibility();
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
        super.onPause();

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
                imm.hideSoftInputFromWindow(token, 0);
            }
        }.start();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mDoResetTerminal) doResetTerminal();

        registerReceiver(mBroadcastReceiever, new IntentFilter(PURCHASES_UPDATED));
        RELOAD_STYLE_ACTION = getPackageName()+".app.reload_style";
        registerReceiver(mBroadcastReceiever, new IntentFilter(RELOAD_STYLE_ACTION));

       if (existsPlayStore()) {
           if (mIabHelper == null) {
               iabSetup();
           } else {
               // for promotion code
               if (isConnected(this.getApplicationContext())) mIabHelper.queryInventoryAsync(mGotInventoryListener);
           }
       }
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
        doWarningDialog(null, this.getString(R.string.keyboard_warning), "do_warning_hw_keyboard");
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
        if (!FLAVOR_VIM) {
            menu.removeItem(R.id.menu_edit_vimrc);
        }
        return true;
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
            startActivityForResult(new Intent(this, WindowList.class), REQUEST_CHOOSE_WINDOW);
        } else if (id == R.id.menu_reset) {
            doResetTerminal();
            updatePrefs();
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
        } else if (id == R.id.menu_update) {
            networkUpdate();
        } else if (id == R.id.menu_tutorial) {
            sendKeyStrings(":Vimtutor\r", true);
        } else if (id == R.id.menu_toggle_wakelock) {
            doToggleWakeLock();
        } else if (id == R.id.menu_toggle_wifilock) {
            doToggleWifiLock();
        } else if (id == R.id.menu_edit_vimrc) {
            sendKeyStrings(":exe $MYVIMRC == '' ? 'e $HOME/.vimrc' : 'e $MYVIMRC'\r", true);
        } else if (id == R.id.menu_text_box) {
            setEditTextView(2);
        } else if (id == R.id.menu_reload) {
            fileReload();
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
        if (key == 999) {
            // do nothing
        } else if (key == 1002) {
            doToggleSoftKeyboard();
        } else if (key == 1249) {
            doPaste();
        } else if (key == 1250) {
            doCreateNewWindow();
        } else if (key == 1251) {
            if (FLAVOR_VIM && mSettings.getInitialCommand().matches("(.|\n)*(^|\n)-vim\\.app(.|\n)*") && mTermSessions.size() == 1) {
                sendKeyStrings(":confirm qa\r", true);
            } else {
                confirmCloseWindow();
            }
        } else if (key == 1252) {
            InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        } else if (key == 1253) {
            if (FLAVOR_VIM) {
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
            view.doImeShortcutsAction();
        } else if (key == 1261) {
            setEditTextView(2);
        } else if (key >= 1351 && key <= 1353) {
            view.doImeShortcutsAction(key-1300);
        } else if (key == 1355) {
            toggleDrawer();
        } else if (key == KeycodeConstants.KEYCODE_ESCAPE) {
            view.restartInputGoogleIme();
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, key);
            dispatchKeyEvent(event);
            event = new KeyEvent(KeyEvent.ACTION_UP, key);
            dispatchKeyEvent(event);
        } else if (key > 0) {
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, key);
            dispatchKeyEvent(event);
            event = new KeyEvent(KeyEvent.ACTION_UP, key);
            dispatchKeyEvent(event);
        }
        return true;
    }

    private void toggleDrawer() {
        DrawerLayout mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else {
            mDrawer.openDrawer(GravityCompat.START);
        }
    }

    private void sendKeyStrings(String str, boolean esc) {
        TermSession session = getCurrentTermSession();
        if (session != null) {
            if (esc) str = "\u001b"+str;
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

    private void filePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_FILE_PICKER);
        } else {
            intentFilePicker();
        }
    }

    private void intentFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_FILE_PICKER);
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
                if (Term.this.getString(R.string.create_file).equals(item)) {
                    fileCreate();
                } else if (Term.this.getString(R.string.delete_file).equals(item)) {
                    fileDelete();
                } else if (Term.this.getString(R.string.dropbox).equals(item)) {
                    intentMainActivity(APP_DROPBOX);
                } else if (Term.this.getString(R.string.googledrive).equals(item)) {
                    intentMainActivity(APP_GOOGLEDRIVE);
                } else if (Term.this.getString(R.string.onedrive).equals(item)) {
                    intentMainActivity(APP_ONEDRIVE);
                } else if (Term.this.getString(R.string.clear_cache).equals(item)) {
                    confirmClearCache(false);
                }
            }
        }).show();
}

    private void fileDelete() {
        permissionCheckExternalStorage();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_FILE_DELETE);
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
                DocumentsContract.deleteDocument(getContentResolver(), uri);
            }
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void fileCreate() {
        permissionCheckExternalStorage();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "Newfile.txt");
        startActivityForResult(intent, REQUEST_FILE_PICKER);
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
            case REQUEST_DROPBOX:
            case REQUEST_GOOGLEDRIVE:
            case REQUEST_FILE_PICKER:
                String path = null;
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    path = getPath(this, uri);
                    if (path == null) {
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
                    }
                }
                if (path != null) {
                    path = path.replaceAll(SHELL_ESCAPE, "\\\\$1");
                    path = String.format(":e %s\r", path);
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
            case REQUEST_BILLING:
                Log.d(TAG, "onActivityResult(" + request + "," + result + "," + data);
                if (mIabHelper == null) return;

                // Pass on the activity result to the helper for handling
                if (!mIabHelper.handleActivityResult(request, result, data)) {
                    // not handled, so handle it ourselves (here's where you'd
                    // perform any handling of activity results not related to in-app
                    // billing...
                    super.onActivityResult(request, result, data);
                } else {
                    Log.d(TAG, "onActivityResult handled by IABUtil.");
                }
                break;
        }
    }

    public static String getPath(final Context context, final Uri uri) {
        if (uri.toString().matches("^file:///.*")) {
            String path = uri.getPath();
            if (new File(path).canRead()) {
                return path;
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return null;
        }
        if (DocumentsContract.isDocumentUri(context, uri)) {
            try {
                File file = new File(DocumentsContract.getDocumentId(uri));
                if (file.exists()) return file.toString();
            } catch (Exception e){
                Log.d("FilePicker", e.toString());
            }
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
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
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem wakeLockItem = menu.findItem(R.id.menu_toggle_wakelock);
        MenuItem wifiLockItem = menu.findItem(R.id.menu_toggle_wifilock);
        if (!FLAVOR_VIM) menu.removeItem(R.id.menu_reload);
        if (!FLAVOR_VIM) menu.removeItem(R.id.menu_tutorial);
        menu.removeItem(R.id.menu_toggle_wakelock);
        menu.removeItem(R.id.menu_toggle_wifilock);
        menu.removeItem(R.id.menu_window_list);
        menu.removeItem(R.id.menu_toggle_soft_keyboard);
        menu.removeItem(R.id.menu_special_keys);
        menu.removeItem(R.id.menu_send_email);
        menu.removeItem(R.id.menu_update);
        menu.removeItem(R.id.action_help);
        return super.onPrepareOptionsMenu(menu);
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
            if (onelineTextBoxEsc()) {
                return true;
            }
            if (mActionBarMode >= TermSettings.ACTION_BAR_MODE_HIDES && mActionBar != null && mActionBar.isShowing()) {
                mActionBar.hide();
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
            if (mActionBar != null && !mActionBar.isShowing()) {
                mActionBar.show();
                return true;
            } else {
                return super.onKeyUp(keyCode, event);
            }
        case 0xffffffc0:
            if (mSettings.getBackKeyAction() == TermSettings.BACK_KEY_TOGGLE_IME_ESC) {
                sendKeyStrings("\u001b", false);
            }
            break;
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setupStorageSymlinks(this);
            }
            return true;
        case 0xfffffff9:
            EditText editText = (EditText) findViewById(R.id.text_input);
            if (mEditTextView && editText != null && !editText.isFocused()) {
                editText.requestFocus();
            } else {
                setEditTextView(2);
            }
            return true;
        case 0xfffffffa:
            clearClipBoard();
            return true;
        case 0xfffffffb:
            doAndroidIntent(mSettings.getHomePath() + "/.intent");
            return true;
        case 0xfffffffc:
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
            int bottomAction = mSettings.getBottomDoubleTapAction();

            // if (mFunctionBar == 1 && rightAction == 1261 && mEditTextView) rightAction = 999;
            // if (mFunctionBar == 1 && bottomAction == 1261 && mEditTextView) bottomAction = 999;
            if (rightAction != 999 && (me.getX() > (width - size))) {
                doSendActionBarKey(getCurrentEmulatorView(), rightAction);
            } else if (bottomAction != 999 && me.getY() > (height - size)) {
                doSendActionBarKey(getCurrentEmulatorView(), bottomAction);
            } else {
                doSendActionBarKey(getCurrentEmulatorView(), mSettings.getDoubleTapAction());
            }
            return true;
        }
        return false;
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
        String mime = null;
        if (str[2] != null) {
            mime = str[2];
        } else {
            int ch = str[1].lastIndexOf('.');
            String ext = (ch >= 0) ?str[1].substring(ch + 1) : null;
            mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        }
        if ((str[1] != null) && (str[1].matches("^%(w3m|open)%.*"))) {
            String url = str[1].replaceFirst("%(w3m|open)%", "");
            if ((mime == null || mime.equals("")) && !url.matches("^http://.*")) {
                url = "http://" + url;
            } else if (url.matches("^file://.*")) {
                url = url.replaceFirst("file://", "");
            }
            str[1] = url;
        }
        Uri uri = null;

        TermSession session = getCurrentTermSession();
        if (session != null) {
            if (action.equalsIgnoreCase("activity")) {
                try {
                    startActivity(new Intent(this, Class.forName(str[1])));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (action.matches("^.*(VIEW|EDIT).*")) {
                Intent intent = new Intent(action);
                if (str[1].matches("^http://.*")) {
                    uri = Uri.parse(str[1]);
                } else {
                    File file = new File(str[1]);
                    if (file.exists()) {
                        String scheme = "";
                        scheme = "file://";
                        uri = Uri.parse(scheme + file.getAbsolutePath());
                    } else {
                        Toast toast = Toast.makeText(this,R.string.storage_read_error,Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                        return;
                    }
                }
                if (mime != null && !mime.equals("")){
                    intent.setDataAndType(uri, mime);
                    Toast toast = Toast.makeText(this,R.string.storage_read_error,Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                    return;
                } else {
                    intent.setData(uri);
                }
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void start(Intent intent) {
        String action = intent.getAction();
        String data = "";
        String mime = intent.getType();
        Uri uri = intent.getData();
        if (uri != null) {
            data = uri.toString();
            String scheme = "text/html";
            String cmd = "am start intent --user 0 -a "+action+" -t "+scheme+" -d "+data;
            TermVimInstaller.shell(cmd);
            return;
        }
        startActivity(intent);
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
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        if (clip.hasText()) {
            String str = clip.getText().toString();
            writeStringToFile(filename, str);
        }
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
        if (clip.hasText()) {
            return true;
        }
        return false;
    }

    private void doPreferences() {
        mDoResetTerminal = true;
        startActivity(new Intent(this, TermPreferences.class));
    }

    boolean mDoResetTerminal = false;
    private void doResetTerminal() {
        TermSession session = getCurrentTermSession();
        if (session != null) {
            session.reset();
            sendKeyStrings("\u001b\u000c", false);
        }
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

    private void doCopyAll() {
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        clip.setText(getCurrentEmulatorView().getTranscriptScreenText());
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
        doWarningBeforePaste();
    }

    private void doTermPaste() {
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        if (clip == null) return;
        CharSequence paste = clip.getText();
        getCurrentTermSession().write(paste.toString());
    }

    private void doWarningBeforePaste() {
        if (!mVimFlavor) {
            doTermPaste();
            return;
        }
        boolean warning = getDevBoolean(Term.this, "do_warning_before_paste", true);
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
                        setDevBoolean(Term.this, "do_warning_before_paste", false);
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
    }

    private void doToggleWifiLock() {
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
        editor.apply();
        return value;
    }

    public boolean getDevBoolean(Context context, String key, boolean defValue) {
        SharedPreferences pref = context.getSharedPreferences("dev", Context.MODE_PRIVATE);
        boolean res;
        try {
            res = pref.getBoolean(key, defValue);
        } catch (Exception e) {
            res = defValue;
        }
        return res;
    }

    private int mOnelineTextBox = -1;
    private void initOnelineTextBox(int mode) {
        final EditText editText = (EditText) findViewById(R.id.text_input);
        editText.setText("");
        setEditTextView(mode);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
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
            final EditText editText = (EditText) findViewById(R.id.text_input);
            if (mode == 0) editText.setText("");
        }
        if (mAlreadyStarted) updatePrefs();
    }

    private void setEditTextVisibility() {
        final EditText editText = (EditText) findViewById(R.id.text_input);
        final View layout = findViewById(R.id.oneline_text_box);
        int visibility = mEditTextView ? View.VISIBLE : View.GONE;
        if (mHideFunctionBar) visibility = View.GONE;
        EmulatorView view = getCurrentEmulatorView();
        if (view != null) view.restartInputGoogleIme();
        layout.setVisibility(visibility);
        if (visibility == View.VISIBLE) {
            if (!mHaveFullHwKeyboard) doShowSoftKeyboard();
            editText.requestFocus();
            doWarningEditTextView();
        } else {
            if (view != null) view.requestFocus();
        }
        setEditTextViewSize();
        if (mViewFlipper != null) mViewFlipper.setEditTextView(visibility == View.VISIBLE);
    }

    private void doWarningEditTextView() {
        if (!mVimFlavor) return;
        doWarningDialog(this.getString(R.string.edit_text_view_warning_title), this.getString(R.string.edit_text_view_warning), "do_warning_edit_text_view");
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
                    final TextView  textView = (TextView)findViewById((R.id.text_input));
                    float sp = SCALE_VIEW * textView.getTextSize() * getApplicationContext().getResources().getDisplayMetrics().scaledDensity;
                    size = (int) Math.ceil(sp);
                }
            }
        }
       if (mViewFlipper != null) mViewFlipper.setEditTextViewSize(size);
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

    private void setFunctionBarSize() {
        int size;
        if (mFunctionBarId == 0) size = findViewById(R.id.view_function_bar).getHeight();
        else size = findViewById(R.id.view_function_bar2).getHeight();
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
        new FunctionKey("functionbar_esc",          R.id.button_esc,               true),
        new FunctionKey("functionbar_ctrl",         R.id.button_ctrl,              true),
        new FunctionKey("functionbar_alt",          R.id.button_alt,               false),
        new FunctionKey("functionbar_tab",          R.id.button_tab,               true),
        new FunctionKey("functionbar_up",           R.id.button_up,                true),
        new FunctionKey("functionbar_down",         R.id.button_down,              true),
        new FunctionKey("functionbar_left",         R.id.button_left,              false),
        new FunctionKey("functionbar_right",        R.id.button_right,             false),
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
        new FunctionKey("functionbar_menu",         R.id.button_menu,              true),
        new FunctionKey("functionbar_menu_hide",    R.id.button_menu_hide,         true),
        new FunctionKey("functionbar_menu_plus",    R.id.button_menu_plus,         false),
        new FunctionKey("functionbar_menu_minus",   R.id.button_menu_minus,        false),
        new FunctionKey("functionbar_menu_x",       R.id.button_menu_x,            false),
        new FunctionKey("functionbar_menu_user",    R.id.button_menu_user,         true),
        new FunctionKey("functionbar_menu_quit",    R.id.button_menu_quit,         true),
        new FunctionKey("functionbar_next",         R.id.button_next_functionbar,  false),
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
                findViewById(fkey.resid).setOnTouchListener(new RepeatListener(400, 40, new OnClickListener() {
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
        Button button = (Button)findViewById(R.id.button_oneline_text_box_enter);
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

    private boolean onelineTextBoxEsc(){
        if (mEditTextView) {
            EditText editText = (EditText) findViewById(R.id.text_input);
            if (editText != null && editText.isFocused()) {
                String str = editText.getText().toString();
                EmulatorView view = getCurrentEmulatorView();
                if (str.equals("")) {
                    if (view != null) view.requestFocus();
                } else {
                    if (view != null) view.restartInputGoogleIme();
                    editText.setText("");
                }
                return true;
            }
        }
        return false;
    }

    private boolean onelineTextBoxTab(){
        if (mEditTextView) {
            EditText editText = (EditText) findViewById(R.id.text_input);
            if (editText != null && editText.isFocused()) {
                sendKeyStrings("\t", false);
                return true;
            }
        }
        return false;
    }

    private boolean onelineTextBoxEnter(boolean force){
        if (mEditTextView) {
            EditText editText = (EditText) findViewById(R.id.text_input);
            if (editText != null && (force || editText.isFocused())) {
                String str = editText.getText().toString();
                EmulatorView view = getCurrentEmulatorView();
                if (view != null) {
                    view.restartInputGoogleIme();
                    if (str.equals("")) str = "\r";
                    sendKeyStrings(str, false);
                    editText.setText("");
                    return true;
                }
            }
        }
        return false;
    }

    private void setFunctionKeyVisibility(SharedPreferences prefs, String key, int id, boolean defValue) {
        int visibility = prefs.getBoolean(key, defValue) ? View.VISIBLE : View.GONE;
        if (id == R.id.button_menu_plus)  visibility = View.GONE;
        if (id == R.id.button_menu_minus) visibility = View.GONE;
        if (id == R.id.button_menu_x)     visibility = View.GONE;
        setFunctionBarButton(id, visibility);
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
        for (FunctionKey fkey: FunctionKeys) {
            setFunctionKeyVisibility(prefs, fkey.key, fkey.resid, fkey.defValue);
        }

        visibility = (mFunctionBar == 1 && mFunctionBarId == 0) ? View.VISIBLE : View.GONE;
        findViewById(R.id.view_function_bar).setVisibility(visibility);
        visibility = (mFunctionBar == 1 && mFunctionBarId == 1) ? View.VISIBLE : View.GONE;
        findViewById(R.id.view_function_bar2).setVisibility(visibility);
        setFunctionBarSize();
        mViewFlipper.setFunctionBar(mFunctionBar == 1);
    }

    @SuppressLint("NewApi")
    private void setFunctionBarButton(int id, int visibility) {
        Button button = (Button)findViewById(id);
        button.setVisibility(visibility);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int height = (int) Math.ceil(mSettings.getFontSize() * metrics.density * metrics.scaledDensity);
        button.setMinHeight(height);
        if (AndroidCompat.SDK >= 14) {
            button.setAllCaps(false);
        }
    }

    public void onClick(View v) {
        EmulatorView view = getCurrentEmulatorView();
        switch (v.getId()) {
        case R.id.button_esc:
            if (onelineTextBoxEsc()) break;
            doSendActionBarKey(view, KeycodeConstants.KEYCODE_ESCAPE);
            break;
        case R.id.button_ctrl:
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
            if (!mHaveFullHwKeyboard) {
                doShowSoftKeyboard();
            }
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
        case R.id.button_next_functionbar:
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

    public static String handleOpenDocument(Uri uri, Cursor cursor) {
        if (uri == null || cursor == null) return null;

        cursor.moveToFirst();
        int index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
        if (index == -1) return null;
        String displayName = cursor.getString(index);

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
            path = uri.toString().replaceFirst("content://[^/]+/", "/");
            if (isDownloadDocument(uri)) {
                path = uri.toString().replaceFirst("content://[^/]+/", "/Download/");
            } else if (isGoogleDriveDocument(uri)) {
                path = uri.toString().replaceFirst("content://[^/]+/", "/GoogleDrive/");
            } else if (isGoogleDriveLegacyDocument(uri)) {
                path = uri.toString().replaceFirst("content://[^/]+/", "/GoogleDriveLegacy/");
            } else if (isOneDriveDocument(uri)) {
                path = uri.toString().replaceFirst("content://[^/]+/", "/OneDrive/");
            } else if (isMediaDocument(uri)) {
                path = uri.toString().replaceFirst("content://[^/]+/", "/MediaDocument/");
            }
            if (path != null) {
                path = "/"+path+"/"+displayName;
                path = path.replaceAll(":", "/");
                path = path.replaceAll("%2F", "/");
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

    public static boolean isOneDriveDocument(Uri uri) {
        return "com.microsoft.skydrive.content.StorageAccessProvider".equals(uri.getAuthority());
    }

    IabHelper mIabHelper = null;
    boolean mIabHelperDisable = false;
    static final String TAG = "In App Billing";
    private void iabSetup() {
        if (!isConnected(this.getApplicationContext())) return;
        if (mIabHelperDisable) {
            alert(Term.this.getString(R.string.iab_null));
            return;
        }
        String base64EncodedPublicKey = BuildConfig.BASE64_PUBLIC_KEY;

        final ProgressDialog pd = ProgressDialog.show(Term.this, null, Term.this.getString(R.string.iab_wait), true, false);
        if (mIabHelper == null) mIabHelper = new IabHelper(this, base64EncodedPublicKey);
        if (mIabHelper == null) {
            pd.dismiss();
            confirmRetryIabSetup();
        } else {
            mIabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
                public void onIabSetupFinished(IabResult result) {
                    pd.dismiss();
                    Log.d(TAG, "Setup finished.");
                    if (!result.isSuccess()) {
                        // Oh noes, there was a problem.
                        // complain("Problem setting up in-app billing: " + result);
                        showAds(true);
                        return;
                    }

                    // Have we been disposed of in the meantime? If so, quit.
                    if (mIabHelper == null) return;

                    // IAB is fully set up. Now, let's get an inventory of stuff we own.
                    Log.d(TAG, "Setup successful. Querying inventory.");
                    mIabHelper.queryInventoryAsync(mGotInventoryListener);
                }
            });
        }
    }

    private void confirmRetryIabSetup() {
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(this.getString(R.string.iabsetup_try_again));
        b.setPositiveButton(this.getString(R.string.installer_try_again_button), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                iabSetup();
            }
        }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                mOnExtraButtonClicked = false;
            }
        });
        b.create().show();
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

    boolean mIsPremium = false;
    static final String SKU_PREMIUM = "com.droidvim.premium.git";

    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.d(TAG, "Query inventory finished.");
            // Have we been disposed of in the meantime? If so, quit.
            if (mIabHelper == null) {
                mOnExtraButtonClicked = false;
                return;
            }

            // Is it a failure?
            if (result.isFailure()) {
                // complain("Failed to query inventory: " + result);
                mOnExtraButtonClicked = false;
                return;
            }

            Log.d(TAG, "Query inventory was successful.");

            // Do we have the premium upgrade?
            Purchase premiumPurchase = inventory.getPurchase(SKU_PREMIUM);
            mIsPremium = (premiumPurchase != null && verifyDeveloperPayload(premiumPurchase));
            Log.d(TAG, "User is " + (mIsPremium ? "PREMIUM" : "NOT PREMIUM"));
            // if (!mIsPremium) showAds(true);
            setExtraButton();

            updateUi();
            setWaitScreen(false);
            Log.d(TAG, "Initial inventory query finished; enabling main UI.");
            if (mOnExtraButtonClicked) {
                mOnExtraButtonClicked = false;
                onExtraButtonClicked(null);
            }
        }
    };

    IabHelper.OnConsumeFinishedListener mGotConsumeInventoryListener = new IabHelper.OnConsumeFinishedListener() {
        @Override
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            if (result.isSuccess()) {
               // provision the in-app purchase to the user
                Log.d(TAG, "Consume Successl : " + purchase.getSku());
            } else {
                Log.d(TAG, "Consume failed : " + purchase.getSku());
               // handle error
            }
        }
    };

    /** Verifies the developer payload of a purchase. */
    boolean verifyDeveloperPayload(Purchase p) {
        return true;
        // String payload = p.getDeveloperPayload();
        // // FIXME: https://github.com/googlesamples/android-play-billing/issues/7
        // // for promotion code
        // if (payload.equals("")) return true;
        // String source = getPayload();
        // return payload.equals(source);
    }

    private String getPayload() {
        /* TODO: for security, generate your payload here for verification. See the comments on
         *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
         *        an empty string, but on a production app you should carefully generate this. */
        String property = "";
        return property;
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
        if (mIsPremium) {
            installGit();
        } else if (mIabHelper == null) {
            mOnExtraButtonClicked = true;
            iabSetup();
        } else {
            confirmDonation(this);
        }
    }

    private void confirmDonation(final Activity activity) {
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_info);
        String title = this.getString(R.string.donate_confirm_title);
        String summary = "";
        if (TermVimInstaller.getArch() == null) {
            summary = this.getString(R.string.donate_confirm_summary_no_payed_function);
        } else {
            summary = this.getString(R.string.donate_confirm_summary);
        }
        b.setTitle(title);
        b.setMessage(summary);
        b.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                if (mIabHelper != null) {
                    String payload = getPayload();

                    mIabHelper.launchPurchaseFlow(activity, SKU_PREMIUM, REQUEST_BILLING,
                            mPurchaseFinishedListener, payload);
                }
            }
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    // Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);

            // if we were disposed of in the meantime, quit.
            if (mIabHelper == null) return;

            if (result.isFailure()) {
                if (BuildConfig.DEBUG) complain("Error purchasing: " + result);
                setWaitScreen(false);
                return;
            }
            if (!verifyDeveloperPayload(purchase)) {
                complain("Error purchasing. Authenticity verification failed.");
                setWaitScreen(false);
                return;
            }

            Log.d(TAG, "Purchase successful.");

            if (purchase.getSku().equals(SKU_PREMIUM)) {
                // bought the premium upgrade!
                Log.d(TAG, "Purchase is premium upgrade. Congratulating user.");
                alert(Term.this.getString(R.string.donate_premium_purchase));
                mIsPremium = true;
                updateUi();
                setWaitScreen(false);
                installGit();
            }
        }
    };

    public void updateUi() {
        setExtraButton();
    }

    // Enables or disables the "please wait" screen.
    void setWaitScreen(boolean set) {
        // findViewById(R.id.screen_main).setVisibility(set ? View.GONE : View.VISIBLE);
        // findViewById(R.id.screen_wait).setVisibility(set ? View.VISIBLE : View.GONE);
    }

    void complain(String message) {
        Log.e(TAG, "**** Error: " + message);
        alert("Error: " + message);
    }

    void alert(String message) {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setMessage(message);
        bld.setPositiveButton(android.R.string.ok, null);
        Log.d(TAG, "Showing alert dialog: " + message);
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

}

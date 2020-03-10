/*
 * Copyright (C) 2012 Steven Luo
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
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import jackpal.androidterm.compat.AndroidCompat;
import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompat;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompatFactory;
import jackpal.androidterm.util.SessionList;
import jackpal.androidterm.util.TermSettings;

import static jackpal.androidterm.Term.REQUEST_FOREGROUND_SERVICE_PERMISSION;

public class RemoteInterface extends AppCompatActivity {
    protected static final String PRIVACT_OPEN_NEW_WINDOW = "shiftrot.androidterm.private.OPEN_NEW_WINDOW";
    protected static final String PRIVACT_SWITCH_WINDOW = "shiftrot.androidterm.private.SWITCH_WINDOW";

    private static String mHandle = null;
    private static String mFname = null;

    protected static final String PRIVEXTRA_TARGET_WINDOW = "jackpal.androidterm.private.target_window";
    protected static final String EXTRA_WINDOW_HANDLE = "jackpal.androidterm.window_handle";

    protected static final String PRIVACT_ACTIVITY_ALIAS = "jackpal.androidterm.TermInternal";
    private final static boolean FLAVOR_VIM = BuildConfig.FLAVOR.matches(".*vim.*");

    private TermSettings mSettings;

    private TermService mTermService;
    private Intent mTSIntent;
    public static String IntentCommand = null;
    public static CharSequence ShareText = null;
    private boolean mDoInstall = false;
    private final String DO_INSTALL = "echo -n -e \"\\0033[990t\"";
    private final String BASH = "bash\n";

    private ServiceConnection mTSConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            TermService.TSBinder binder = (TermService.TSBinder) service;
            mTermService = binder.getService();
            handleIntent();
        }

        public void onServiceDisconnected(ComponentName className) {
            mTermService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSettings = new TermSettings(getResources(), prefs);

        Intent TSIntent = new Intent(this, TermService.class);
        mTSIntent = TSIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED) {
                this.getApplicationContext().startForegroundService(TSIntent);
            } else {
                requestPermissions(new String[]{Manifest.permission.FOREGROUND_SERVICE}, REQUEST_FOREGROUND_SERVICE_PERMISSION);
            }
        } else {
            startService(TSIntent);
        }
        if (!bindService(TSIntent, mTSConnection, BIND_AUTO_CREATE)) {
            Log.e(TermDebug.LOG_TAG, "bind to service failed!");
            finish();
        }
    }

    @Override
    public void finish() {
        ServiceConnection conn = mTSConnection;
        if (conn != null) {
            unbindService(conn);

            // Stop the service if no terminal sessions are running
            TermService service = mTermService;
            if (service != null) {
                SessionList sessions = service.getSessions();
                if (sessions == null || sessions.size() == 0) {
                    stopService(mTSIntent);
                }
            }

            mTSConnection = null;
            mTermService = null;
        }
        super.finish();
    }

    @Override
    @SuppressLint("NewApi")
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_FOREGROUND_SERVICE_PERMISSION:
                for (int i = 0; i < permissions.length; i++) {
                    if (permissions[i].equals(Manifest.permission.FOREGROUND_SERVICE)) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            this.getApplicationContext().startForegroundService(mTSIntent);
                        } else {
                            startService(mTSIntent);
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

    protected TermService getTermService() {
        return mTermService;
    }

    protected void handleIntent() {
        TermService service = getTermService();
        if (service == null) {
            finish();
            return;
        }
        mDoInstall = StaticConfig.FLAVOR_VIM && !new File(this.getFilesDir() + "/bin").isDirectory();

        Intent myIntent = getIntent();
        String action = myIntent.getAction();
        IntentCommand = null;
        ShareText = null;

        if (action == null) {
            finish();
            return;
        }
        ClipData clipData = myIntent.getClipData();
        if ((AndroidCompat.SDK >= 19 && action.equals(Intent.ACTION_SEND) && myIntent.hasExtra(Intent.EXTRA_STREAM)) ||
                (action.equals(Intent.ACTION_SEND) && clipData != null) ||
                (action.equals("android.intent.action.VIEW")) ||
                (action.equals("android.intent.action.EDIT")) ||
                (action.equals("android.intent.action.PICK")) ||
                (action.equals("com.googlecode.android_scripting.action.EDIT_SCRIPT"))) {
            String url = null;
            Uri uri = null;
            if (clipData != null) {
                uri = clipData.getItemAt(0).getUri();
                if (uri == null) {
                    ShareText = clipData.getItemAt(0).getText();
                    shareText(ShareText);
                    finish();
                    return;
                }
            } else if (AndroidCompat.SDK >= 19 && action.equals(Intent.ACTION_SEND) && myIntent.hasExtra(Intent.EXTRA_STREAM)) {
                Object extraStream = myIntent.getExtras().get(Intent.EXTRA_STREAM);
                if (extraStream instanceof Uri) {
                    uri = (Uri) extraStream;
                }
            } else {
                uri = myIntent.getData();
            }
            String intentCommand = mSettings.getIntentCommand();
            boolean flavorVim = mSettings.getInitialCommand().matches("(.|\n)*(^|\n)-?vim\\.app(.|\n)*");
            if (intentCommand.equals("")) {
                intentCommand = "sh";
                if (flavorVim) intentCommand = ":e";
            }
            if (uri != null && uri.toString().matches("^file:///.*") && flavorVim) {
                String path = uri.getPath();
                if (new File(path).canRead()) {
                    path = path.replaceAll(Term.SHELL_ESCAPE, "\\\\$1");
                    String command = "\u001b" + intentCommand + " " + path;
                    if (mDoInstall) {
                        IntentCommand = command;
                        command = DO_INSTALL;
                    }
                    // Find the target window
                    mReplace = true;
                    mHandle = switchToWindow(mHandle, command);
                    mReplace = false;
                }
                finish();
            } else if (AndroidCompat.SDK >= 19 && uri != null && uri.getScheme() != null && uri.getScheme().equals("content") && flavorVim) {
                Context context = this;
                String command = null;
                String path = UriToPath.getPath(context, uri);
                if (path != null) {
                    path = path.replaceAll(Term.SHELL_ESCAPE, "\\\\$1");
                    command = "\u001b" + intentCommand + " " + path;
                    if (mDoInstall) {
                        IntentCommand = command;
                        command = DO_INSTALL;
                    }
                } else if (getContentResolver() != null) {
                    try {
                        Cursor cursor = getContentResolver().query(uri, null, null, null, null, null);
                        path = Term.handleOpenDocument(uri, cursor);
                    } catch (Exception e) {
                        alert(e.toString() + "\n" + this.getString(R.string.storage_read_error));
                        finish();
                    }
                    if (path == null) {
                        alert(this.getString(R.string.storage_read_error));
                        finish();
                    } else {
                        SyncFileObserver sfo = Term.restoreSyncFileObserver(this);
                        if (sfo != null) {
                            path = sfo.getObserverDir() + path;
                            if (path.equals("") || !sfo.putUriAndLoad(uri, path)) {
                                String fname = new File(path).getName();
                                alert(fname + "\n" + this.getString(R.string.storage_read_error));
                                finish();
                            }
                            path = path.replaceAll(Term.SHELL_ESCAPE, "\\\\$1");
                            command = "\u001b" + intentCommand + " " + path;
                        }
                        if (mDoInstall) {
                            IntentCommand = command;
                            command = DO_INSTALL;
                        }
                    }
                }
                // Find the target window
                mReplace = true;
                mHandle = switchToWindow(mHandle, command);
                mReplace = false;
                finish();
            } else if (action.equals("com.googlecode.android_scripting.action.EDIT_SCRIPT")) {
                url = myIntent.getExtras().getString("com.googlecode.android_scripting.extra.SCRIPT_PATH");
            } else if (myIntent.getScheme() != null && myIntent.getScheme() != null && myIntent.getScheme().equals("file")) {
                if (myIntent.getData() != null) url = myIntent.getData().getPath();
            }
            if (url != null) {
                String command = mSettings.getIntentCommand();
                if (command.equals("")) {
                    command = "sh";
                    if (flavorVim) command = ":e";
                }
                if (command.matches("^:.*")) {
                    url = url.replaceAll(Term.SHELL_ESCAPE, "\\\\$1");
                    command = "\u001b" + command + " " + url;
                    if (mDoInstall) {
                        IntentCommand = command;
                        command = DO_INSTALL;
                    }
                    // Find the target window
                    mReplace = true;
                    mHandle = switchToWindow(mHandle, command);
                    mReplace = false;
                } else if ((mHandle != null) && (url.equals(mFname))) {
                    // Target the request at an existing window if open
                    command = command + " " + url;
                    if (mDoInstall) {
                        IntentCommand = command;
                        command = DO_INSTALL;
                    }
                    mHandle = switchToWindow(mHandle, command);
                } else {
                    // Open a new window
                    command = command + " " + url;
                    if (mDoInstall) {
                        IntentCommand = command;
                        command = DO_INSTALL;
                    }
                    mHandle = openNewWindow(command);
                }
                mFname = url;

                Intent result = new Intent();
                result.putExtra(EXTRA_WINDOW_HANDLE, mHandle);
                setResult(RESULT_OK, result);
            }
        } else if (action.equals(Intent.ACTION_SEND) && myIntent.hasExtra(Intent.EXTRA_TEXT)) {
            ShareText = myIntent.getExtras().getCharSequence(Intent.EXTRA_TEXT);
            shareText(ShareText);
        }

        finish();
    }

    void alert(final String message) {
        final Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        Term.showToast(toast);
    }

    private String FILE_CLIPBOARD = "/data/data/" + BuildConfig.APPLICATION_ID + "/files/.clipboard";
    public void shareText(CharSequence str) {
        if (str == null) {
            alert(this.getString(R.string.toast_clipboard_error));
            return;
        }
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(this.getApplicationContext());
        if (clip != null) {
            if (FLAVOR_VIM) {
                FILE_CLIPBOARD = TermService.getAPPFILES() + "/.clipboard";
                String filename = FILE_CLIPBOARD;
                Term.writeStringToFile(filename, "\n" + str.toString());
                String command = "\u001b" + ":ATEMod _paste";
                if (mDoInstall) command = DO_INSTALL;
                // Find the target window
                mReplace = true;
                mHandle = switchToWindow(mHandle, command);
                mReplace = false;
            } else {
                clip.setText(str);
                alert(this.getString(R.string.toast_clipboard));
            }
        }
    }

    /**
     * Quote a string so it can be used as a parameter in bash and similar shells.
     */
    public static String quoteForBash(String s) {
        StringBuilder builder = new StringBuilder();
        String specialChars = "\"\\$`!";
        builder.append('"');
        int length = s.length();
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            if (specialChars.indexOf(c) >= 0) {
                builder.append('\\');
            }
            builder.append(c);
        }
        builder.append('"');
        return builder.toString();
    }

    protected String openNewWindow(String iInitialCommand) {
        TermService service = getTermService();

        String initialCommand = getInitialCommand();
        if (iInitialCommand != null) {
            if (initialCommand != null) {
                initialCommand += "\r" + iInitialCommand;
            } else {
                initialCommand = iInitialCommand;
            }
        }

        String bash = (AndroidCompat.SDK >= Build.VERSION_CODES.LOLLIPOP &&
                new File(TermService.getAPPFILES() + "/usr/bin/bash").canExecute())
                ? BASH : "";
        initialCommand = initialCommand.replaceAll("\n(-?vim.app)", "\n" + bash + "$1");
        try {
            TermSession session = Term.createTermSession(this, mSettings, initialCommand);

            session.setFinishCallback(service);
            service.getSessions().add(session);

            String handle = UUID.randomUUID().toString();
            ((GenericTermSession) session).setHandle(handle);

            Intent intent = new Intent(PRIVACT_OPEN_NEW_WINDOW);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            return handle;
        } catch (IOException e) {
            return null;
        }
    }

    private boolean mReplace = false;

    private String getInitialCommand() {
        String cmd = mSettings.getInitialCommand();
        cmd = mTermService.getInitialCommand(cmd, (mReplace && mTermService.getSessions().size() == 0));
        return cmd;
    }

    protected String appendToWindow(String handle, String iInitialCommand) {
        TermService service = getTermService();

        // Find the target window
        SessionList sessions = service.getSessions();
        GenericTermSession target = null;
        int index;
        for (index = 0; index < sessions.size(); ++index) {
            GenericTermSession session = (GenericTermSession) sessions.get(index);
            String h = session.getHandle();
            if (h != null && h.equals(handle)) {
                target = session;
                break;
            }
        }

        if (target == null) {
            // Target window not found, open a new one
            return openNewWindow(iInitialCommand);
        }

        if (iInitialCommand != null) {
            target.write(iInitialCommand);
            target.write('\r');
        }

        Intent intent = new Intent(PRIVACT_SWITCH_WINDOW);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(PRIVEXTRA_TARGET_WINDOW, index);
        startActivity(intent);

        return handle;
    }

    private String switchToWindow(String handle, String iInitialCommand) {
        TermService service = mTermService;
        if (service == null) {
            finish();
            return null;
        }

        // Find the target window
        SessionList sessions = service.getSessions();
        ShellTermSession target = null;
        int index;
        for (index = 0; index < sessions.size(); ++index) {
            ShellTermSession session = (ShellTermSession) sessions.get(index);
            String h = session.getHandle();
            if (h != null && h.equals(handle)) {
                target = session;
                break;
            }
        }
        int displayChild = TermViewFlipper.getCurrentDisplayChild();
        if (displayChild > 0 && displayChild < sessions.size()) {
            ShellTermSession session = (ShellTermSession) sessions.get(displayChild);
            if (session != null) {
                index = displayChild;
                target = session;
            }
        }

        if (target == null) {
            if (sessions.isEmpty() || iInitialCommand == null)
                return openNewWindow(iInitialCommand);
            target = (ShellTermSession) sessions.get(0);
        }

        if (iInitialCommand != null) {
            target.write(iInitialCommand);
            target.write('\r');
        }

        handle = target.getHandle();
        Intent intent = new Intent(PRIVACT_SWITCH_WINDOW);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(PRIVEXTRA_TARGET_WINDOW, index);
        startActivity(intent);
        return handle;
    }
}

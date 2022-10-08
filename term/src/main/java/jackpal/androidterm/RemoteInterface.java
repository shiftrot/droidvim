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
import android.content.DialogInterface;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompat;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompatFactory;
import jackpal.androidterm.util.SessionList;
import jackpal.androidterm.util.TermSettings;

import static jackpal.androidterm.StaticConfig.FLAVOR_TERMINAL;
import static jackpal.androidterm.StaticConfig.SCOPED_STORAGE;
import static jackpal.androidterm.Term.REQUEST_FOREGROUND_SERVICE_PERMISSION;
import static jackpal.androidterm.Term.REQUEST_STORAGE;

public class RemoteInterface extends AppCompatActivity {
    protected static final String PRIVACT_OPEN_NEW_WINDOW = BuildConfig.APPLICATION_ID + ".shiftrot.androidterm.private.OPEN_NEW_WINDOW";
    protected static final String PRIVACT_SWITCH_WINDOW = BuildConfig.APPLICATION_ID + ".shiftrot.androidterm.private.SWITCH_WINDOW";

    private static String mHandle = null;
    private static String mFname = null;

    protected static final String PRIVEXTRA_TARGET_WINDOW = "jackpal.androidterm.private.target_window";
    protected static final String EXTRA_WINDOW_HANDLE = "jackpal.androidterm.window_handle";

    protected static final String PRIVACT_ACTIVITY_ALIAS = "jackpal.androidterm.TermInternal";
    private final static boolean FLAVOR_VIM = BuildConfig.FLAVOR.matches(".*vim.*");

    private TermSettings mSettings;

    private TermService mTermService;
    private Intent mTSIntent;
    public static CharSequence ShareText = null;
    public static String FILE_CLIPBOARD = "/data/data/" + BuildConfig.APPLICATION_ID + "/files/.clipboard";

    private ServiceConnection mTSConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            TermService.TSBinder binder = (TermService.TSBinder) service;
            mTermService = binder.getService();
            permissionCheck();
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
            case REQUEST_STORAGE:
                for (int i = 0; i < permissions.length; i++) {
                    if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            handleIntent();
                        } else {
                            String permisson = permissions[i];
                            if (shouldShowRequestPermissionRationale(permisson)) {
                                alert(getString(R.string.storage_permission_error));
                            }
                            handleIntent();
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

    @SuppressLint("NewApi")
    void permissionCheck() {
        if ((SCOPED_STORAGE) || (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)) {
            handleIntent();
            return;
        }
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
            } else {
                handleIntent();
            }
        } catch (Exception e) {
            handleIntent();
        }
    }

    private boolean isInstalled(String nativeLibraryDir) {
        if (FLAVOR_TERMINAL) {
            boolean status = (!new File(this.getFilesDir() + "/bin").isDirectory() || !new File(this.getFilesDir() + "/usr").isDirectory());
            if (status) return false;
        } else if (FLAVOR_VIM) {
            boolean status = (!new File(this.getFilesDir() + "/bin").isDirectory() || !new File(this.getFilesDir() + "/usr").isDirectory());
            if (status) return false;
            String APP_VERSION = TermService.APP_VERSION_KEY + nativeLibraryDir;
            if (!APP_VERSION.equals(new PrefValue(this).getString(TermService.VERSION_NAME_KEY, "")))
                return false;
        }
        return true;
    }

    protected void handleIntent() {
        TermService service = getTermService();
        if (service == null) {
            finish();
            return;
        }
        String nativeLibraryDir = this.getApplicationContext().getApplicationInfo().nativeLibraryDir;
        if (!isInstalled(nativeLibraryDir)) {
            if (FLAVOR_TERMINAL) {
                TermVimInstaller.doInstallTerm = true;
            } else if (FLAVOR_VIM) {
                TermVimInstaller.doInstallVim = true;
            }
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            bld.setIcon(android.R.drawable.ic_dialog_alert);
            bld.setTitle(getString(R.string.setup_error_title));
            bld.setMessage(getString(R.string.setup_error));
            bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    finish();
                }
            });
            bld.setCancelable(false);
            bld.create().show();
            return;
        }

        Intent myIntent = getIntent();
        String action = myIntent.getAction();
        ShareText = null;

        if (action == null) {
            finish();
            return;
        }
        ClipData clipData = myIntent.getClipData();
        if ((action.equals(Intent.ACTION_SEND) && myIntent.hasExtra(Intent.EXTRA_STREAM)) ||
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
            } else if (action.equals(Intent.ACTION_SEND) && myIntent.hasExtra(Intent.EXTRA_STREAM)) {
                Object extraStream = myIntent.getExtras().get(Intent.EXTRA_STREAM);
                if (extraStream instanceof Uri) {
                    uri = (Uri) extraStream;
                }
            } else {
                uri = myIntent.getData();
            }
            String intentCommand = mSettings.getIntentCommand();
            String CMD_SH = new File(TermService.getAPPFILES() + "/usr/bin/bash").canExecute() ? "bash" : "sh";
            boolean flavorVim = mSettings.getInitialCommand().matches("(.|\n)*(^|\n)-?vim\\.app(.|\n)*");
            if (intentCommand.equals("")) {
                intentCommand = CMD_SH;
                if (flavorVim) intentCommand = ":e";
            }
            String CMD_ESC = intentCommand.startsWith(":") ? "\u001b" : "";
            if (uri != null && uri.toString().startsWith("file:///")) {
                String path = uri.getPath();
                if (new File(path).canRead()) {
                    path = quotePath(path, "".equals(CMD_ESC));
                    String command = CMD_ESC + intentCommand + " " + path;
                    // Find the target window
                    mReplace = true;
                    mHandle = switchToWindow(mHandle, command);
                    mReplace = false;
                    finish();
                } else {
                    try {
                        AlertDialog.Builder bld = new AlertDialog.Builder(this);
                        if (bld == null) return;
                        bld.setMessage(getString(R.string.storage_read_error));
                        bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                finish();
                            }
                        });
                        bld.setCancelable(false);
                        bld.create().show();
                    } catch (Exception e) {
                        finish();
                    }
                    return;
                }
            } else if (uri != null && uri.getScheme() != null && uri.getScheme().equals("content")) {
                Context context = this;
                String command = null;
                String path = UriToPath.getPath(context, uri);
                if (path != null) {
                    path = quotePath(path, "".equals(CMD_ESC));
                    command = CMD_ESC + intentCommand + " " + path;
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
                            path = quotePath(path, "".equals(CMD_ESC));
                            command = CMD_ESC + intentCommand + " " + path;
                        }
                    }
                }
                // Find the target window
                mReplace = true;
                mHandle = switchToWindow(mHandle, command);
                mReplace = false;
                finish();
                return;
            } else if (action.equals("com.googlecode.android_scripting.action.EDIT_SCRIPT")) {
                url = myIntent.getExtras().getString("com.googlecode.android_scripting.extra.SCRIPT_PATH");
            } else if (myIntent.getScheme() != null && myIntent.getScheme() != null && myIntent.getScheme().equals("file")) {
                if (myIntent.getData() != null) url = myIntent.getData().getPath();
            }
            if (url != null) {
                String command = mSettings.getIntentCommand();
                if (command.equals("")) {
                    command = CMD_SH;
                    if (flavorVim) command = ":e";
                }
                if (command.matches("^:.*")) {
                    url = quotePath(url, "".equals(CMD_ESC));
                    command = CMD_ESC + command + " " + url;
                    // Find the target window
                    mReplace = true;
                    mHandle = switchToWindow(mHandle, command);
                    mReplace = false;
                } else if ((mHandle != null) && (url.equals(mFname))) {
                    // Target the request at an existing window if open
                    command = command + " " + url;
                    mHandle = switchToWindow(mHandle, command);
                } else {
                    // Open a new window
                    command = command + " " + url;
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
        @SuppressLint("ShowToast") final Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        Term.showToast(toast);
    }

    public void shareText(CharSequence str) {
        if (str == null) {
            alert(this.getString(R.string.toast_clipboard_error));
            return;
        }
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(this.getApplicationContext());
        if (clip != null) {
            String shareText = str.toString().replaceAll("[\\xC2\\xA0]", " ");
            if (FLAVOR_VIM) {
                FILE_CLIPBOARD = TermService.getAPPFILES() + "/.clipboard";
                Term.writeStringToFile(FILE_CLIPBOARD, shareText);
                String command = "\u001b" + ":ATEMod _paste";
                // Find the target window
                mReplace = true;
                mHandle = switchToWindow(mHandle, command);
                mReplace = false;
            } else {
                clip.setText(shareText);
                alert(this.getString(R.string.toast_clipboard));
            }
        }
    }

    private static String quotePath(String path, boolean bash) {
        if (bash) {
            return quoteForBash(path);
        } else {
            return path.replaceAll(Term.SHELL_ESCAPE, "\\\\$1");
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
        Term.restoreSyncFileObserver(this);

        String initialCommand = getInitialCommand();
        if (iInitialCommand != null) {
            if (initialCommand != null) {
                initialCommand += "\r" + iInitialCommand;
            } else {
                initialCommand = iInitialCommand;
            }
        }

        try {
            TermSession session = Term.createTermSession(this, mSettings, initialCommand);

            session.setFinishCallback(service);
            service.getSessions().add(session);

            String handle = UUID.randomUUID().toString();
            ((GenericTermSession) session).setHandle(handle);

            Intent intent = new Intent(PRIVACT_OPEN_NEW_WINDOW);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClassName(BuildConfig.APPLICATION_ID, PRIVACT_ACTIVITY_ALIAS);
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
        intent.setClassName(BuildConfig.APPLICATION_ID, PRIVACT_ACTIVITY_ALIAS);
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
        intent.setClassName(BuildConfig.APPLICATION_ID, PRIVACT_ACTIVITY_ALIAS);
        startActivity(intent);
        return handle;
    }
}

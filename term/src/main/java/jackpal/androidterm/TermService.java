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
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.Locale;
import java.util.UUID;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import jackpal.androidterm.compat.ServiceForegroundCompat;
import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.libtermexec.v1.ITerminal;
import jackpal.androidterm.util.SessionList;
import jackpal.androidterm.util.TermSettings;

import static jackpal.androidterm.StaticConfig.SCOPED_STORAGE;

public class TermService extends Service implements TermSession.FinishCallback {
    public static int TermServiceState = -1;
    private static final int RUNNING_NOTIFICATION = 1;
    private ServiceForegroundCompat compat;

    private SessionList mTermSessions;

    public class TSBinder extends Binder {
        TermService getService() {
            Log.i("TermService", "Activity binding to service");
            return TermService.this;
        }
    }

    private final IBinder mTSBinder = new TSBinder();

    @Override
    public IBinder onBind(Intent intent) {
        if (TermExec.SERVICE_ACTION_V1.equals(intent.getAction())) {
            Log.i("TermService", "Outside process called onBind()");

            return new RBinder();
        } else {
            Log.i("TermService", "Activity called onBind()");

            return mTSBinder;
        }
    }

    @Override
    @SuppressLint("NewApi")
    public void onCreate() {
        if (TermServiceState == -1) TermServiceState = 0;
        // should really belong to the Application class, but we don't use one...
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean showStatusIcon = prefs.getBoolean(TermSettings.STATUSBAR_ICON_KEY, true);
        startForegroundServiceNotification(showStatusIcon);
        SharedPreferences.Editor editor = prefs.edit();
        Context context = this.getApplicationContext();
        mAPPLIB = context.getApplicationInfo().nativeLibraryDir;
        mARCH = getArch();
        mAPPBASE = context.getApplicationInfo().dataDir;
        mAPPFILES = context.getFilesDir().toString();
        File externalFiles = context.getExternalFilesDir(null);
        mAPPEXTFILES = externalFiles != null ? externalFiles.toString() : mAPPFILES;
        int sdcard = getSDCard(this);
        if (sdcard > 0) {
            File[] dirs = context.getExternalFilesDirs(null);
            mAPPEXTFILES = dirs[sdcard].toString();
        }
        try {
            mAPPEXTHOME = context.getExternalFilesDir(null).getAbsolutePath() + "/home";
        } catch (Exception e) {
            mAPPEXTHOME = mAPPEXTFILES + "/home";
        }

        String defHomeValue;
        if (BuildConfig.APPLICATION_ID.equals("jackpal.androidterm")) {
            defHomeValue = getDir("HOME", MODE_PRIVATE).getAbsolutePath();
        } else {
            defHomeValue = getFilesDir().getAbsolutePath() + "/home";
            File home = new File(defHomeValue);
            if (!home.exists()) home.mkdir();
        }
        String homePath = prefs.getString("home_path", defHomeValue);
        if ("$APPEXTHOME".equals(homePath)) homePath = mAPPEXTHOME;
        if (!new File(homePath).canWrite()) homePath = defHomeValue;
        mHOME = homePath;
        if (SCOPED_STORAGE) {
            int modeHome = Integer.parseInt( prefs.getString("scoped_storage_home_path_mode", "0"));
            if (modeHome != 0) mHOME = mAPPEXTHOME;
            boolean appExtHome = prefs.getBoolean("scoped_storage_startup_path_is_APPEXTHOME", false);
            if (appExtHome) mSTARTUP_DIR = mAPPEXTHOME;
        }
        File sharedDir = Environment.getExternalStorageDirectory();
        if (sharedDir.canWrite()) {
            mEXTSTORAGE = sharedDir.toString();
        } else {
            mEXTSTORAGE = mAPPEXTFILES;
        }
        mSTARTUP_DIR = prefs.getString("startup_path", mHOME);
        if (!new File(mSTARTUP_DIR).canWrite()) mSTARTUP_DIR = mHOME;

        editor.putString("home_path", mHOME);
        editor.apply();

        mCACHE_DIR = getCacheDir().getAbsolutePath();
        mTMPDIR = mCACHE_DIR + "/tmp";
        mLD_LIBRARY_PATH = mAPPFILES + "/usr/lib";
        File tmpdir = new File(mTMPDIR);
        if (!tmpdir.exists()) tmpdir.mkdir();

        mVERSION_FILES_DIR = mAPPFILES;

        mTermSessions = new SessionList();
        install();

        Log.d(TermDebug.LOG_TAG, "TermService started");
    }

    static public String getArch() {
        return getArch(false);
    }

    static public String getArch(boolean raw) {
        String libPath = getAPPLIB();
        String cpu = null;

        for (String androidArch : Build.SUPPORTED_ABIS) {
            if (androidArch.contains("arm64")) {
                cpu = "arm64";
                break;
            } else if (androidArch.contains("armeabi")) {
                cpu = "arm";
                break;
            } else if (androidArch.contains("x86_64")) {
                cpu = "x86_64";
                break;
            } else if (androidArch.contains("x86")) {
                cpu = "x86";
                break;
            }
        }

        if (cpu == null) {
            if (new File(libPath + "/libx86_64.so").exists()) cpu = "x86_64";
            else if (new File(libPath + "/libx86.so").exists()) cpu = "x86";
            else if (new File(libPath + "/libarm64.so").exists()) cpu = "arm64";
            else if (new File(libPath + "/libarm.so").exists()) cpu = "arm";
        }

        if (cpu != null) return cpu;

        // Unreachable
        return "arm";
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean showStatusIcon = prefs.getBoolean(TermSettings.STATUSBAR_ICON_KEY, true);
        startForegroundServiceNotification(showStatusIcon);
        return START_NOT_STICKY;
    }

    private boolean startForegroundServiceNotification(boolean showStatusIcon) {
        try {
            String channelId = getText(R.string.application_term_app) + "_channel";
            setNotificationChannel(channelId, showStatusIcon);
            Notification notification = buildNotification(channelId, showStatusIcon);
            if (notification != null) {
                if (useNotificationForgroundService()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(RUNNING_NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                    } else {
                        startForeground(RUNNING_NOTIFICATION, notification);
                    }
                } else {
                    compat = new ServiceForegroundCompat(this);
                    compat.startForeground(RUNNING_NOTIFICATION, notification);
                }
            }
        } catch (Exception e) {
            Log.e("TermService", e.toString());
            return false;
        }
        return true;
    }

    void setNotificationChannel(String channelId, boolean showStatusIcon) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Terminal Session";
            int importance = NotificationManager.IMPORTANCE_LOW;
            if (!showStatusIcon) importance = NotificationManager.IMPORTANCE_NONE;
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            String description = getText(R.string.service_notify_text).toString();
            channel.setDescription(description);
            channel.setLightColor(Color.GREEN);
            channel.enableLights(false);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            channel.enableVibration(false);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String channelId, boolean showStatusIcon) {
        Intent notifyIntent = new Intent(this, Term.class);
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, PendingIntent.FLAG_IMMUTABLE);

        CharSequence contentText = getText(R.string.application_term_app);
        Bitmap largeIconBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        int priority = Notification.PRIORITY_LOW;
        int statusIcon = R.mipmap.ic_stat_service_notification_icon;
        if (!showStatusIcon) {
            priority = Notification.PRIORITY_MIN;
            statusIcon = R.drawable.ic_stat_transparent_icon;
        }

        Notification.Builder builder = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, BuildConfig.APPLICATION_ID + ".running");
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setContentText(getText(R.string.service_notify_text));
        builder.setContentTitle(contentText);
        builder.setTicker(contentText);
        builder.setContentIntent(pendingIntent);
        builder.setLargeIcon(largeIconBitmap);
        builder.setSmallIcon(statusIcon);
        builder.setAutoCancel(false);
        builder.setOngoing(true);
        builder.setShowWhen(true);
        builder.setWhen(System.currentTimeMillis());
        builder.setPriority(priority);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(channelId);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    private boolean useNotificationForgroundService() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED));
    }

    @SuppressLint("NewApi")
    private boolean install() {
        boolean status = getInstallStatus(TermVimInstaller.getInstallVersionFile(this), mAPPLIB);
        TermVimInstaller.doInstallVim = !status;
        return status;
    }

    @SuppressLint("NewApi")
    private static String mAPPBASE;
    private static String mAPPEXTFILES;
    private static String mAPPEXTHOME;
    private static String mAPPFILES;
    private static String mAPPLIB;
    private static String mARCH;
    private static String mCACHE_DIR;
    private static String mEXTSTORAGE;
    private static String mHOME;
    private static String mLD_LIBRARY_PATH;
    private static String mPATH;
    private static String mSTARTUP_DIR;
    private static String mTERMINFO;
    private static String mTMPDIR;
    private static String mVERSION_FILES_DIR;
    private static String mVIM;
    private static String mVIMRUNTIME;
    private static String mVIMRUNTIME_INSTALL_DIR;

    public String getInitialCommand(String cmd, boolean bFirst) {
        if (cmd == null || cmd.equals("")) return cmd;

        mPATH = mAPPFILES + "/bin:" + mAPPFILES + "/usr/bin" + ":" + System.getenv("PATH");
        mTERMINFO = mAPPFILES + "/usr/share/terminfo";
        mVIM = mAPPFILES;
        mVIMRUNTIME_INSTALL_DIR = mAPPFILES;
        mVIMRUNTIME = mVIMRUNTIME_INSTALL_DIR + "/runtime";

        String replace = bFirst ? "" : "#";
        cmd = cmd.replaceAll("(^|\n)-+", "$1" + replace);
        cmd = cmd.replaceAll("%APPBASE%", mAPPBASE);
        cmd = cmd.replaceAll("%APPFILES%", mAPPFILES);
        cmd = cmd.replaceAll("%APPEXTFILES%", mAPPEXTFILES);
        cmd = cmd.replaceAll("%APPEXTHOME%", mAPPEXTHOME);
        cmd = cmd.replaceAll("%APPLIB%", mAPPLIB);
        cmd = cmd.replaceAll("%INTERNAL_STORAGE%", mEXTSTORAGE);
        cmd = cmd.replaceAll("%TMPDIR%", mTMPDIR);
        cmd = cmd.replaceAll("%LD_LIBRARY_PATH%", mLD_LIBRARY_PATH);
        cmd = cmd.replaceAll("%PATH%", mPATH);
        cmd = cmd.replaceAll("%STARTUP_DIR%", mSTARTUP_DIR);
        cmd = cmd.replaceAll("%TERMINFO%", mTERMINFO);
        cmd = cmd.replaceAll("%VIMRUNTIME%", mVIMRUNTIME);
        cmd = cmd.replaceAll("%VIM%", mVIM);
        cmd = cmd.replaceAll("\n#.*\n|\n\n", "\n");
        cmd = cmd.replaceAll("^#.*\n|\n#.*$|\n$", "");
        cmd = cmd.replaceAll("(^|\n)bash([ \t]*|[ \t][^\n]+)?$", "$1bash.app$2");
        return cmd;
    }

    static int getSDCard(Context context) {
        int sdcard = 0;
        File[] dirs = context.getApplicationContext().getExternalFilesDirs(null);
        if (dirs.length > 1) {
            for (int i = 1; i < dirs.length; i++) {
                File dir = dirs[i];
                if (dir != null && dir.canWrite() && new File(dir.toString() + "/terminfo").isDirectory()) {
                    sdcard = i;
                    break;
                }
            }
        }
        return sdcard;
    }

    static String getCacheDir(Context context, int sdcard) {
        File cache = context.getExternalCacheDir();
        if (sdcard > 0) {
            File[] dirs = context.getApplicationContext().getExternalCacheDirs();
            if (sdcard < dirs.length) cache = dirs[sdcard];
        }
        if (cache == null || !cache.canWrite()) cache = context.getCacheDir();
        return cache.getAbsolutePath();
    }

    static public String getCACHE_DIR() {
        return mCACHE_DIR;
    }

    public void clearTMPDIR() {
        File tmpdir = new File(mTMPDIR);
        if (tmpdir.exists()) TermVimInstaller.deleteFileOrFolder(tmpdir);
        if (!tmpdir.exists()) tmpdir.mkdir();
    }

    static public String getTMPDIR() {
        return mTMPDIR;
    }

    static public String getVersionFilesDir() {
        return mVERSION_FILES_DIR;
    }

    static public String getTERMINFO() {
        if (mTERMINFO == null) return "/data/data/" + BuildConfig.APPLICATION_ID + "/files/usr/share/terminfo";
        return mTERMINFO;
    }

    static public String getVIM() {
        return mVIM;
    }

    static public String getVimRuntimeInstallDir() {
        return mVIMRUNTIME_INSTALL_DIR;
    }

    static public String getVIMRUNTIME() {
        return mVIMRUNTIME;
    }

    static public String getHOME() {
        return mHOME;
    }

    static public String getARCH() {
        return mARCH;
    }

    static public String getAPPLIB(Context context) {
        mAPPLIB = context.getApplicationInfo().nativeLibraryDir;
        return getAPPLIB();
    }

    static public String getAPPLIB() {
        if (mAPPLIB == null) return "/data/data/" + BuildConfig.APPLICATION_ID + "/lib";
        return mAPPLIB;
    }

    static public String getAPPBASE() {
        return mAPPBASE;
    }

    static public String getLANG() {
        Locale locale = Locale.getDefault();
        String language = locale.getLanguage();
        String country = locale.getCountry();
        return language + "_" + country +".UTF-8";
    }

    static public String getAPPFILES() {
        if (mAPPFILES == null) return "/data/data/" + BuildConfig.APPLICATION_ID + "/files";
        return mAPPFILES;
    }

    static public String getAPPEXTFILES() {
        return mAPPEXTFILES;
    }

    static public String getAPPEXTHOME() {
        return mAPPEXTHOME;
    }

    static public String getEXTSTORAGE() {
        return mEXTSTORAGE;
    }

    static public String getLD_LIBRARY_PATH() {
        return mLD_LIBRARY_PATH;
    }

    static public String getPATH() {
        return mPATH;
    }

    static public final String VERSION_NAME_KEY = "AppVersionName";
    static public String APP_VERSION_KEY = BuildConfig.VERSION_CODE + " + " + BuildConfig.VERSION_NAME + " + ";
    static public String APP_VERSION;
    private boolean getInstallStatus(String scriptFile, String desc) {
        APP_VERSION = APP_VERSION_KEY + desc;
        if (!APP_VERSION.equals(new PrefValue(this).getString(VERSION_NAME_KEY, "")))
            return false;
        return new File(scriptFile).exists();
    }

    @Override
    public void onDestroy() {
        TermServiceState = -1;
        stopNotificationService();
        destroySessions();
        clearTerminalMode();
    }

    private void clearTerminalMode() {
        File local = new File(mVERSION_FILES_DIR + Term.TERMINAL_MODE_FILE);
        if (Term.mTerminalMode != 0 && local.exists()) {
            local.delete();
        }
    }

    private void stopNotificationService() {
        try {
            if (useNotificationForgroundService()) {
                stopSelf();
            } else {
                compat.stopForeground(true);
            }
        } catch (Exception e) {
            Log.e("TermService", "Failed to destory: " + e.toString());
        }
    }

    private void destroySessions() {
        try {
            for (TermSession session : mTermSessions) {
                /* Don't automatically remove from list of sessions -- we clear the
                 * list below anyway and we could trigger
                 * ConcurrentModificationException if we do */
                session.setFinishCallback(null);
                session.finish();
            }
            mTermSessions.clear();
        } catch (Exception e) {
            Log.e("TermService", "Failed to close sessions: " + e.toString());
        }
    }

    public SessionList getSessions() {
        return mTermSessions;
    }

    public void onSessionFinish(TermSession session) {
        mTermSessions.remove(session);
    }

    private final class RBinder extends ITerminal.Stub {
        @Override
        public IntentSender startSession(final ParcelFileDescriptor pseudoTerminalMultiplexerFd,
                                         final ResultReceiver callback) {
            final String sessionHandle = UUID.randomUUID().toString();

            // distinct Intent Uri and PendingIntent requestCode must be sufficient to avoid collisions
            final Intent switchIntent = new Intent(RemoteInterface.PRIVACT_OPEN_NEW_WINDOW)
                    .setData(Uri.parse(sessionHandle))
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(RemoteInterface.PRIVEXTRA_TARGET_WINDOW, sessionHandle);

            final PendingIntent result = PendingIntent.getActivity(getApplicationContext(), sessionHandle.hashCode(),
                    switchIntent, PendingIntent.FLAG_IMMUTABLE);

            final PackageManager pm = getPackageManager();
            final String[] pkgs = pm.getPackagesForUid(getCallingUid());
            if (pkgs == null || pkgs.length == 0)
                return null;

            for (String packageName : pkgs) {
                try {
                    final PackageInfo pkgInfo = pm.getPackageInfo(packageName, 0);

                    final ApplicationInfo appInfo = pkgInfo.applicationInfo;
                    if (appInfo == null)
                        continue;

                    final CharSequence label = pm.getApplicationLabel(appInfo);

                    if (!TextUtils.isEmpty(label)) {
                        final String niceName = label.toString();

                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                GenericTermSession session = null;
                                try {
                                    final TermSettings settings = new TermSettings(getResources(),
                                            PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));

                                    session = new BoundSession(pseudoTerminalMultiplexerFd, settings, niceName);

                                    mTermSessions.add(session);

                                    session.setHandle(sessionHandle);
                                    session.setFinishCallback(new RBinderCleanupCallback(result, callback));
                                    session.setTitle("");

                                    session.initializeEmulator(80, 24);
                                } catch (Exception whatWentWrong) {
                                    Log.e("TermService", "Failed to bootstrap AIDL session: "
                                            + whatWentWrong.getMessage());

                                    if (session != null)
                                        session.finish();
                                }
                            }
                        });

                        return result.getIntentSender();
                    }
                } catch (PackageManager.NameNotFoundException ignore) {
                }
            }

            return null;
        }
    }

    private final class RBinderCleanupCallback implements TermSession.FinishCallback {
        private final PendingIntent result;
        private final ResultReceiver callback;

        public RBinderCleanupCallback(PendingIntent result, ResultReceiver callback) {
            this.result = result;
            this.callback = callback;
        }

        @Override
        public void onSessionFinish(TermSession session) {
            result.cancel();

            callback.send(0, new Bundle());

            mTermSessions.remove(session);
        }
    }
}

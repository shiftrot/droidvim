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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.app.Notification;
import android.app.PendingIntent;

import java.io.File;

import jackpal.androidterm.emulatorview.TermSession;

import jackpal.androidterm.compat.AndroidCompat;
import jackpal.androidterm.compat.ServiceForegroundCompat;
import jackpal.androidterm.libtermexec.v1.*;
import jackpal.androidterm.util.SessionList;
import jackpal.androidterm.util.TermSettings;

import java.util.UUID;

public class TermService extends Service implements TermSession.FinishCallback
{
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
        // should really belong to the Application class, but we don't use one...
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        String defValue;
        if (!BuildConfig.FLAVOR.equals("master")) {
            defValue = getFilesDir().getAbsolutePath() + "/home";
            File home = new File(defValue);
            if (!home.exists()) home.mkdir();
        } else {
            defValue = getDir("HOME", MODE_PRIVATE).getAbsolutePath();
        }
        String homePath = prefs.getString("home_path", defValue);
        if (!new File(homePath).canWrite()) homePath = defValue;
        editor.putString("home_path", homePath);
        mHOME = homePath;

        mAPPBASE = this.getApplicationInfo().dataDir;
        mAPPFILES = this.getFilesDir().toString();
        File externalFiles = this.getExternalFilesDir(null);
        mAPPEXTFILES = externalFiles != null ? externalFiles.toString() : mAPPFILES;
        int sdcard = getSDCard(this);
        if (sdcard > 0) {
            File[] dirs = this.getApplicationContext().getExternalFilesDirs(null);
            mAPPEXTFILES = dirs[sdcard].toString();
        }

        mEXTSTORAGE = Environment.getExternalStorageDirectory().toString();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            mEXTSTORAGE = mHOME;
        }
        mTMPDIR = getCacheDir(this, sdcard) + "/tmp";
        mLD_LIBRARY_PATH = mAPPFILES+"/usr/lib";
        String model = TermVimInstaller.getProp("ro.product.model");
        if ((AndroidCompat.SDK == Build.VERSION_CODES.N) && model != null && model.equals("SM-T585")) {
            mLD_LIBRARY_PATH = "/system/lib:/vendor/lib:"+mLD_LIBRARY_PATH;
        }
        File tmpdir = new File(mTMPDIR);
        if (!tmpdir.exists()) tmpdir.mkdir();

        String libShPath = new File(getApplicationContext().getApplicationInfo().nativeLibraryDir) + "/libsh.so -";
        editor.putString("lib_sh_path", libShPath);
        defValue = AndroidCompat.SDK >= 24 ? "" : "/system/bin/sh -";
        String defshell = prefs.getString("shell_path", defValue);
        editor.putString("shell_path", defshell);
        editor.apply();

        mTermSessions = new SessionList();
        install();

        Log.d(TermDebug.LOG_TAG, "TermService started");
    }

    private boolean useNotificationForgroundService() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (useNotificationForgroundService()) {
                Notification notification = showNotification();
                startForeground(RUNNING_NOTIFICATION, notification);
            } else {
                compat = new ServiceForegroundCompat(this);
                Notification notification = showNotification();
                if (notification != null) compat.startForeground(RUNNING_NOTIFICATION, notification);
            }
        } catch (Exception e) {
            Log.e("TermService", e.toString());
        }
        return START_STICKY;
    }

    Notification showNotification() {
        CharSequence contentText = getText(R.string.application_terminal);
        if (!BuildConfig.FLAVOR.equals("main")) {
            contentText = getText(R.string.application_term_app);
        }
        int priority = Notification.PRIORITY_DEFAULT;
        int statusIcon = R.mipmap.ic_stat_service_notification_icon;
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        if (!pref.getBoolean("statusbar_icon", true)) {
            priority = Notification.PRIORITY_MIN;
            if (AndroidCompat.SDK >= 24 || TermVimInstaller.OS_AMAZON) statusIcon = R.drawable.ic_stat_transparent_icon;
        }
        Intent notifyIntent = new Intent(this, Term.class);
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, 0);
        Bitmap largeIconBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        Notification notification;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            String NOTIFICATION_CHANNEL_ID = contentText.toString() + "_channel";
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "Terminal Session", NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setDescription(getText(R.string.service_notify_text).toString());
            notificationChannel.setLightColor(Color.GREEN);
            notificationChannel.enableLights(false);
            notificationChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            notificationChannel.setImportance(NotificationManager.IMPORTANCE_MIN);
            notificationChannel.enableVibration(false);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(notificationChannel);
            }
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
            notificationBuilder
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setTicker(contentText)
                .setContentTitle(contentText)
                .setContentText(getText(R.string.service_notify_text))
                .setContentIntent(pendingIntent)
                .setSmallIcon(statusIcon)
                .setLargeIcon(largeIconBitmap)
                .setPriority(priority)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setContentInfo("");
            notification = notificationBuilder.build();
        } else {
            notification = new NotificationCompat.Builder(getApplicationContext())
                .setContentTitle(contentText)
                .setContentText(getText(R.string.service_notify_text))
                .setContentIntent(pendingIntent)
                .setSmallIcon(statusIcon)
                .setLargeIcon(largeIconBitmap)
                .setPriority(priority)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .build();
        }
        return notification;
    }

    @SuppressLint("NewApi")
    private boolean install() {
        boolean status = getInstallStatus(TermVimInstaller.getInstallVersionFile(this), "res/raw/version");
        TermVimInstaller.doInstallVim = !status;
        return status;
    }

    @SuppressLint("NewApi")
    private static String mAPPBASE;
    private static String mAPPFILES;
    private static String mAPPEXTFILES;
    private static String mEXTSTORAGE;
    private static String mLD_LIBRARY_PATH;
    private static String mTMPDIR;
    private static String mHOME;
    public String getInitialCommand(String cmd, boolean bFirst) {
        String replace = bFirst ? "" : "#";
        cmd = cmd.replaceAll("(^|\n)-+", "$1"+ replace);
        cmd = cmd.replaceAll("%APPBASE%", mAPPBASE);
        cmd = cmd.replaceAll("%APPFILES%", mAPPFILES);
        cmd = cmd.replaceAll("%APPEXTFILES%", mAPPEXTFILES);
        cmd = cmd.replaceAll("%INTERNAL_STORAGE%", mEXTSTORAGE);
        cmd = cmd.replaceAll("%TMPDIR%", mTMPDIR);
        cmd = cmd.replaceAll("%LD_LIBRARY_PATH%", mLD_LIBRARY_PATH);
        cmd = cmd.replaceAll("\n#.*\n|\n\n", "\n");
        cmd = cmd.replaceAll("^#.*\n|\n#.*$|\n$", "");
        return cmd;
    }

    static int getSDCard(Context context) {
        int sdcard = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            File[] dirs = context.getApplicationContext().getExternalFilesDirs(null);
            if (dirs.length > 1) {
                for (int i = 1; i < dirs.length; i++) {
                    File dir = dirs[i];
                    if (dir != null && dir.canWrite() && new File(dir.toString()+"/terminfo").isDirectory()) {
                        sdcard = i;
                        break;
                    }
                }
            }
        }
        return sdcard;
    }

    static String getCacheDir(Context context, int sdcard) {
        File cache = context.getExternalCacheDir();
        if (sdcard > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            File[] dirs = context.getApplicationContext().getExternalCacheDirs();
            if (sdcard < dirs.length) cache = dirs[sdcard];
        }
        if (cache == null || !cache.canWrite()) cache = context.getCacheDir();
        return cache.getAbsolutePath();
    }

    public void clearTMPDIR() {
        File tmpdir = new File(mTMPDIR);
        if (tmpdir.exists()) TermVimInstaller.deleteFileOrFolder(tmpdir);
    }

    static public String getTMPDIR() {
        return mTMPDIR;
    }

    static public String getHOME() {
        return mHOME;
    }

    static public String getAPPBASE() {
        return mAPPBASE;
    }

    static public String getAPPFILES() {
        return mAPPFILES;
    }

    static public String getAPPEXTFILES() {
        return mAPPEXTFILES;
    }

    static public String getEXTSTORAGE() {
        return mEXTSTORAGE;
    }

    static public String getLD_LIBRARY_PATH() {
        return mLD_LIBRARY_PATH;
    }

    private boolean getInstallStatus(String scriptFile, String zipFile) {
        if (!TermVimInstaller.TERMVIM_VERSION.equals(new PrefValue(this).getString("versionName", ""))) return false;
        if (!(new File(scriptFile).exists())) return false;
        return true;
    }

    @Override
    public void onDestroy() {
        stopNotificationService();
        destroySessions();
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
                    switchIntent, 0);

            final PackageManager pm = getPackageManager();
            final String[] pkgs = pm.getPackagesForUid(getCallingUid());
            if (pkgs == null || pkgs.length == 0)
                return null;

            for (String packageName:pkgs) {
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
                } catch (PackageManager.NameNotFoundException ignore) {}
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

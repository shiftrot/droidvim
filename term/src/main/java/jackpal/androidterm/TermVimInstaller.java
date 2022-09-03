package jackpal.androidterm;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.system.Os;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jackpal.androidterm.compat.AndroidCompat;
import jackpal.androidterm.util.TermSettings;

import static jackpal.androidterm.ShellTermSession.getProotCommand;
import static jackpal.androidterm.StaticConfig.FLAVOR_TERMINAL;
import static jackpal.androidterm.StaticConfig.FLAVOR_VIM;
import static jackpal.androidterm.StaticConfig.SCOPED_STORAGE;

final class TermVimInstaller {
    static public boolean doInstallVim = false;
    static private String SOLIB_PATH;
    static private TermSettings mSettings;

    static void installVim(final AppCompatActivity activity, final Runnable whenDone) {
        if (!doInstallVim) return;

        SOLIB_PATH = getSolibPath(activity);
        String arch = getArch();
        if ((AndroidCompat.SDK < 16) || ((AndroidCompat.SDK < 18) && (arch.contains("x86") || arch.contains("i686")))) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder bld = new AlertDialog.Builder(activity);
                    bld.setMessage(R.string.error_not_supported_device);
                    bld.setPositiveButton("OK", null);
                    bld.create().show();
                    doInstallVim = false;
                }
            });
            return;
        }
        if (true) {
            doInstallVim(activity, whenDone, true);
        } else {
            final AlertDialog.Builder b = new AlertDialog.Builder(activity);
            b.setIcon(android.R.drawable.ic_dialog_info);
            //        b.setTitle(activity.getString(R.string.install_runtime_doc_dialog_title));
            b.setMessage(activity.getString(R.string.install_runtime_doc_message));
            b.setPositiveButton(activity.getString(R.string.button_yes), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    doInstallVim(activity, whenDone, true);
                }
            });
            b.setNegativeButton(activity.getString(R.string.button_no), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    doInstallVim(activity, whenDone, false);
                }
            });
            b.setCancelable(false);
            b.show();
        }
    }

    static private int orientationLock(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return ActivityInfo.SCREEN_ORIENTATION_LOCKED;
        }
        Configuration config = activity.getResources().getConfiguration();
        switch (config.orientation) {
            case Configuration.ORIENTATION_PORTRAIT:
            case Configuration.ORIENTATION_SQUARE:
            case Configuration.ORIENTATION_UNDEFINED:
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case Configuration.ORIENTATION_LANDSCAPE:
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
        return ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
    }

    static private void fixOrientation(final AppCompatActivity activity, final int orientation) {
        if (activity != null && !activity.isFinishing()) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.setRequestedOrientation(orientation);
                }
            });
        }
    }

    static String getInstallVersionFile(final Service service) {
        return TermService.getVersionFilesDir() + "/version";
    }

    static void installTerm(final AppCompatActivity activity) {
        installTerm(activity, null);
    }

    private static final String TERM_VERSION_KEY = "versionNameTerm";
    static boolean getTermInstallStatus(final AppCompatActivity activity) {
        SharedPreferences pref = activity.getApplicationContext().getSharedPreferences("dev", Context.MODE_PRIVATE);
        mSettings = new TermSettings(activity.getResources(), pref);
        String terminfoDir = TermService.getTERMINFO();
        File dir = new File(terminfoDir);
        boolean doInstall = doInstallTerm || !dir.isDirectory() || !pref.getString(TERM_VERSION_KEY, "").equals(TermService.APP_VERSION);
        return !doInstall;
    }

    static void installTerm(final AppCompatActivity activity, Runnable whenDone) {
        if (getTermInstallStatus(activity)) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    toast(activity, activity.getString(R.string.message_please_wait));
                    doInstallTerm(activity, whenDone);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    static public boolean doInstallTerm = false;
    static boolean doInstallTerm(final AppCompatActivity activity) {
        return doInstallTerm(activity, null);
    }

    static boolean doInstallTerm(final AppCompatActivity activity, Runnable whenDone) {
        boolean doInstall = !getTermInstallStatus(activity);

        if (doInstall) {
            int id = activity.getResources().getIdentifier("terminfo_min", "raw", activity.getPackageName());
            String terminfoDir = TermService.getAPPFILES() + "/usr/share";
            installZip(terminfoDir, getInputStream(activity, id));
            final String path = TermService.getAPPFILES();
            id = activity.getResources().getIdentifier("shell", "raw", activity.getPackageName());
            installZip(path, getInputStream(activity, id));
            if (AndroidCompat.SDK >= Build.VERSION_CODES.LOLLIPOP) {
                String bin_am = "bin_am";
                id = activity.getResources().getIdentifier(bin_am, "raw", activity.getPackageName());
                installZip(path, getInputStream(activity, id));
                id = activity.getResources().getIdentifier("am", "raw", activity.getPackageName());
                copyScript(activity.getResources().openRawResource(id), TermService.getAPPFILES() + "/bin/am");
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                File fontPath = new File(TermPreferences.FONT_PATH);
                if (!fontPath.exists()) fontPath.mkdirs();
            }
            if (!FLAVOR_VIM) {
                if (AndroidCompat.SDK >= Build.VERSION_CODES.LOLLIPOP) {
                    installInternalBusybox(path + "/usr/bin");
                    installSoTar(path, "unzip");
                    installTar(path, getInputStream(activity, getLocalLibId(activity, "unzip_")));
                    JniLibsToBin.jniLibsToBin(path, getInputStream(activity, getLocalLibId(activity, "unzip_symlinks_")));
                    installSoTar(path, "zip");
                    installTar(path, getInputStream(activity, getLocalLibId(activity, "zip_")));
                    JniLibsToBin.jniLibsToBin(path, getInputStream(activity, getLocalLibId(activity, "zip_symlinks_")));
                    installSoTar(path, "bash");
                    installTar(path, getInputStream(activity, getLocalLibId(activity, "bash_")));
                    JniLibsToBin.jniLibsToBin(path, getInputStream(activity, getLocalLibId(activity, "bash_symlinks_")));
                    installSoTar(path, "term-bash");
                    installTar(path, getInputStream(activity, getLocalLibId(activity, "term_bash_")));
                    JniLibsToBin.jniLibsToBin(path, getInputStream(activity, getLocalLibId(activity, "term_bash_symlinks_")));
                    if (!new File(TermService.getHOME() + "/.bashrc").exists()) {
                        shell("cat " + TermService.getAPPFILES() + "/usr/etc/bash.bashrc > " + TermService.getHOME() + "/.bashrc");
                    }
                    JniLibsToBin.jniLibsToBin(path, JniLibsToBin.JNIlIBS_MAP);
                    installBusyboxCommands();
                }
            }
            doInstallTerm = false;
            new PrefValue(activity).setString(TERM_VERSION_KEY, TermService.APP_VERSION);
            if (whenDone != null) whenDone.run();
            return true;
        }
        return false;
    }

    static private int getLocalLibId(AppCompatActivity activity, String tarName) {
        String arch = getArch();
        String arch32 = arch.contains("arm") ? "arm" : "x86";
        String bin = tarName + arch;
        int id = activity.getResources().getIdentifier(bin, "raw", activity.getPackageName());
        if (id == 0) {
            bin = tarName + arch32;
            id = activity.getResources().getIdentifier(bin, "raw", activity.getPackageName());
        }
        return id;
    }

    static public boolean ScopedStorageWarning = false;
    static void doInstallVim(final AppCompatActivity activity, final Runnable whenDone, final boolean installHelp) {
        File force32 = new File(TermService.getVersionFilesDir() + "/.32bit");
        File force64 = new File(TermService.getVersionFilesDir() + "/.64bit");
        if (force32.exists() || force64.exists()) {
            Term.recoveryDelete();
            force32.delete();
            force64.delete();
        }

        ScopedStorageWarning = SCOPED_STORAGE && new PrefValue(activity).getBoolean("enableScopedStorageWarning", true);
        final String path = TermService.getAPPFILES();
        final String versionPath = TermService.getVersionFilesDir();
        INSTALL_ZIP = activity.getString(R.string.update_message);
        INSTALL_WARNING = "\n\n" + activity.getString(R.string.update_warning);
        if (FLAVOR_VIM) INSTALL_WARNING += "\n" + activity.getString(R.string.update_vim_warning);
        boolean first = !new File(TermService.getAPPFILES() + "/bin").isDirectory();

//        final ProgressDialog pd = ProgressDialog.show(activity, null, activity.getString(R.string.update_message), true, false);
        final AlertDialog pd = new AlertDialog.Builder(activity).create();
        pd.setTitle(null);
        pd.setMessage(activity.getString(R.string.update_message));
        pd.setCancelable(false);
        pd.setCanceledOnTouchOutside(false);
        pd.show();

        final DrawerLayout layout = activity.findViewById(R.id.drawer_layout);
        final ProgressBar progressBar = new ProgressBar(activity);
        progressBar.setIndeterminate(true);
        Term.showProgressRing(layout, progressBar);
        new Thread() {
            @Override
            public void run() {
                final int orientation = activity.getRequestedOrientation();
                fixOrientation(activity, orientationLock(activity));
                try {
                    if (FLAVOR_VIM) showWhatsNew(activity, first);
                    setMessage(activity, pd, "term");
                    doInstallTerm(activity);
                    if (FLAVOR_TERMINAL) return;
                    setMessage(activity, pd, "scripts");
                    int id = activity.getResources().getIdentifier("shell", "raw", activity.getPackageName());
                    installZip(path, getInputStream(activity, id));
                    id = activity.getResources().getIdentifier("shell_vim", "raw", activity.getPackageName());
                    installZip(path, getInputStream(activity, id));
                    String vimsh = TermService.getAPPFILES() + "/bin/vim";
                    if (!new File(vimsh).exists()) {
                        String libSrcDefaultVim = TermService.getAPPLIB() + "/libsrc.vim.default.so";
                        if (new File(libSrcDefaultVim).exists()) {
                            JniLibsToBin.jniLibsToBin(path, "libsrc.vim.default.so", "/bin/vim");
                        } else {
                            shell("cat " + TermService.getAPPFILES() + "/usr/etc/src.vim.default" + " > " + vimsh);
                            shell("chmod 755 " + vimsh);
                        }
                    }
                    setMessage(activity, pd, "binaries");
                    id = activity.getResources().getIdentifier("libpreload", "raw", activity.getPackageName());
                    installZip(path, getInputStream(activity, id));
                    installInternalBusybox(path + "/usr/bin");
                    setMessage(activity, pd, "binaries - bin tools");
                    installSoZip(path, "bin");
                    installZip(path, getInputStream(activity, getLocalLibId(activity, "bin_")));
                    setMessage(activity, pd, "binaries - zip tools");
                    installSoTar(path, "unzip");
                    installTar(path, getInputStream(activity, getLocalLibId(activity, "unzip_")));
                    JniLibsToBin.jniLibsToBin(path, getInputStream(activity, getLocalLibId(activity, "unzip_symlinks_")));
                    installSoTar(path, "zip");
                    installTar(path, getInputStream(activity, getLocalLibId(activity, "zip_")));
                    JniLibsToBin.jniLibsToBin(path, getInputStream(activity, getLocalLibId(activity, "zip_symlinks_")));

                    if (AndroidCompat.SDK >= Build.VERSION_CODES.LOLLIPOP) {
                        setMessage(activity, pd, "binaries - shell");
                        String local = versionPath + "/version.bash";
                        String target = TermService.getTMPDIR() + "/version";
                        id = activity.getResources().getIdentifier("version_bash", "raw", activity.getPackageName());
                        copyScript(activity.getResources().openRawResource(id), target);
                        File targetVer = new File(target);
                        File localVer = new File(local);
                        // if (isNeedUpdate(targetVer, localVer)) {
                            installSoTar(path, "bash");
                            installTar(path, getInputStream(activity, getLocalLibId(activity, "bash_")));
                            JniLibsToBin.jniLibsToBin(path, getInputStream(activity, getLocalLibId(activity, "bash_symlinks_")));
                            installSoTar(path, "term-bash");
                            installTar(path, getInputStream(activity, getLocalLibId(activity, "term_bash_")));
                            JniLibsToBin.jniLibsToBin(path, getInputStream(activity, getLocalLibId(activity, "term_bash_symlinks_")));
                            id = activity.getResources().getIdentifier("version_bash", "raw", activity.getPackageName());
                            copyScript(activity.getResources().openRawResource(id), versionPath + "/version.bash");
                        // }
                        targetVer.delete();
                        if (!new File(TermService.getHOME() + "/.bashrc").exists()) {
                            shell("cat " + TermService.getAPPFILES() + "/usr/etc/bash.bashrc > " + TermService.getHOME() + "/.bashrc");
                        }

                        String bin_am = "bin_am";
                        id = activity.getResources().getIdentifier(bin_am, "raw", activity.getPackageName());
                        installZip(path, getInputStream(activity, id));
                        id = activity.getResources().getIdentifier("am", "raw", activity.getPackageName());
                        copyScript(activity.getResources().openRawResource(id), TermService.getAPPFILES() + "/bin/am");
                    }

                    setMessage(activity, pd, "binaries - vim");
                    installSoTar(path, "vim");
                    installTar(path, getInputStream(activity, getLocalLibId(activity, "vim_")));
                    id = activity.getResources().getIdentifier("suvim", "raw", activity.getPackageName());
                    String dst = TermService.getAPPFILES() + "/bin/suvim";
                    copyScript(activity.getResources().openRawResource(id), dst);
                    shell("chmod 755 " + dst);
                    id = activity.getResources().getIdentifier("suvim_sh", "raw", activity.getPackageName());
                    dst = TermService.getAPPFILES() + "/bin/vim.sh";
                    copyScript(activity.getResources().openRawResource(id), dst);
                    shell("chmod 755 " + dst);
                    createExecCheckCmdFile(activity);

                    String runtimeDir = TermService.getVimRuntimeInstallDir();
                    setMessage(activity, pd, "runtime");
                    id = activity.getResources().getIdentifier("runtime", "raw", activity.getPackageName());
                    installTar(runtimeDir, getInputStream(activity, id));
                    setMessage(activity, pd, "lang");
                    id = activity.getResources().getIdentifier("runtimelang", "raw", activity.getPackageName());
                    installTar(runtimeDir, getInputStream(activity, id));
                    setMessage(activity, pd, "spell");
                    id = activity.getResources().getIdentifier("runtimespell", "raw", activity.getPackageName());
                    installTar(runtimeDir, getInputStream(activity, id));
                    setMessage(activity, pd, "syntax");
                    id = activity.getResources().getIdentifier("runtimesyntax", "raw", activity.getPackageName());
                    installTar(runtimeDir, getInputStream(activity, id));
                    if (installHelp) {
                        setMessage(activity, pd, "doc");
                        id = activity.getResources().getIdentifier("runtimedoc", "raw", activity.getPackageName());
                        installTar(runtimeDir, getInputStream(activity, id));
                    }
                    setMessage(activity, pd, "tutor");
                    id = activity.getResources().getIdentifier("runtimetutor", "raw", activity.getPackageName());
                    installTar(runtimeDir, getInputStream(activity, id));

                    id = activity.getResources().getIdentifier("runtime_extra", "raw", activity.getPackageName());
                    installZip(runtimeDir, getInputStream(activity, id));
                    if (first) {
                        id = activity.getResources().getIdentifier("runtime_user", "raw", activity.getPackageName());
                        String homeDir = TermService.getHOME();
                        installZip(homeDir, getInputStream(activity, id));
                    }
                    File appExtHome= new File(TermService.getAPPEXTHOME());
                    if (!appExtHome.exists()) {
                        appExtHome.mkdirs();
                        id = activity.getResources().getIdentifier("app_ext_home", "raw", activity.getPackageName());
                        installZip(TermService.getAPPEXTFILES(), getInputStream(activity, id));
                    }
                    setMessage(activity, pd, "symlinks");
                    JniLibsToBin.jniLibsToBin(path, JniLibsToBin.JNIlIBS_MAP);
                    installBusyboxCommands();
                    setAmbiWidthToVimrc(mSettings.getAmbiWidth());
                    setupStorageSymlinks(activity);
                    JniLibsToBin.symlinkDebugReport(path);
                    id = activity.getResources().getIdentifier("version", "raw", activity.getPackageName());
                    copyScript(activity.getResources().openRawResource(id), versionPath + "/version");
                    new PrefValue(activity).setString(TermService.VERSION_NAME_KEY, TermService.APP_VERSION);
                } finally {
                    if (!activity.isFinishing() && pd != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    pd.dismiss();
                                    Term.dismissProgressRing(layout, progressBar);
                                } catch (Exception e) {
                                    // Do nothing
                                }
                                doInstallVim = false;
                                if (whenDone != null) whenDone.run();
                            }
                        });
                    }
                    if (!activity.isFinishing()) fixOrientation(activity, orientation);
                }
            }
        }.start();
    }

    static public void setAmbiWidthToVimrc(int ambiWidth) {
        if (ambiWidth == 1) {
            busybox("sed -i -e 's/set ambiwidth=double/set ambiwidth=single/g' " + TermService.getAPPFILES() + "/vimrc");
        } else {
            busybox("sed -i -e 's/set ambiwidth=single/set ambiwidth=double/g' " + TermService.getAPPFILES() + "/vimrc");
        }
    }

    static public void createExecCheckCmdFile(AppCompatActivity activity) {
        try {
            String dst = TermService.getVersionFilesDir() + Term.EXEC_STATUS_CHECK_CMD_FILE;
            if (new File(dst).exists()) return;
            int id = activity.getResources().getIdentifier("exec_check", "raw", activity.getPackageName());
            copyScript(activity.getResources().openRawResource(id), dst);
            shell("chmod 755 " + dst);
        } catch (Exception e) {
            e.printStackTrace();
            // Do nothing
        }
    }

    static private boolean isNeedUpdate(File target, File local) {
        if (target == null || !target.exists()) return false;
        if (local == null || !local.exists()) return true;
        boolean needUpdate = true;
        try {
            if (local.exists()) {
                byte[] b1 = new byte[(int) local.length()];
                byte[] b2 = new byte[(int) target.length()];
                try {
                    new FileInputStream(local).read(b1);
                    new FileInputStream(target).read(b2);
                    if (Arrays.equals(b1, b2)) {
                        needUpdate = false;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            target.delete();
            return true;
        }
        target.delete();
        return needUpdate;
    }

    static private String getSolibPath(AppCompatActivity activity) {
        if (SOLIB_PATH != null) return SOLIB_PATH;
        String soLibPath = TermService.getAPPLIB();
        if (activity != null) soLibPath = activity.getApplicationContext().getApplicationInfo().nativeLibraryDir;
        return soLibPath;
    }

    static private void installSoTar(String path, String soLib) {
        try {
            SOLIB_PATH = getSolibPath(null);
            String fname = "lib" + soLib + ".tar.so";
            String local = TermService.getTMPDIR() + "/" + fname;
            cp(SOLIB_PATH + "/" + fname, local);
            installTar(path, new FileInputStream(local));
            new File(local).delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static public boolean cp(String src, String dst) {
        try {
            InputStream is = new FileInputStream(src);
            OutputStream os = new FileOutputStream(dst);
            return cpStream(is, os);
        } catch (Exception e) {
            return false;
        }
    }

    static public boolean cpStream(InputStream is, OutputStream os) {
        if (is == null || os == null) return false;
        try {
            byte[] buf = new byte[1024 * 4];
            int len = 0;

            while ((len = is.read(buf)) > 0) {
                os.write(buf, 0, len);
            }
            os.flush();
            is.close();
            os.close();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    static private void installTar(String path, InputStream is) {
        if (is == null) return;
        try {
            String type = "tar.xz";
            String local = TermService.getTMPDIR() + "/tmp." + type;
            FileOutputStream fileOutputStream = new FileOutputStream(local);
            byte[] buffer = new byte[1024];
            int length = 0;
            while ((length = is.read(buffer)) >= 0) {
                fileOutputStream.write(buffer, 0, length);
            }
            fileOutputStream.close();
            is.close();

            extractXZ(local, path);
            new File(local).delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("StaticFieldLeak")
    static private AppCompatActivity mActivity;
    static private boolean mProgressToast = false;
    static private final Handler mProgressToastHandler = new Handler();
    static private int mProgressToastHandlerMillis = 0;
    static private final int PROGRESS_TOAST_HANDLER_MILLIS = 5000;
    static private final Runnable mProgressToastRunner = new Runnable() {
        @Override
        public void run() {
            if (mProgressToastHandler != null) {
                mProgressToastHandler.removeCallbacks(mProgressToastRunner);
            }
            if (mProgressToast) {
                try {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mProgressToastHandlerMillis += PROGRESS_TOAST_HANDLER_MILLIS;
                            CharSequence mes;
                            if (mProgressToastHandlerMillis >= 3 * 60 * 1000) {
                                mes = "ERROR : Time out.";
                                mProgressToast = false;
                            } else {
                                mes = mActivity.getString(R.string.message_please_wait);
                                if (mProgressToastHandler != null) {
                                    mProgressToastHandler.postDelayed(mProgressToastRunner, PROGRESS_TOAST_HANDLER_MILLIS);
                                }
                            }
                            toast(mActivity, mes.toString());
                        }
                    });
                } catch (Exception e) {
                    // Activity already dismissed - ignore.
                }
            }
        }
    };

    private static void showProgressToast(final AppCompatActivity activity, boolean show) {
        if (!show) {
            mProgressToast = false;
            return;
        }

        mActivity = activity;
        try {
            mProgressToast = true;
            if (mProgressToastHandler != null)
                mProgressToastHandler.removeCallbacks(mProgressToastRunner);
            mProgressToastHandlerMillis = 0;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mProgressToastHandler.postDelayed(mProgressToastRunner, PROGRESS_TOAST_HANDLER_MILLIS);
                }
            });
        } catch (Exception e) {
            // Do nothing
        }
    }

    public static void extractXZ(final AppCompatActivity activity, final String in, final String outDir) {
        showProgressToast(activity, true);
        extractXZ(in, outDir);
    }

    public static void extractXZ(final String in, final String outDir) {
        try {
            String opt = (in.matches(".*.tar.xz|.*.so$")) ? " Jxf " : " xf ";
            if (busybox(null)) {
                busybox("tar " + opt + " " + new File(in).getAbsolutePath() + " -C " + outDir);
                busybox("chmod -R 755 " + TermService.getAPPFILES() + "/bin");
                busybox("chmod -R 755 " + TermService.getAPPFILES() + "/usr/bin");
                busybox("chmod -R 755 " + TermService.getAPPFILES() + "/usr/lib");
                busybox("chmod -R 755 " + TermService.getAPPFILES() + "/usr/libexec");
                return;
            }
            TarArchiveInputStream fin;
            FileInputStream is = new FileInputStream(in);
            if (in.matches(".*.tar.xz|.*.so$")) {
                XZCompressorInputStream xzIn = new XZCompressorInputStream(is);
                fin = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", xzIn);
            } else {
                fin = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is);
            }
            TarArchiveEntry entry;
            while ((entry = fin.getNextTarEntry()) != null) {
                final File file = new File(outDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!file.exists()) file.mkdirs();
                } else if (entry.isFile()) {
                    shell("rm " + file.getAbsolutePath());
                    final OutputStream outputFileStream = new FileOutputStream(file);
                    IOUtils.copy(fin, outputFileStream);
                    outputFileStream.close();
                    int mode = entry.getMode();
                    if ((mode & 0x49) != 0) {
                        file.setExecutable(true, false);
                    }
                    if (file.getName().matches(".*/?bin/.*")) {
                        file.setExecutable(true, false);
                    }
                    if (file.getName().matches(".*\\.so\\.?.*")) {
                        file.setExecutable(true, false);
                    }
                }
            }
            fin.close();
            is = new FileInputStream(in);
            if (in.matches(".*.tar.xz|.*.so$")) {
                XZCompressorInputStream xzIn = new XZCompressorInputStream(is);
                fin = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", xzIn);
            } else {
                fin = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is);
            }
            while ((entry = fin.getNextTarEntry()) != null) {
                final File file = new File(outDir, entry.getName());
                if (entry.isSymbolicLink()) {
                    try {
                        String symlink = file.getAbsolutePath();
                        String target = file.getAbsoluteFile().getParent() + "/" + entry.getLinkName();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            if (new File(target).exists()) {
                                file.delete();
                                Os.symlink(target, symlink);
                            }
                        } else {
                            file.delete();
                            shell("ln -s " + file.getAbsolutePath() + " " + symlink);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            fin.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mProgressToast = false;
        }
    }

    static public void setupStorageSymlinks(final AppCompatActivity activity) {
        try {
            File storageDir = new File(TermService.getHOME());
            String symlink = "internalStorage";

            File internalDir = Environment.getExternalStorageDirectory();
            if (!internalDir.canWrite()) {
                internalDir = new File(TermService.getEXTSTORAGE());
            }
            shell("rm " + new File(storageDir, symlink).getAbsolutePath());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Os.symlink(internalDir.getAbsolutePath(), new File(storageDir, symlink).getAbsolutePath());
            } else {
                shell("ln -s " + internalDir.getAbsolutePath() + " " + storageDir.getAbsolutePath() + "/" + symlink);
            }

            final File[] dirs = activity.getExternalFilesDirs(null);
            if (dirs != null && dirs.length > 1) {
                for (int i = 1; i < dirs.length; i++) {
                    File dir = dirs[i];
                    if (dir == null) continue;
                    String symlinkName = "external-" + i;
                    if (dir.canWrite()) {
                        File link = new File(storageDir, symlinkName);
                        shell("rm " + link.getAbsolutePath());
                        Os.symlink(dir.getAbsolutePath(), link.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TermDebug.LOG_TAG, "Error setting up link", e);
        }
    }

    static void showWhatsNew(final AppCompatActivity activity, final boolean first) {
        final String whatsNew = BuildConfig.WHATS_NEW;
        if (!first && whatsNew.equals("")) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder bld = new AlertDialog.Builder(activity);
                if (first) {
                    bld.setTitle(activity.getString(R.string.tips_vim_title));
                    String message = activity.getString(R.string.tips_vim);
                    if (SCOPED_STORAGE) {
                        message = activity.getString(R.string.scoped_storage_first_warning_message) + "\n\n" + message;
                    }
                    bld.setMessage(message);
                } else {
                    bld.setTitle(activity.getString(R.string.whats_new_title));
                    bld.setMessage(whatsNew);
                }
                bld.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        if (!first) showVimTips(activity);
                    }
                });
                final Term term = (Term) activity;
                try {
                    AlertDialog dialog = bld.create();
                    dialog.show();
                    Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    positive.requestFocus();
                } catch (Exception e) {
                    // do nothing
                }
            }
        });
    }

    static public int copyScript(InputStream is, String fname) {
        return doCopyScript(is, fname, null);
    }

    static public int copyScript(InputStream is, String fname, String strings) {
        return doCopyScript(is, fname, strings);
    }

    static public int doCopyScript(InputStream is, String fname, String strings) {
        if (is == null) return -1;
        BufferedReader br = null;
        File dir = new File(fname);
        if (dir.getParent() != null) new File(dir.getParent()).mkdirs();
        try {
            try {
                String term = ShellTermSession.getTermType();
                String colorFgBg = ShellTermSession.getColorFGBG();
                if (mSettings != null) {
                    try {
                        term = mSettings.getTermType();
                        colorFgBg = mSettings.getCOLORFGBG();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (term == null) term = "screen-256color";
                if (colorFgBg == null) colorFgBg = "";
                String appBase = TermService.getAPPBASE();
                String appExtFiles = TermService.getAPPEXTFILES();
                String appExtHome = TermService.getAPPEXTHOME();
                String appFiles = TermService.getAPPFILES();
                String appLib = TermService.getAPPLIB();
                String home = TermService.getHOME();
                String internalStorage = TermService.getEXTSTORAGE();
                String lang = TermService.getLANG();
                String ld_library_path = TermService.getLD_LIBRARY_PATH();
                String path = TermService.getPATH();
                String terminfo = TermService.getTERMINFO();
                String tmpDir = TermService.getTMPDIR();
                String vim = TermService.getVIM();
                String vimRuntime = TermService.getVIMRUNTIME();

                br = new BufferedReader(new InputStreamReader(is));
                PrintWriter writer = new PrintWriter(new FileOutputStream(fname));
                String str;
                while ((str = br.readLine()) != null) {
                    str = str.replaceAll("%APPBASE%", appBase);
                    str = str.replaceAll("%APPEXTFILES%", appExtFiles);
                    str = str.replaceAll("%APPEXTHOME%", appExtHome);
                    str = str.replaceAll("%APPFILES%", appFiles);
                    str = str.replaceAll("%APPLIB%", appLib);
                    str = str.replaceAll("%COLORFGBG%", colorFgBg);
                    str = str.replaceAll("%HOME%", home);
                    str = str.replaceAll("%INTERNAL_STORAGE%", internalStorage);
                    str = str.replaceAll("%LANG%", lang);
                    str = str.replaceAll("%LD_LIBRARY_PATH%", ld_library_path);
                    str = str.replaceAll("%PATH%", path);
                    str = str.replaceAll("%TERM%", term);
                    str = str.replaceAll("%TERMINFO%", terminfo);
                    str = str.replaceAll("%TMPDIR%", tmpDir);
                    str = str.replaceAll("%VIM%", vim);
                    str = str.replaceAll("%VIMRUNTIME%", vimRuntime);
                    if (strings != null && str.contains("%%STRINGS%%")) {
                        String prev = str.replaceFirst("%%STRINGS%%.*", "");
                        if (!prev.equals("")) writer.print(prev);
                        writer.print(strings);
                        writer.print(str.replaceFirst(".*%%STRINGS%%", ""));
                    } else {
                        writer.print(str + "\n");
                    }
                }
                writer.close();
            } catch (Exception e) {
                return 1;
            } finally {
                is.close();
                if (br != null) br.close();
            }
        } catch (IOException e) {
            return 1;
        }
        if (fname.contains("/bin/")) shell("chmod 755 " + fname);
        return 0;
    }

    static private InputStream getInputStream(final AppCompatActivity activity, int id) {
        InputStream is = null;
        try {
            is = activity.getResources().openRawResource(id);
        } catch (Exception e) {
            // do nothing
        }
        return is;
    }

    static void deleteFileOrFolder(File fileOrDirectory) {
        String opt = fileOrDirectory.isDirectory() ? " -rf " : "";
        shell("rm " + opt + fileOrDirectory.getAbsolutePath());
        if (fileOrDirectory.exists()) {
            // throw new RuntimeException("Unable to delete " + (fileOrDirectory.isDirectory() ? "directory " : "file ") + fileOrDirectory.getAbsolutePath());
        }
    }

    static private void installInternalBusybox(String target) {
        String bin = "libbusybox.so";
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            bin = "libbusybox_api16.so";
        }
        JniLibsToBin.jniLibsToBin(target, bin, "/busybox");
    }

    static public void installBusyboxCommands() {
        final File destDir = new File(TermService.getAPPFILES());
        final File outDir = new File(TermService.getAPPFILES() + "/usr/bin");
        shell("export APPFILES=" + destDir.toString(), outDir + "/busybox-setup all");
    }

    static public boolean busybox(String cmd) {
        String busybox = TermService.getAPPFILES() + "/usr/bin/busybox";
        File file = new File(busybox);
        if (!file.canExecute()) {
            installInternalBusybox(file.getParent());
        }
        boolean canExecute = file.canExecute();
        if (cmd == null || !canExecute) return canExecute;

        String busyboxCommand = busybox + " " + cmd;
        shell(busyboxCommand);
        return true;
    }

    static public void shell(String... commands) {
        List<String> shellCommands = new ArrayList<>();
        String[] prootCommands = getProotCommand();
        boolean proot = !Arrays.equals(prootCommands, new String[]{});

        if (proot) shellCommands.addAll(Arrays.asList(prootCommands));
        shellCommands.addAll(Arrays.asList(commands));
        shellCommands.add("exit");
        if (proot) shellCommands.add("exit");

        try {
            Process shell = Runtime.getRuntime().exec("sh");
            DataOutputStream sh = new DataOutputStream(shell.getOutputStream());

            for (String s : shellCommands) {
                sh.writeBytes(s + "\n");
                sh.flush();
            }

            try {
                shell.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sh.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static private String INSTALL_ZIP = "";
    static private String INSTALL_WARNING = "";

    static private void setMessage(final AppCompatActivity activity, final AlertDialog pd, final String message) {
        if (!activity.isFinishing() && pd != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pd.setMessage(INSTALL_ZIP + "\n- " + message + INSTALL_WARNING);
                }
            });
        }
    }

    static private void setMessage(final AppCompatActivity activity, final ProgressRingDialog pd, final String message) {
        if (!activity.isFinishing() && pd != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pd.setMessage(INSTALL_ZIP + "\n- " + message + INSTALL_WARNING);
                }
            });
        }
    }

    static private void installSoZip(String path, String soLib) {
        try {
            SOLIB_PATH = getSolibPath(null);
            final String arch = "_" + getArch();
            File soFile = new File(SOLIB_PATH + "/lib" + soLib + arch + ".so");
            installZip(path, new FileInputStream(soFile));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static public void installZip(String path, InputStream is) {
        if (is == null) return;
        try {
            File outDir = new File(path);
            outDir.mkdirs();
            ZipInputStream zin = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            int size;
            byte[] buffer = new byte[8192];

            while ((ze = zin.getNextEntry()) != null) {
                if (ze.isDirectory()) {
                    File file = new File(path + "/" + ze.getName());
                    if (!file.isDirectory()) file.mkdirs();
                } else {
                    File file = new File(path + "/" + ze.getName());
                    File parentFile = file.getParentFile();
                    parentFile.mkdirs();

                    file.delete();
                    FileOutputStream fout = new FileOutputStream(file);
                    BufferedOutputStream bufferOut = new BufferedOutputStream(fout, buffer.length);
                    while ((size = zin.read(buffer, 0, buffer.length)) != -1) {
                        bufferOut.write(buffer, 0, size);
                    }
                    bufferOut.flush();
                    bufferOut.close();
                    if (ze.getName().matches(".*/?bin/.*")) {
                        if (AndroidCompat.SDK >= 9) file.setExecutable(true, false);
                    }
                    if (ze.getName().matches(".*/?lib/.*")) {
                        if (AndroidCompat.SDK >= 9) file.setExecutable(true, false);
                    }
                }
            }

            byte[] buf = new byte[2048];
            while (is.available() > 0) {
                is.read(buf);
            }
            zin.close();
        } catch (Exception e) {
        }
    }

    static String getArch() {
        return TermService.getArch();
    }

    static String getProp(String propName) {
        Process process = null;
        BufferedReader bufferedReader = null;

        try {
            final String GETPROP_PATH = "/system/bin/getprop";
            process = new ProcessBuilder().command(GETPROP_PATH, propName).redirectErrorStream(true).start();
            bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return bufferedReader.readLine();
        } catch (Exception e) {
            return null;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
            if (process != null) {
                process.destroy();
            }
        }
    }

    public static void showVimTips(final AppCompatActivity activity) {
        if (!FLAVOR_VIM) return;
        try {
            String title = activity.getString(R.string.tips_vim_title);
            String[] list = activity.getString(R.string.tips_vim_list).split("\t");
            int index = mRandom.nextInt(list.length);
            String message = list[index];
            AlertDialog.Builder bld = new AlertDialog.Builder(activity);
            bld.setTitle(title);
            bld.setMessage(message);
            bld.setPositiveButton(android.R.string.yes, null);
            AlertDialog dialog = bld.create();
            dialog.show();
        } catch (Exception e) {
            // do nothing
        }
    }

    private static final Random mRandom = new Random();

    static void toast(final AppCompatActivity activity, final String message) {
        try {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Snackbar snackbar = Snackbar.make(activity.findViewById(R.id.term_coordinator_layout_top), message, Snackbar.LENGTH_LONG);
                    View snackbarView = snackbar.getView();
                    TextView tv= snackbarView.findViewById(R.id.snackbar_text);
                    tv.setMaxLines(3);
                    snackbar.show();
                }
            });
        } catch (Exception e) {
            // Activity already dismissed - ignore.
        }
    }

}

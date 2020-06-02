package jackpal.androidterm;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
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
import android.widget.TextView;

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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import androidx.preference.PreferenceManager;
import jackpal.androidterm.compat.AndroidCompat;

import static jackpal.androidterm.ShellTermSession.getProotCommand;
import static jackpal.androidterm.StaticConfig.FLAVOR_VIM;
import static jackpal.androidterm.StaticConfig.SCOPED_STORAGE;

final class TermVimInstaller {
    static final String TERMVIM_VERSION = String.format(Locale.US, "%d : %s", BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME);
    static final boolean OS_AMAZON = System.getenv("AMAZON_COMPONENT_LIST") != null;
    static public boolean doInstallVim = false;
    static private final String DEBUG_OLD_LST = "";

    static void installVim(final Activity activity, final Runnable whenDone) {
        if (!doInstallVim) return;

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
            b.show();
        }
    }

    static private int orientationLock(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return ActivityInfo.SCREEN_ORIENTATION_LOCKED;
        }
        Configuration config = activity.getResources().getConfiguration();
        switch (config.orientation) {
            case Configuration.ORIENTATION_PORTRAIT:
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case Configuration.ORIENTATION_LANDSCAPE:
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
        return ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
    }

    static private void fixOrientation(final Activity activity, final int orientation) {
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

    static boolean doInstallTerm(final Activity activity) {
        SharedPreferences pref = activity.getApplicationContext().getSharedPreferences("dev", Context.MODE_PRIVATE);
        String terminfoDir = TermService.getTerminfoInstallDir();
        File dir = new File(terminfoDir + "/terminfo");
        boolean doInstall = !dir.isDirectory() || !pref.getString("versionName", "").equals(TERMVIM_VERSION);

        if (doInstall) {
            int id = activity.getResources().getIdentifier("terminfo_min", "raw", activity.getPackageName());
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
                    installInternalBusybox(activity, path + "/usr/bin");
                    installSoTar(path, "unzip");
                    installTar(path, getInputStream(activity, getLocalLibId(activity, "unzip_")));
                    installSoTar(path, "zip");
                    installTar(path, getInputStream(activity, getLocalLibId(activity, "zip_")));
                    installSoTar(path, "bash");
                    installTar(path, getInputStream(activity, getLocalLibId(activity, "bash_")));
                    if (!new File(TermService.getHOME() + "/.bashrc").exists()) {
                        shell("cat " + TermService.getAPPFILES() + "/usr/etc/bash.bashrc > " + TermService.getHOME() + "/.bashrc");
                    }
                }
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
                final SharedPreferences.Editor editor = prefs.edit();
                String key = "functionbar_vim_paste";
                boolean value = prefs.getBoolean(key, false);
                editor.putBoolean(key, value);
                key = "functionbar_vim_yank";
                value = prefs.getBoolean(key, false);
                editor.putBoolean(key, value);
                editor.apply();
                new PrefValue(activity).setString("versionName", TERMVIM_VERSION);
            }
            return true;
        }
        return false;
    }

    static private int getLocalLibId(Activity activity, String tarName) {
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
    static void doInstallVim(final Activity activity, final Runnable whenDone, final boolean installHelp) {
        ScopedStorageWarning = SCOPED_STORAGE && new PrefValue(activity).getBoolean("enableScopedStorageWarning", true);
        final String path = TermService.getAPPFILES();
        final String versionPath = TermService.getVersionFilesDir();
        INSTALL_ZIP = activity.getString(R.string.update_message);
        INSTALL_WARNING = "\n\n" + activity.getString(R.string.update_warning);
        if (FLAVOR_VIM) INSTALL_WARNING += "\n" + activity.getString(R.string.update_vim_warning);
        final ProgressDialog pd = ProgressDialog.show(activity, null, activity.getString(R.string.update_message), true, false);
        new Thread() {
            @Override
            public void run() {
                final int orientation = activity.getRequestedOrientation();
                fixOrientation(activity, orientationLock(activity));
                try {
                    boolean first = !new File(TermService.getAPPFILES() + "/bin").isDirectory();
                    showWhatsNew(activity, first);
                    setMessage(activity, pd, "scripts");
                    doInstallTerm(activity);
                    int id = activity.getResources().getIdentifier("shell", "raw", activity.getPackageName());
                    installZip(path, getInputStream(activity, id));
                    id = activity.getResources().getIdentifier("shell_vim", "raw", activity.getPackageName());
                    installZip(path, getInputStream(activity, id));
                    String defaultVim = TermService.getAPPFILES() + "/bin/vim.default";
                    String vimsh = TermService.getAPPFILES() + "/bin/vim";
                    if ((!new File(defaultVim).exists()) || (!new File(vimsh).exists())) {
                        shell("cat " + TermService.getAPPFILES() + "/usr/etc/src.vim.default" + " > " + vimsh);
                        shell("chmod 755 " + vimsh);
                    }
                    setMessage(activity, pd, "binaries");
                    id = activity.getResources().getIdentifier("libpreload", "raw", activity.getPackageName());
                    installZip(path, getInputStream(activity, id));
                    installInternalBusybox(activity, path + "/usr/bin");
                    installSoZip(path, "bin");
                    installZip(path, getInputStream(activity, getLocalLibId(activity, "bin_")));
                    installSoTar(path, "unzip");
                    installTar(path, getInputStream(activity, getLocalLibId(activity, "unzip_")));
                    installSoTar(path, "zip");
                    installTar(path, getInputStream(activity, getLocalLibId(activity, "zip_")));

                    if (AndroidCompat.SDK >= Build.VERSION_CODES.LOLLIPOP) {
                        setMessage(activity, pd, "binaries - shell");
                        String local = versionPath + "/version.bash";
                        String target = TermService.getTMPDIR() + "/version";
                        id = activity.getResources().getIdentifier("version_bash", "raw", activity.getPackageName());
                        copyScript(activity.getResources().openRawResource(id), target);
                        File targetVer = new File(target);
                        File localVer = new File(local);
                        if (isNeedUpdate(targetVer, localVer)) {
                            installSoTar(path, "bash");
                            installTar(path, getInputStream(activity, getLocalLibId(activity, "bash_")));
                            if (!new File(TermService.getHOME() + "/.bashrc").exists()) {
                                shell("cat " + TermService.getAPPFILES() + "/usr/etc/bash.bashrc > " + TermService.getHOME() + "/.bashrc");
                            }
                            id = activity.getResources().getIdentifier("version_bash", "raw", activity.getPackageName());
                            copyScript(activity.getResources().openRawResource(id), versionPath + "/version.bash");
                        }
                        targetVer.delete();

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
                    if (!new File(TermService.getAPPEXTHOME()).exists()) {
                        id = activity.getResources().getIdentifier("app_ext_home", "raw", activity.getPackageName());
                        installZip(TermService.getAPPEXTFILES(), getInputStream(activity, id));
                    }
                    id = activity.getResources().getIdentifier("version", "raw", activity.getPackageName());
                    copyScript(activity.getResources().openRawResource(id), versionPath + "/version");
                    setupStorageSymlinks();
                    new PrefValue(activity).setString("versionName", TERMVIM_VERSION);
                } finally {
                    if (!activity.isFinishing() && pd != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    pd.dismiss();
                                } catch (Exception e) {
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

    static private void installSoTar(String path, String soLib) {
        final String SOLIB_PATH = TermService.getAPPLIB();
        try {
            String fname = "lib" + soLib + ".so";
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
    static private Activity mActivity;
    static private boolean mProgressToast = false;
    static private Handler mProgressToastHandler = new Handler();
    static private int mProgressToastHandlerMillis = 0;
    static private final int PROGRESS_TOAST_HANDLER_MILLIS = 5000;
    static private Runnable mProgressToastRunner = new Runnable() {
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

    private static void showProgressToast(final Activity activity, boolean show) {
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

    public static void extractXZ(final Activity activity, final String in, final String outDir) {
        showProgressToast(activity, true);
        extractXZ(in, outDir);
    }

    public static void extractXZ(final String in, final String outDir) {
        try {
            if (busybox(null)) {
                String opt = (in.matches(".*.tar.xz|.*.so$")) ? " Jxf " : " xf ";
                busybox("tar " + opt + " " + new File(in).getAbsolutePath() + " -C " + outDir);
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

    static public void setupStorageSymlinks() {
        if (SCOPED_STORAGE) return;
        try {
            File storageDir = new File(TermService.getHOME());
            String symlink = "internalStorage";

            File internalDir = Environment.getExternalStorageDirectory();
            if (!internalDir.canWrite()) {
                internalDir = new File(TermService.getAPPEXTHOME());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                shell("rm " + new File(storageDir, symlink).getAbsolutePath());
                Os.symlink(internalDir.getAbsolutePath(), new File(storageDir, symlink).getAbsolutePath());
            } else {
                busybox("ln -sf " + internalDir.getAbsolutePath() + " " + storageDir.getAbsolutePath() + "/" + symlink);
            }
        } catch (Exception e) {
            Log.e(TermDebug.LOG_TAG, "Error setting up link", e);
        }
    }

    static void showWhatsNew(final Activity activity, final boolean first) {
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
                String appBase = TermService.getAPPBASE();
                String appFiles = TermService.getAPPFILES();
                String appExtFiles = TermService.getAPPEXTFILES();
                String internalStorage = TermService.getEXTSTORAGE();
                br = new BufferedReader(new InputStreamReader(is));
                PrintWriter writer = new PrintWriter(new FileOutputStream(fname));
                String str;
                while ((str = br.readLine()) != null) {
                    str = str.replaceAll("%APPBASE%", appBase);
                    str = str.replaceAll("%APPFILES%", appFiles);
                    str = str.replaceAll("%APPEXTFILES%", appExtFiles);
                    str = str.replaceAll("%INTERNAL_STORAGE%", internalStorage);
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
                if (br != null) br.close();
            }
        } catch (IOException e) {
            return 1;
        }
        if (fname.contains("/bin/")) shell("chmod 755 " + fname);
        return 0;
    }

    static private InputStream getInputStream(final Activity activity, int id) {
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

    static private void installInternalBusybox(Activity activity, String target) {
        try {
            InputStream is = null;
            final String arch = getArch();
            final String SOLIB_PATH = TermService.getAPPLIB();
            File soFile = new File(SOLIB_PATH + "/libbusybox_" + arch + ".so");
            final File symlink = new File(target+"/busybox");
            if (soFile.exists()) {
/*
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (symlink.exists()) symlink.delete();
                    Os.symlink(soFile.getAbsolutePath(), symlink.getAbsolutePath());
                    return;
                }
*/
                is = new FileInputStream(soFile);
            } else {
                is = getInputStream(activity, getLocalLibId(activity,"busybox_"));
            }
            if (is != null) {
                if (symlink.exists()) symlink.delete();
                cpStream(is , new FileOutputStream(symlink.getAbsolutePath()));
                shell("chmod 755 " + symlink.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static public boolean busybox(String cmd) {
        String busybox = TermService.getAPPLIB() + "/busybox_"+ TermService.getARCH() +".so";
        if (!new File(busybox).canExecute()) {
            busybox = TermService.getAPPFILES() + "/usr/bin/busybox";
        }
        boolean canExecute = new File(busybox).canExecute();
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

    static private void setMessage(final Activity activity, final ProgressDialog pd, final String message) {
        if (!activity.isFinishing() && pd != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pd.setMessage(INSTALL_ZIP + "\n- " + message + INSTALL_WARNING);
                }
            });
        }
    }

    static private void setMessage(final Activity activity, final ProgressRingDialog pd, final String message) {
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
        final String SOLIB_PATH = TermService.getAPPLIB();
        try {
            File soFile = new File(SOLIB_PATH + "/lib" + soLib + ".so");
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
        return TermService.getARCH();
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

    public static void showVimTips(final Activity activity) {
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

    private static Random mRandom = new Random();

    static void toast(final Activity activity, final String message) {
        try {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Snackbar snackbar = Snackbar.make(activity.findViewById(R.id.term_coordinator_layout_top), message, Snackbar.LENGTH_LONG);
                    View snackbarView = snackbar.getView();
                    TextView tv= (TextView) snackbarView.findViewById(R.id.snackbar_text);
                    tv.setMaxLines(3);
                    snackbar.show();
                }
            });
        } catch (Exception e) {
            // Activity already dismissed - ignore.
        }
    }

}

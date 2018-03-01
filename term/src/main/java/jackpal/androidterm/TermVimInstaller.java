package jackpal.androidterm;

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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jackpal.androidterm.compat.AndroidCompat;

import static android.content.Context.MODE_PRIVATE;

final class TermVimInstaller {

    static boolean FLAVOR_VIM = BuildConfig.FLAVOR.matches(".*vim.*");
    static final String TERMVIM_VERSION = String.format(Locale.US, "%d : %s", BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME);
    static final boolean OS_AMAZON = System.getenv("AMAZON_COMPONENT_LIST") != null;
    static public boolean doInstallVim = false;
    static void installVim(final Activity activity, final Runnable whenDone) {
        if (!doInstallVim) return;

        String cpu = System.getProperty("os.arch").toLowerCase();
        if ((AndroidCompat.SDK < 16) || ((AndroidCompat.SDK < 18) && (cpu.contains("x86") || cpu.contains("i686")))) {
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
        String sdcard = TermService.getAPPEXTFILES();
        return sdcard+"/version";
    }

    static boolean doInstallTerm(final Activity activity) {
        final String sdcard = TermService.getAPPEXTFILES();
        SharedPreferences pref = activity.getApplicationContext().getSharedPreferences("dev", Context.MODE_PRIVATE);
        File dir = new File(sdcard+"/terminfo");
        boolean doInstall = !dir.isDirectory() || !pref.getString("versionName", "").equals(TERMVIM_VERSION);

        if (doInstall) {
            int id = activity.getResources().getIdentifier("terminfo", "raw", activity.getPackageName());
            installZip(sdcard, getInputStream(activity, id));
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                File fontPath = new File(TermPreferences.FONTPATH);
                if (!fontPath.exists()) fontPath.mkdirs();
            }
            if (!FLAVOR_VIM) new PrefValue(activity).setString("versionName", TERMVIM_VERSION);
            return true;
        }
        return false;
    }

    static void doInstallVim(final Activity activity, final Runnable whenDone, final boolean installHelp) {
        final String path = TermService.getAPPFILES();
        final String sdcard = TermService.getAPPEXTFILES();
        INSTALL_ZIP = activity.getString(R.string.update_message);
        INSTALL_WARNING = "\n\n"+activity.getString(R.string.update_warning);
        if (FLAVOR_VIM) INSTALL_WARNING += "\n"+activity.getString(R.string.update_vim_warning);
        final ProgressDialog pd = ProgressDialog.show(activity, null, activity.getString(R.string.update_message), true, false);
        new Thread() {
            @Override
            public void run() {
                final int orientation = activity.getRequestedOrientation();
                fixOrientation(activity, orientationLock(activity));
                try {
                    boolean first = !new File(sdcard+"/vimrc").exists();
                    showWhatsNew(activity, first);
                    setMessage(activity, pd, "runtime");
                    doInstallTerm(activity);
                    int id = activity.getResources().getIdentifier("runtime", "raw", activity.getPackageName());
                    installZip(sdcard, getInputStream(activity, id));
                    setMessage(activity, pd, "lang");
                    id = activity.getResources().getIdentifier("runtimelang", "raw", activity.getPackageName());
                    installZip(sdcard, getInputStream(activity, id));
                    setMessage(activity, pd, "spell");
                    id = activity.getResources().getIdentifier("runtimespell", "raw", activity.getPackageName());
                    installZip(sdcard, getInputStream(activity, id));
                    setMessage(activity, pd, "syntax");
                    id = activity.getResources().getIdentifier("runtimesyntax", "raw", activity.getPackageName());
                    installZip(sdcard, getInputStream(activity, id));
                    if (installHelp) {
                        setMessage(activity, pd, "doc");
                        id = activity.getResources().getIdentifier("runtimedoc", "raw", activity.getPackageName());
                        installZip(sdcard, getInputStream(activity, id));
                    }
                    setMessage(activity, pd, "tutor");
                    id = activity.getResources().getIdentifier("runtimetutor", "raw", activity.getPackageName());
                    installZip(sdcard, getInputStream(activity, id));
                    setMessage(activity, pd, "binaries");
                    id = activity.getResources().getIdentifier("extra", "raw", activity.getPackageName());
                    installZip(sdcard, getInputStream(activity, id));
                    id = activity.getResources().getIdentifier("bin", "raw", activity.getPackageName());
                    installZip(path, getInputStream(activity, id));
                    String arch = getArch().contains("arm") ? "arm" : "x86";
                    String bin = "bin_" + arch;
                    id = activity.getResources().getIdentifier(bin, "raw", activity.getPackageName());
                    installZip(path, getInputStream(activity, id));
                    bin = "vim_" + arch;
                    id = activity.getResources().getIdentifier(bin, "raw", activity.getPackageName());
                    installZip(path, getInputStream(activity, id));
                    id = activity.getResources().getIdentifier("version", "raw", activity.getPackageName());
                    copyScript(activity, id, sdcard+"/version");
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

    static void showWhatsNew(final Activity activity, final boolean first) {
        if (BuildConfig.DEBUG) return;
        final String whatsNew = BuildConfig.WHATS_NEW;
        if (!first && whatsNew.equals("")) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder bld = new AlertDialog.Builder(activity);
                String[] list = activity.getString(R.string.tips_vim_list).split("\\|");
                Random random = new Random();
                int index = random.nextInt(list.length);
                if (first) index = 0;
                if (first) {
                    bld.setTitle(activity.getString(R.string.tips_vim_title));
                    bld.setMessage(list[index]);
                } else {
                    bld.setTitle(activity.getString(R.string.whats_new_title));
                    bld.setMessage(whatsNew);
                }
                bld.setPositiveButton("OK", null);
                bld.create().show();
            }
        });
    }

    static private int copyScript(Activity activity,int id, String fname) {
        if (id == 0) return -1;
        BufferedReader br = null;
        try {
            try {
                InputStream is = activity.getResources().openRawResource(id);
                br = new BufferedReader(new InputStreamReader(is));
                PrintWriter writer = new PrintWriter(new FileOutputStream(fname));
                String str;
                while ((str = br.readLine()) != null) {
                    writer.print(str+"\n");
                }
                writer.close();
            } catch (IOException e) {
                return 1;
            } finally {
                if (br != null) br.close();
            }
        } catch (IOException e) {
            return 1;
        }
        return 0;
    }

    static private InputStream getInputStream(final Activity activity, int id) {
        InputStream is = null;
        try {
            is = activity.getResources().openRawResource(id);
        } catch(Exception e) {
            // do nothing
        }
        return is;
    }

    static void deleteFileOrFolder(File fileOrDirectory) {
        File[] children = fileOrDirectory.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteFileOrFolder(child);
            }
        }
        if (!fileOrDirectory.delete()) {
            // throw new RuntimeException("Unable to delete " + (fileOrDirectory.isDirectory() ? "directory " : "file ") + fileOrDirectory.getAbsolutePath());
        }
    }

    static public void shell(String...strings) {
        try{
            Process shell = Runtime.getRuntime().exec("sh");
            DataOutputStream sh = new DataOutputStream(shell.getOutputStream());

            for (String s : strings) {
                sh.writeBytes(s+"\n");
                sh.flush();
            }

            sh.writeBytes("exit\n");
            sh.flush();
            try {
                shell.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sh.close();
        } catch (IOException e){
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

    static public void installZip(String path, InputStream is) {
        if (is == null) return;
        File outDir = new File(path);
        outDir.mkdirs();
        ZipInputStream zin = new ZipInputStream(new BufferedInputStream(is));
        ZipEntry ze;
        int size;
        byte[] buffer = new byte[8192];

        try {
            while ((ze = zin.getNextEntry()) != null) {
                if (ze.isDirectory()) {
                    File file = new File(path+"/"+ze.getName());
                    if (!file.isDirectory()) file.mkdirs();
                } else {
                    File file = new File(path+"/"+ze.getName());
                    File parentFile = file.getParentFile();
                    parentFile.mkdirs();

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
        String cpu;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (String androidArch : Build.SUPPORTED_64_BIT_ABIS) {
                cpu = androidArch.toLowerCase();
                if (cpu.contains("arm64")) {
                    return "arm64";
                }
                if (cpu.contains("x86_64")) {
                    return "x86_64";
                }
            }
            // FIXME: for Kindle
            cpu = getProp("ro.product.cpu.abi");
            if (cpu != null) {
                cpu = cpu.toLowerCase();
                if (cpu.contains("arm64")) {
                    return "arm64";
                }
                if (cpu.contains("x86_64")) {
                    return "x86_64";
                }
            }
            for (String androidArch : Build.SUPPORTED_32_BIT_ABIS) {
                cpu = androidArch.toLowerCase();
                if (cpu.contains("arm")) {
                    return "arm";
                }
                if (cpu.contains("x86") || cpu.contains("i686")) {
                    return "x86";
                }
            }
        } else {
            cpu = System.getProperty("os.arch").toLowerCase();
            if (cpu.contains("arm")) {
                return "arm";
            } else if (cpu.contains("x86") || cpu.contains("i686")) {
                return  "x86";
            }
        }
        return "arm";
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
        } finally{
            if (bufferedReader != null){
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
            if (process != null){
                process.destroy();
            }
        }
    }
}

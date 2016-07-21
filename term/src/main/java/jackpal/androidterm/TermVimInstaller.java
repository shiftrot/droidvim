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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jackpal.androidterm.compat.AndroidCompat;

import static android.content.Context.MODE_PRIVATE;

final class TermVimInstaller {

    static final boolean FLAVOR_VIM = true;
    static final String TERMVIM_VERSION = String.format(Locale.US, "%d : %s", BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME);
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
                    bld.setNeutralButton("OK", null);
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

    static private void fixOrientation(final Activity activity, final int orientation) {
        if (!activity.isFinishing()) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch(orientation) {
                        case Configuration.ORIENTATION_PORTRAIT:
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                            break;
                        case Configuration.ORIENTATION_LANDSCAPE:
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                            break;
                        default :
                            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
                    }
                }
            });
        }
    }

    static String getInstallVersionFile(final Service service) {
        String path = service.getFilesDir().toString();
        File extfilesdir = (AndroidCompat.SDK >= 8) ? service.getExternalFilesDir(null) : null;
        String sdcard = extfilesdir != null ? extfilesdir.toString() : path;
        return sdcard+"/version";
    }

    static String getInstallVersionFile(final Activity activity) {
        String path = activity.getFilesDir().toString();
        File extfilesdir = (AndroidCompat.SDK >= 8) ? activity.getExternalFilesDir(null) : null;
        String sdcard = extfilesdir != null ? extfilesdir.toString() : path;
        return sdcard+"/version";
    }

    static void doInstallVim(final Activity activity, final Runnable whenDone, final boolean installHelp) {
        final String path = activity.getFilesDir().toString();
        File extfilesdir = (AndroidCompat.SDK >= 8) ? activity.getExternalFilesDir(null) : null;
        final String sdcard = extfilesdir != null ? extfilesdir.toString() : path;
        INSTALL_ZIP = activity.getString(R.string.update_vim);
        final ProgressDialog pd = ProgressDialog.show(activity, null, activity.getString(R.string.update_vim), true, false);
        new Thread() {
            @Override
            public void run() {
                final int orientation = activity.getRequestedOrientation();
                Configuration config = activity.getResources().getConfiguration();
                fixOrientation(activity, config.orientation);
                try {
                    int id = activity.getResources().getIdentifier("runtime", "raw", activity.getPackageName());
                    setMessage(activity, pd, "runtime");
                    installZip(sdcard, getInputStream(activity, id));
                    id = activity.getResources().getIdentifier("extra", "raw", activity.getPackageName());
                    installZip(sdcard, getInputStream(activity, id));
                    id = activity.getResources().getIdentifier("bin", "raw", activity.getPackageName());
                    installZip(path, getInputStream(activity, id));
                    id = activity.getResources().getIdentifier("terminfo", "raw", activity.getPackageName());
                    installZip(sdcard, getInputStream(activity, id));
                    id = activity.getResources().getIdentifier("version", "raw", activity.getPackageName());
                    copyScript(activity, id, sdcard+"/version");
                    setDevString(activity, "versionName", TERMVIM_VERSION);
                    File fontPath = new File(TermPreferences.FONTPATH);
                    if (!fontPath.exists()) fontPath.mkdirs();
                } finally {
                    if (!activity.isFinishing() && pd != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pd.dismiss();
                                doInstallVim = false;
                                if (whenDone != null) whenDone.run();
                            }
                        });
                        activity.setRequestedOrientation(orientation);
                    }
                }
            }
        }.start();
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

    static private String setDevString(Context context, String key, String value) {
        SharedPreferences pref = context.getSharedPreferences("dev", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(key, value);
        editor.apply();
        return value;
    }

    static private InputStream getInputStream(final Activity activity, int id) {
        InputStream is = null;
        try {
            is = activity.getResources().openRawResource(id);
        } catch(Exception e) {
        }
        return is;
    }

    static public void deleteFileOrFolder(File fileOrDirectory) {
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

    static String INSTALL_ZIP = "";
    static private void setMessage(final Activity activity, final ProgressDialog pd, final String message) {
        if (!activity.isFinishing() && pd != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.getString(R.string.update_vim);
                    pd.setMessage(INSTALL_ZIP + "\n- " + message);
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
        String cpu = System.getProperty("os.arch").toLowerCase();
        String arch = null;
        if (cpu.contains("arm")) {
            arch = "arm";
        } else if (cpu.contains("x86") || cpu.contains("i686")) {
            arch = "x86";
        }
        return arch;
    }
}

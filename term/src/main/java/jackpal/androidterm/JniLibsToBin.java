package jackpal.androidterm;

import android.os.Build;
import android.system.Os;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static jackpal.androidterm.TermVimInstaller.shell;

public class JniLibsToBin {
    static final public Map<String, String> JNIlIBS_MAP = new LinkedHashMap<String, String>() {
        {
            put("libctags.so",       "/bin/ctags");
            put("libvim.default.so", "/bin/vim.default");
            put("libxxd.so",         "/bin/xxd");
            put("libdiff.so",        "/usr/bin/diff");
            put("libgrep.so",        "/usr/bin/grep");
            put("libgrep32.so",      "/usr/bin/grep");
            put("libam.so"                   , "/bin/am");
            put("libatemod.so"               , "/bin/atemod");
            put("libbash.app.so"             , "/bin/bash.app");
            put("libgetprop.so"              , "/bin/getprop");
            put("libopen.so"                 , "/bin/open");
            put("libsetup-storage.so"        , "/bin/setup-storage");
            put("libsh.app.so"               , "/bin/sh.app");
            put("libxdg-open.so"             , "/bin/xdg-open");
            put("libusr.bin.busybox-setup.so", "/usr/bin/busybox-setup");
            put("libusr.bin.cphome.so"       , "/usr/bin/cphome");
            put("libvim.app.default.so"      , "/bin/vim.app.default");
            put("libvim.app.so"              , "/bin/vim.app");
            put("libproot.sh.so"             , "/bin/proot.sh");
            put("libsuvim.so"                , "/bin/suvim");
        }
    };

    static public boolean jniLibsToBin(String targetDir, InputStream is) {
        Map<String, String> maps = readSymlinks(is);
        if (maps != null && maps.size() > 0) jniLibsToBin(targetDir, maps);
        return true;
    }

    static public Map<String, String> readSymlinks(InputStream inputStream) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("UTF-8")))) {
            Map<String, String> result = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    List<String> item = Arrays.asList(line.split("\\|"));
                    if (item.size() != 2) continue;
                    String lib = item.get(0);
                    String file = item.get(1);
                    result.put(lib, file);
                } catch (Exception e) {
                    // do nothing
                }
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    static public boolean jniLibsToBin(String targetDir, String lib, String symlinkName) {
        Map<String, String> maps = new LinkedHashMap<String, String>() {
            {
                put(lib, symlinkName);
            }
        };
        jniLibsToBin(targetDir, maps);
        File symlink = new File(targetDir + symlinkName);
        if (symlink.exists() && ASFUtils.isSymlink(symlink)) return true;
        String SOLIB_PATH = TermService.getAPPLIB();
        File libFile = new File(SOLIB_PATH + "/" + lib);
        long size = 0;
        long libSize = 0;
        try {
            size = symlink.length();
            libSize = libFile.length();
        } catch (Exception symlinkError) {
            size = -1;
        }
        if (symlink.exists() && size == libSize) return true;
        return false;
    }

    static public void jniLibsToBin(String targetDir, Map <String, String> maps) {
        String SOLIB_PATH = TermService.getAPPLIB();
        for (Map.Entry<String, String> entry : maps.entrySet()) {
            if (entry.getKey().contains("grep32") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) continue;
            try {
                File soFile = new File(SOLIB_PATH + "/" + entry.getKey());
                if (!soFile.exists()) continue;
                File symlink = new File(targetDir + "/" + entry.getValue());
                File parent = new File(symlink.getParent());
                if (!parent.isDirectory()) {
                    parent.mkdirs();
                }
                shell("rm " + symlink.getAbsolutePath());
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !SOLIB_PATH.startsWith("/data") || !symlink(soFile, symlink)) {
                    InputStream is = new FileInputStream(soFile);
                    TermVimInstaller.cpStream(is , new FileOutputStream(symlink.getAbsolutePath()));
                }
                if ((symlink.getAbsolutePath().contains("/bin/")) ||
                    (symlink.getAbsolutePath().contains("/lib/")) ||
                    (symlink.getAbsolutePath().contains("/libexec/"))) {
                        shell("chmod 755 " + symlink.getAbsolutePath());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static private boolean symlink(File src, File symlink) {
        try {
            if (!src.exists() || !src.canExecute()) return false;
            shell("rm " + symlink.getAbsolutePath());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Os.symlink(src.getAbsolutePath(), symlink.getAbsolutePath());
            } else {
                shell("ln -s " + src.getAbsolutePath() + " " + symlink.getAbsolutePath());
            }
            if (!symlink.exists() || !ASFUtils.isSymlink(symlink)) return false;
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    static public void symlinkDebugReport(String targetDir) {
        try {
            String arch = TermVimInstaller.getArch();
            Map<String, String> maps = new LinkedHashMap<String, String>() {
                {
                    put("lib" + arch + ".so", "/usr/bin/"+arch);
                }
            };
            File symlink = new File(targetDir + "/usr/bin/" + arch);
            shell("rm " + symlink.getAbsolutePath());
            jniLibsToBin(targetDir, maps);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

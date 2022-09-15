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
            put("libctags.so"                , "/bin/ctags");
            put("libvim.default.so"          , "/bin/vim.default");
            put("libxxd.so"                  , "/bin/xxd");
            put("libdiff.so"                 , "/bin/diff");
            put("libgrep.so"                 , "/bin/grep");
            put("libgrep32.so"               , "/bin/grep");
            put("libam.so"                   , "/bin/am");
            put("libam.apk.so"               , "/bin/am.apk");
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
            put("libproot.so"                , "/bin/proot");
            put("libloader.so"               , "/bin/loader");
            put("libloader-m32.so"           , "/bin/loader-m32");
            put("libproot.bash.so"           , "/bin/proot.bash");
            put("libproot.sh.so"             , "/bin/proot.sh");
            put("libsuvim.so"                , "/bin/suvim");
        }
    };

    static public boolean jniLibsToBin(String targetDir, String[] lines) {
        Map<String, String> maps = readSymlinks(lines);
        if (maps != null && maps.size() > 0) jniLibsToBin(targetDir, maps);
        return true;
    }

    static public Map<String, String> readSymlinks(String[] lines) {
        try {
            Map<String, String> result = new HashMap<>();
            for (String line : lines) {
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
                symlink(soFile, symlink);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static public final boolean JNILIBS_FORCE_SYMLINK = true;
    static private boolean symlink(File src, File symlink) {
        try {
            if (!src.exists()) return false;
            shell("rm " + symlink.getAbsolutePath());
            boolean useSymlink = true;
            if (!TermService.getAPPFILES().matches("/data/.*")) useSymlink = JNILIBS_FORCE_SYMLINK;
            if (useSymlink) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Os.symlink(src.getAbsolutePath(), symlink.getAbsolutePath());
                } else {
                    shell("ln -s " + src.getAbsolutePath() + " " + symlink.getAbsolutePath());
                }
            }
            boolean executable = setExecMode(symlink);
            if (!symlink.exists() || !ASFUtils.isSymlink(symlink) || (executable && !symlink.canExecute())) {
                shell("rm " + symlink.getAbsolutePath());
                InputStream is = new FileInputStream(src);
                TermVimInstaller.cpStream(is , new FileOutputStream(symlink.getAbsolutePath()));
                setExecMode(symlink);
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    static private boolean setExecMode(File symlink) {
        if ((symlink.getAbsolutePath().contains("/bin/")) ||
                (symlink.getAbsolutePath().contains("/lib/")) ||
                (symlink.getAbsolutePath().contains("/libexec/"))) {
            shell("chmod 755 " + symlink.getAbsolutePath());
            return true;
        }
        return false;
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

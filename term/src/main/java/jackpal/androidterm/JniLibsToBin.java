package jackpal.androidterm;

import android.os.Build;
import android.system.Os;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
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
        }
    };

    static public void jniLibsToBin(String targetDir, Map <String, String> maps) {
        String SOLIB_PATH = TermService.getAPPLIB();
        for (Map.Entry<String, String> entry : maps.entrySet()) {
            if (entry.getKey().contains("grep32") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) continue;
            try {
                File soFile = new File(SOLIB_PATH + "/" + entry.getKey());
                File symlink = new File(targetDir + "/" + entry.getValue());
                File parent = new File(symlink.getParent());
                if (!parent.isDirectory()) {
                    parent.mkdirs();
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !SOLIB_PATH.startsWith("/data") || !symlink(soFile, symlink)) {
                    InputStream is = new FileInputStream(soFile);
                    TermVimInstaller.cpStream(is , new FileOutputStream(symlink.getAbsolutePath()));
                }
                if ((symlink.getAbsolutePath().contains("/bin/")) || (symlink.getAbsolutePath().contains("/lib/"))) {
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
            if (!symlink.exists()) return false;
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}


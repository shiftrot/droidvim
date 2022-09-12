package jackpal.androidterm;

import android.os.Build;

public class StaticConfig {
    public static final boolean FLAVOR_TERMINAL = BuildConfig.FLAVOR.equals("terminal");
    public static final boolean FLAVOR_DROIDVIM = BuildConfig.FLAVOR.equals("droidvim");
    public static final boolean FLAVOR_VIM = BuildConfig.FLAVOR.matches(".*vim.*");
    public static boolean SCOPED_STORAGE = false;
}

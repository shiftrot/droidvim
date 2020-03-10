package jackpal.androidterm;

import android.os.Build;

public class StaticConfig {
    public static final boolean FLAVOR_VIM = BuildConfig.FLAVOR.matches(".*vim.*");
    public static boolean SCOPED_STORAGE = (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q);
}

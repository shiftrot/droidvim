package jackpal.androidterm.emulatorview.compat;

import android.content.Context;

public class ClipboardManagerCompatFactory {

    private ClipboardManagerCompatFactory() {
        /* singleton */
    }

    public static ClipboardManagerCompat getManager(Context context) {
        return new ClipboardManagerCompatV11(context);
    }
}

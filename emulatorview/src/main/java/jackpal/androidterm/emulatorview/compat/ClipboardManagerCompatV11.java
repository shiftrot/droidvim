package jackpal.androidterm.emulatorview.compat;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.ClipboardManager;

@SuppressLint("NewApi")
public class ClipboardManagerCompatV11 implements ClipboardManagerCompat {
    private final ClipboardManager clip;

    public ClipboardManagerCompatV11(Context context) {
        clip = (ClipboardManager) context.getApplicationContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public CharSequence getText() {
        if (!hasText()) return null;
        ClipData.Item item = clip.getPrimaryClip().getItemAt(0);
        return item.getText();
    }

    @Override
    public boolean hasText() {
        ClipData data = clip.getPrimaryClip();
        if (data == null) return false;
        ClipData.Item item = data.getItemAt(0);
        return (item != null && item.getText() != null && (item.getText().toString().length() > 0));
    }

    @Override
    public void setText(CharSequence text) {
        ClipData clipData = ClipData.newPlainText("simple text", text);
        clip.setPrimaryClip(clipData);
    }
}

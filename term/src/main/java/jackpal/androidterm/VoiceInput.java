package jackpal.androidterm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.speech.RecognizerIntent;

import java.util.Locale;

public class VoiceInput {
    public static void start(Activity activity, int REQUEST_CODE) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, getLocaleId());
        if (!Term.isConnected(activity.getApplicationContext())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            }
        }
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 100);
        try {
            activity.startActivityForResult(intent, REQUEST_CODE);
        } catch (Exception e) {
            try {
                AlertDialog.Builder bld = new AlertDialog.Builder(activity);
                bld.setMessage("No voice input activity was found.");
                bld.setPositiveButton(android.R.string.ok, null);
                bld.create().show();
            } catch (Exception voice) {
                // Do nothing
            }
        }
    }

    public static String getLocaleId() {
        String id = RecognizerIntent.LANGUAGE_MODEL_FREE_FORM;
        Locale locale = Locale.getDefault();
        id = locale.toString();
        String language = locale.getLanguage();
        String country = locale.getCountry();
        return id;
    }
}

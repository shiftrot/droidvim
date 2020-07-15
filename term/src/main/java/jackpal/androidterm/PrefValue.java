package jackpal.androidterm;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

class PrefValue {
    private final SharedPreferences mPref;
    private final SharedPreferences.Editor mEditor;

    @SuppressLint("CommitPrefEdits")
    PrefValue(Context context, String database) {
        mPref = context.getSharedPreferences(database, Context.MODE_PRIVATE);
        mEditor = mPref.edit();
    }

    final private static String DATABASE = "dev";

    @SuppressLint("CommitPrefEdits")
    PrefValue(Context context) {
        mPref = context.getSharedPreferences(DATABASE, Context.MODE_PRIVATE);
        mEditor = mPref.edit();
    }

    public void setInt(String key, int value) {
        mEditor.putInt(key, value);
        mEditor.apply();
    }

    public int getInt(String key, int defValue) {
        int res;
        try {
            res = mPref.getInt(key, defValue);
        } catch (Exception e) {
            res = defValue;
        }
        return res;
    }

    public void setString(String key, String value) {
        mEditor.putString(key, value);
        mEditor.apply();
    }

    public String getString(String key, String defValue) {
        String res;
        try {
            res = mPref.getString(key, defValue);
        } catch (Exception e) {
            res = defValue;
        }
        return res;
    }

    public void setBoolean(String key, boolean value) {
        mEditor.putBoolean(key, value);
        mEditor.apply();
    }

    public Boolean getBoolean(String key, boolean defValue) {
        boolean res;
        try {
            res = mPref.getBoolean(key, defValue);
        } catch (Exception e) {
            res = defValue;
        }
        return res;
    }
}



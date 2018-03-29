/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jackpal.androidterm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import jackpal.androidterm.compat.AndroidCompat;
import jackpal.androidterm.util.TermSettings;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;

import com.droidvim.XmlUtils;

import static jackpal.androidterm.Term.getPath;

public class TermPreferences extends PreferenceActivity {
    private static final String STATUSBAR_KEY = "statusbar";
    private static final String ACTIONBAR_KEY = "actionbar";
    private static final String CATEGORY_SCREEN_KEY = "screen";
    static final String FONTPATH = Environment.getExternalStorageDirectory().getPath()+"/fonts";
    private static final String CATEGORY_TEXT_KEY = "text";

    private final static boolean FLAVOR_VIM = TermVimInstaller.FLAVOR_VIM;
    private boolean mFirst = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        final TermSettings settings = new TermSettings(getResources(), mPrefs);
        if (mFirst && settings.getColorTheme() == 0) setTheme(R.style.Theme_AppCompat);
        mFirst = false;
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
        if (FLAVOR_VIM) {
            addPreferencesFromResource(R.xml.preferences_apps);

            Resources res = getResources();
            String[] array = res.getStringArray(R.array.entries_app_filepicker_preference);

            OnPreferenceChangeListener listener = new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Resources res = getResources();
                    String[] array = res.getStringArray(R.array.entries_app_filepicker_preference);
                    int value = Integer.valueOf((String) newValue);
                    String summary = array[value];
                    preference.setSummary(summary);
                    return true;
                }
            };

            String id = "cloud_dropbox_filepicker";
            Preference filePicker = findPreference(id);
            String summary = array[settings.getDropboxFilePicker()];
            filePicker.setSummary(summary);
            filePicker.setOnPreferenceChangeListener(listener);

            id = "cloud_googledrive_filepicker";
            filePicker = findPreference(id);
            summary = array[settings.getGoogleDriveFilePicker()];
            filePicker.setSummary(summary);
            filePicker.setOnPreferenceChangeListener(listener);

            id = "cloud_onedrive_filepicker";
            filePicker = findPreference(id);
            summary = array[settings.getOneDriveFilePicker()];
            filePicker.setSummary(summary);
            filePicker.setOnPreferenceChangeListener(listener);
        }

        if (AndroidCompat.SDK >= 19) {
            addPreferencesFromResource(R.xml.preferences_prefs);
            final String PREFS_KEY = "prefs_rw";
            Preference prefsPicker = getPreferenceScreen().findPreference(PREFS_KEY);
            prefsPicker.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    prefsPicker();
                    return true;
                }
            });

            final String LICENSE_KEY = "license";
            Preference licensePrefs = getPreferenceScreen().findPreference(LICENSE_KEY);
            licensePrefs.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    licensePrefs();
                    return true;
                }
            });
        }

        // Remove the action bar pref on older platforms without an action bar
        Preference actionBarPref = findPreference(ACTIONBAR_KEY);
        PreferenceCategory screenCategory =
               (PreferenceCategory) findPreference(CATEGORY_SCREEN_KEY);
        if ((actionBarPref != null) && (screenCategory != null)) {
            screenCategory.removePreference(actionBarPref);
        }

        Preference statusBarPref = findPreference(STATUSBAR_KEY);
        if ((statusBarPref != null) && (screenCategory != null)) {
            screenCategory.removePreference(statusBarPref);
        }

        PreferenceCategory keyboardCategory =
                (PreferenceCategory) findPreference("categoryKeyboard");
        Preference controlKeyPref = findPreference("controlkey");
        if ((controlKeyPref != null) && (keyboardCategory != null)) {
            keyboardCategory.removePreference(controlKeyPref);
        }
        Preference fnKeyPref = findPreference("fnkey");
        if ((fnKeyPref != null) && (keyboardCategory != null)) {
            keyboardCategory.removePreference(fnKeyPref);
        }

        if (FLAVOR_VIM) {
            findPreference("functionbar_vim_paste").setDefaultValue(true);
        }

        // FIXME:
        if (AndroidCompat.SDK < 19 && !new File(FONTPATH).exists()) new File(FONTPATH).mkdirs();
        Preference fontPicker = getPreferenceScreen().findPreference("fontfile_picker");
        fontPicker.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                filePicker();
                return true;
            }
        });

        ListPreference fontFileList= (ListPreference) getPreferenceScreen().findPreference("fontfile");
        setFontList(fontFileList);

        Preference fontSelect = findPreference("fontfile");
        Resources res = getResources();
        fontSelect.setSummary(res.getString(R.string.summary_fontfile_preference)+String.format(" (%s)", FONTPATH));
        fontSelect.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ListPreference fontFileList = (ListPreference) preference;
                setFontList(fontFileList);
                return true;
            }
        });

        fontSelect.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                ListPreference fontFileList = (ListPreference) preference;
                setFontList(fontFileList);
                fontFileList.setDefaultValue(newValue);
                return true;
            }
        });

        PreferenceCategory textCategory = (PreferenceCategory) findPreference(CATEGORY_TEXT_KEY);
        Preference fontPref;
        if (AndroidCompat.SDK >= 19) {
            fontPref = findPreference("fontfile");
        } else {
            fontPref = findPreference("fontfile_picker");
        }
        if ((fontPref != null) && (textCategory != null)) {
            textCategory.removePreference(fontPref);
        }

    }

    void licensePrefs() {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_info);
        bld.setMessage(this.getString(R.string.license_text));
        bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        bld.setNeutralButton(this.getString(R.string.github), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                Intent openUrl = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.github_url)));
                    startActivity(openUrl);
            }
        });
        bld.create().show();
    }

    public static final int REQUEST_FONT_PICKER          = 16;
    public static final int REQUEST_PREFS_READ_PICKER    = REQUEST_FONT_PICKER + 1;
    public static final int REQUEST_STORAGE_FONT_PICKER  = REQUEST_FONT_PICKER + 2;
    public static final int REQUEST_STORAGE_PREFS_PICKER = REQUEST_FONT_PICKER + 3;
    @SuppressLint("NewApi")
    void prefsPicker() {
        if (AndroidCompat.SDK >= 23 && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PREFS_PICKER);
        } else {
            doPrefsPicker();
        }
    }

    @SuppressLint("NewApi")
    private void doPrefsPicker() {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_info);
        bld.setMessage(this.getString(R.string.prefs_dialog_rw));
        bld.setNeutralButton(this.getString(R.string.prefs_write), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                confirmWritePrefs();
            }
        });
        bld.setPositiveButton(this.getString(R.string.prefs_read), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/xml");
                startActivityForResult(intent, REQUEST_PREFS_READ_PICKER);
            }
        });
        bld.setNegativeButton(this.getString(android.R.string.no), null);
        bld.create().show();
    }

    private void confirmWritePrefs() {
        File pathExternalPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String downloadDir = pathExternalPublicDir.getPath();
        final String filename = downloadDir + "/"+ BuildConfig.APPLICATION_ID + ".xml";
        if (new File(filename).exists()) {
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            bld.setIcon(android.R.drawable.ic_dialog_info);
            bld.setMessage(this.getString(R.string.prefs_write_confirm));
            bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    writePrefs(filename);
                }
            });
            bld.setNegativeButton(this.getString(android.R.string.no), null);
            bld.create().show();
        } else {
            writePrefs(filename);
            return;
        }
    }

    private boolean writePrefs(String filename) {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_info);
        bld.setPositiveButton(this.getString(android.R.string.ok), null);
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(filename);
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
            XmlUtils.writeMapXml(pref.getAll(), fos);
        } catch (Exception e) {
            bld.setMessage(this.getString(R.string.prefs_write_info_failure));
            bld.create().show();
            return false;
        }
        bld.setMessage(this.getString(R.string.prefs_write_info_success)+"\n\n"+filename);
        bld.create().show();
        return true;
    }

    private boolean readPrefs(Uri uri, boolean clearPrefs) {
        try {
            InputStream is = this.getApplicationContext().getContentResolver().openInputStream(uri);
            SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(this).edit();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Map<String, ?> entries = XmlUtils.readMapXml(is);

            int error = 0;
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                if (!prefs.contains(entry.getKey())) error += 1;
                if (error > 3) throw new Exception();
            }
            if (clearPrefs && error == 0) prefEdit.clear();

            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                putObject(prefEdit, entry.getKey(), entry.getValue());
            }
            prefEdit.apply();
        } catch (Exception e) {
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            bld.setIcon(android.R.drawable.ic_dialog_alert);
            bld.setTitle(this.getString(R.string.prefs_read_error_title));
            bld.setMessage(this.getString(R.string.prefs_read_error));
            bld.setPositiveButton(this.getString(android.R.string.ok), null);
            bld.create().show();
            return false;
        }
        return true;
    }

    private SharedPreferences.Editor putObject(final SharedPreferences.Editor edit, final String key, final Object val) {
        if (val instanceof Boolean)
            return edit.putBoolean(key, (Boolean) val);
        else if (val instanceof Float)
            return edit.putFloat(key, (Float) val);
        else if (val instanceof Integer)
            return edit.putInt(key, (Integer) val);
        else if (val instanceof Long)
            return edit.putLong(key, (Long) val);
        else if (val instanceof String) {
            String loadVal = (String)val;
            if (key.equals("lib_sh_path")) {
                if (!new File(loadVal).canRead()) loadVal = new File(getApplicationContext().getApplicationInfo().nativeLibraryDir) + "/libsh.so";
                return edit.putString(key, loadVal);
            }
            if (key.equals("shell_path")) {
                loadVal = loadVal.replaceFirst(" .*$", "");
                if (!loadVal.equals("") && !new File(loadVal).canRead()) loadVal = AndroidCompat.SDK >= 24 ? "" : "/system/bin/sh -";
                return edit.putString(key, loadVal);
            }
            if (key.equals("home_path")) {
                if (!new File(loadVal).canWrite()) {
                    String defValue;
                    if (!BuildConfig.FLAVOR.equals("master")) {
                        defValue = TermService.getAPPFILES() + "/home";
                        File home = new File(defValue);
                        if (!home.exists()) home.mkdir();
                    } else {
                        defValue = getDir("HOME", MODE_PRIVATE).getAbsolutePath();
                    }
                    loadVal = defValue;
                }
                return edit.putString(key, loadVal);
            }
            return edit.putString(key, ((String)val));
        }
        return edit;
    }

    @SuppressLint("NewApi")
    void filePicker() {
        if (AndroidCompat.SDK >= 23 && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_FONT_PICKER);
        } else {
            doFilePicker();
        }
    }

    @Override
    @SuppressLint("NewApi")
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_STORAGE_FONT_PICKER:
            case REQUEST_STORAGE_PREFS_PICKER:
                for (int i = 0; i < permissions.length; i++) {
                    if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            switch (requestCode) {
                                case REQUEST_STORAGE_FONT_PICKER:
                                    doFilePicker();
                                    break;
                                case REQUEST_STORAGE_PREFS_PICKER:
                                    doPrefsPicker();
                                    break;
                                default:
                                    break;
                            }
                        } else {
                            AlertDialog.Builder bld = new AlertDialog.Builder(this);
                            bld.setIcon(android.R.drawable.ic_dialog_alert);
                            bld.setMessage(this.getString(R.string.storage_permission_error));
                            bld.setPositiveButton(this.getString(android.R.string.ok), null);
                            bld.create().show();
                        }
                    }
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }

    static final String FONT_FILENAME = "font_filename";
    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        switch (request) {
            case REQUEST_PREFS_READ_PICKER:
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    if (readPrefs(uri, false)) onCreate(null);
                    break;
                }
                break;
            case REQUEST_FONT_PICKER:
                String path;
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    path = getPath(this, uri);
                    if (path != null && path.matches(".*\\.(?i)(ttf|ttc|otf)")) {
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                        sp.edit().putString(FONT_FILENAME, path).apply();
                    } else {
                        AlertDialog.Builder bld = new AlertDialog.Builder(this);
                        bld.setIcon(android.R.drawable.ic_dialog_alert);
                        bld.setMessage(this.getString(R.string.font_file_error));
                        bld.setPositiveButton(this.getString(android.R.string.ok), null);
                        bld.create().show();
                        break;
                    }
                }
                break;
            default:
                break;
        }
    }

    private void doFilePicker() {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_info);
        bld.setMessage(this.getString(R.string.font_file_error));
        bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/octet-stream");
                startActivityForResult(intent, REQUEST_FONT_PICKER);
            }
        });
        bld.setNegativeButton(this.getString(android.R.string.no), null);
        final Activity activity = this;
        bld.setNeutralButton(this.getString(R.string.entry_fontfile_default), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
                sp.edit().putString(FONT_FILENAME, activity.getString(R.string.entry_fontfile_default)).apply();
            }
        });
        bld.create().show();
    }

    private ListPreference setFontList(ListPreference fontFileList) {
        File files[] = new File(FONTPATH).listFiles();
        ArrayList<File> fonts = new ArrayList<File>();

        if (files != null) {
            for (File file : files) {
                if (file.isFile() == true && file.getName().matches(".*\\.(?i)(ttf|ttc|otf)") && file.isHidden() == false) {
                    fonts.add(file);
                }
            }
        }
        Collections.sort(fonts);
        int i = fonts.size() + 1;
        CharSequence[] items = new CharSequence[i];
        CharSequence[] values = new CharSequence[i];

        i = 0;
        Resources res = getResources();
        String systemFontName = res.getString(R.string.entry_fontfile_default);
        items[i] = systemFontName;
        values[i] = systemFontName;
        i++;

        Iterator<File> itr = fonts.iterator();
        while (itr.hasNext()) {
            File file = itr.next();
            items[i] = file.getName();
            values[i] = file.getName();
            i++;
        }

        fontFileList.setEntries(items);
        fontFileList.setEntryValues(values);
        return fontFileList;
    }
}

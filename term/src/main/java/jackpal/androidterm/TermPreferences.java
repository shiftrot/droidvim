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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import jackpal.androidterm.compat.ActionBarCompat;
import jackpal.androidterm.compat.ActivityCompat;
import jackpal.androidterm.compat.AndroidCompat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.MenuItem;

import static jackpal.androidterm.Term.getPath;

public class TermPreferences extends PreferenceActivity {
    private static final String STATUSBAR_KEY = "statusbar";
    private static final String ACTIONBAR_KEY = "actionbar";
    private static final String CATEGORY_SCREEN_KEY = "screen";
    private static final String CATEGORY_CLOUD_STORAGE_KEY = "categoryCloudStorage";
    static final String FONTPATH = Environment.getExternalStorageDirectory().getPath()+"/fonts";
    private static final String CATEGORY_TEXT_KEY = "text";

    private final static boolean FLAVOR_VIM = TermVimInstaller.FLAVOR_VIM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

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

        // Display up indicator on action bar home button
        if (AndroidCompat.V11ToV20) {
            ActionBarCompat bar = ActivityCompat.getActionBar(this);
            if (bar != null) {
                bar.setDisplayOptions(ActionBarCompat.DISPLAY_HOME_AS_UP, ActionBarCompat.DISPLAY_HOME_AS_UP);
            }
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

    static final String FONT_FILENAME = "font_filename";
    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        switch (request) {
            case REQUEST_FONT_PICKER:
                String path;
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    path = getPath(this, uri);
                    if (path != null && path.matches(".*\\.(ttf|otf)")) {
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

    public static final int REQUEST_FONT_PICKER = 16;
    private void filePicker() {
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case ActionBarCompat.ID_HOME:
            // Action bar home button selected
            finish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
}

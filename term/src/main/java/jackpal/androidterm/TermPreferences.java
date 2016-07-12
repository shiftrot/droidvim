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
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.view.MenuItem;

public class TermPreferences extends PreferenceActivity {
    private static final String STATUSBAR_KEY = "statusbar";
    private static final String ACTIONBAR_KEY = "actionbar";
    private static final String CATEGORY_SCREEN_KEY = "screen";
    private static final String FONTPATH = Environment.getExternalStorageDirectory().getPath()+"/fonts";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // Remove the action bar pref on older platforms without an action bar
        if (AndroidCompat.SDK < 11) {
            Preference actionBarPref = findPreference(ACTIONBAR_KEY);
             PreferenceCategory screenCategory =
                    (PreferenceCategory) findPreference(CATEGORY_SCREEN_KEY);
             if ((actionBarPref != null) && (screenCategory != null)) {
                 screenCategory.removePreference(actionBarPref);
             }
        }

        Preference statusBarPref = findPreference(STATUSBAR_KEY);
        PreferenceCategory screenCategory =
                (PreferenceCategory) findPreference(CATEGORY_SCREEN_KEY);
        if ((statusBarPref != null) && (screenCategory != null)) {
            screenCategory.removePreference(statusBarPref);
        }

        // Display up indicator on action bar home button
        if (AndroidCompat.V11ToV20) {
            ActionBarCompat bar = ActivityCompat.getActionBar(this);
            if (bar != null) {
                bar.setDisplayOptions(ActionBarCompat.DISPLAY_HOME_AS_UP, ActionBarCompat.DISPLAY_HOME_AS_UP);
            }
        }

        // FIXME:
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

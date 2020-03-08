package jackpal.androidterm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.EditText;

import com.obsez.android.lib.filechooser.ChooserDialog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import jackpal.androidterm.compat.AndroidCompat;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompat;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompatFactory;
import jackpal.androidterm.util.TermSettings;

import static jackpal.androidterm.TermVimInstaller.shell;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 *
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class TermPreferences extends AppCompatPreferenceActivity {
    public static TermPreferences mTermPreference = null;

    public static final String FONT_FILENAME = "font_filename";
    public static final String FONT_PATH = Environment.getExternalStorageDirectory().getPath() + "/fonts";

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    private final static boolean FLAVOR_VIM = TermVimInstaller.FLAVOR_VIM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setAppPickerList(this);
        setupTheme();
        setupActionBar();
        mTermPreference = this;

        super.onCreate(savedInstanceState);
    }

    private static String[] mLabels = null;
    private static String[] mPackageNames = null;

    public static void setAppPickerList(Activity activity) {
        try {
            final PackageManager pm = activity.getApplicationContext().getPackageManager();
            new Thread() {
                @Override
                public void run() {
                    final List<ApplicationInfo> installedAppList = pm.getInstalledApplications(0);
                    final TreeMap<String, String> items = new TreeMap<>();
                    for (ApplicationInfo app : installedAppList) {
                        Intent intent = pm.getLaunchIntentForPackage(app.packageName);
                        if (intent != null)
                            items.put(app.loadLabel(pm).toString(), app.packageName);
                    }
                    List<String> list = new ArrayList<>(items.keySet());
                    mLabels = list.toArray(new String[0]);
                    list = new ArrayList<>(items.values());
                    mPackageNames = list.toArray(new String[0]);
                }
            }.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupTheme() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final TermSettings settings = new TermSettings(getResources(), prefs);
        if (settings.getColorTheme() % 2 == 0) {
            setTheme(R.style.App_Preference_Theme_Dark);
        } else {
            setTheme(R.style.App_Preference_Theme_Light);
        }
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        if (FLAVOR_VIM && AndroidCompat.SDK >= Build.VERSION_CODES.KITKAT) {
            loadHeadersFromResource(R.xml.pref_headers_vim, target);
        } else {
            loadHeadersFromResource(R.xml.pref_headers, target);
        }
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || ImePreferenceFragment.class.getName().equals(fragmentName)
                || FunctionbarPreferenceFragment.class.getName().equals(fragmentName)
                || GesturePreferenceFragment.class.getName().equals(fragmentName)
                || ScreenPreferenceFragment.class.getName().equals(fragmentName)
                || FontPreferenceFragment.class.getName().equals(fragmentName)
                || KeyboardPreferenceFragment.class.getName().equals(fragmentName)
                || AppsPreferenceFragment.class.getName().equals(fragmentName)
                || ShellPreferenceFragment.class.getName().equals(fragmentName)
                || PrefsPreferenceFragment.class.getName().equals(fragmentName);
    }

    private void documentTreePicker(int requestCode) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            doStartActivityForResult(intent, requestCode);
        }
    }

    private void homeDirectoryPicker(String mes) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final SharedPreferences.Editor editor = prefs.edit();
        final Activity activity = this;

        directoryPicker(mes, new ChooserDialog.Result() {
            @Override
            public void onChoosePath(String path, File pathFile) {
                AlertDialog.Builder bld = new AlertDialog.Builder(activity);
                if (path == null) {
                    path = getDefaultHome();
                    pathFile = new File(path);
                    if (!pathFile.exists()) pathFile.mkdir();
                    editor.putString("home_path", path);
                    editor.apply();
                    bld.setIcon(android.R.drawable.ic_dialog_info);
                    bld.setMessage(activity.getString(R.string.set_home_directory) + " " + path);
                } else if (new File(path).canWrite()) {
                    editor.putString("home_path", path);
                    editor.apply();
                    bld.setIcon(android.R.drawable.ic_dialog_info);
                    bld.setMessage(activity.getString(R.string.set_home_directory) + " " + path);
                } else {
                    bld.setIcon(android.R.drawable.ic_dialog_alert);
                    bld.setMessage(activity.getString(R.string.invalid_directory));
                }
                bld.setPositiveButton(activity.getString(android.R.string.ok), null);
                bld.create().show();
            }
        });
    }

    private String getDefaultHome() {
        String defValue;
        if (!BuildConfig.FLAVOR.equals("origin")) {
            defValue = getFilesDir().getAbsolutePath() + "/home";
        } else {
            defValue = getDir("HOME", MODE_PRIVATE).getAbsolutePath();
        }
        return defValue;
    }

    private void startupDirectoryPicker(String mes) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final SharedPreferences.Editor editor = prefs.edit();
        final Activity activity = this;

        directoryPicker(mes, new ChooserDialog.Result() {
            @Override
            public void onChoosePath(String path, File pathFile) {
                AlertDialog.Builder bld = new AlertDialog.Builder(activity);
                if (path == null) {
                    path = getDefaultHome();
                    pathFile = new File(path);
                    if (!pathFile.exists()) pathFile.mkdir();
                    editor.putString("startup_path", path);
                    editor.apply();
                    bld.setIcon(android.R.drawable.ic_dialog_info);
                    bld.setMessage(activity.getString(R.string.set_startup_directory) + " " + path);
                } else if (new File(path).canWrite()) {
                    editor.putString("startup_path", path);
                    editor.apply();
                    bld.setIcon(android.R.drawable.ic_dialog_info);
                    bld.setMessage(activity.getString(R.string.set_startup_directory) + " " + path);
                } else {
                    bld.setIcon(android.R.drawable.ic_dialog_alert);
                    bld.setMessage(activity.getString(R.string.invalid_directory));
                }
                bld.setPositiveButton(activity.getString(android.R.string.ok), null);
                bld.create().show();
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void directoryPicker(String mes, final ChooserDialog.Result r) {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_info);
        bld.setTitle(getString(R.string.select_directory_message));
        bld.setMessage(mes);
        bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                if (USE_DOCUMENT_TREE_PICKER) {
                    documentTreePicker(REQUEST_HOME_DIRECTORY);
                } else {
                    internalStoragePicker(r);
                }
            }
        });
        bld.setNegativeButton(this.getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        bld.setNeutralButton(this.getString(R.string.reset_directory), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                r.onChoosePath(null, null);
            }
        });
        bld.create().show();
    }

    private void internalStoragePicker(ChooserDialog.Result r) {
        new ChooserDialog().with(this)
                .withResources(R.string.select_directory_message, R.string.select_directory, android.R.string.cancel)
                .enableOptions(true)
                .withFilter(true, true)
                .withStartFile(Environment.getExternalStorageDirectory().getAbsolutePath())
                .withChosenListener(r)
                .build()
                .show();
    }

    void applicationInfo() {
        Intent intent = new Intent();
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
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
                PackageManager pm = TermPreferences.this.getApplicationContext().getPackageManager();
                if (openUrl.resolveActivity(pm) != null) {
                    startActivity(openUrl);
                } else {
                    Intent intent = new Intent(TermPreferences.this.getApplicationContext(), WebViewActivity.class);
                    intent.putExtra("url", getString(R.string.github_url));
                    startActivity(intent);
                }
            }
        });
        bld.create().show();
    }

    @SuppressLint("NewApi")
    void prefsPicker() {
        doPrefsPicker();
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
                doStartActivityForResult(intent, REQUEST_PREFS_READ_PICKER);
            }
        });
        bld.setNegativeButton(this.getString(android.R.string.no), null);
        bld.create().show();
    }

    private void confirmWritePrefs() {
        @SuppressLint("SimpleDateFormat") String timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
        final String fileName = BuildConfig.APPLICATION_ID + "-" + timestamp + ".xml";
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/xml");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            if (checkImplicitIntent(this, intent, fileName))
                doStartActivityForResult(intent, REQUEST_WRITE_PREFS_PICKER);
        } else {
            File pathExternalPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String downloadDir = pathExternalPublicDir.getPath();
            if (!new File(downloadDir).isDirectory()) {
                downloadDir = Environment.getExternalStorageDirectory().getPath();
            }
            writePrefs(downloadDir + "/" + fileName);
        }
    }

    private boolean checkImplicitIntent(Context context, Intent intent, String fileName) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
        if (apps.size() < 1) {
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            bld.setIcon(android.R.drawable.ic_dialog_info);
            bld.setPositiveButton(this.getString(android.R.string.ok), null);
            bld.setMessage(this.getString(R.string.prefs_write_info_failure));
            bld.create().show();
            return false;
        }
        return true;
    }

    private boolean writePrefs(String fileName) {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_info);
        bld.setPositiveButton(this.getString(android.R.string.ok), null);
        FileOutputStream fos;
        try {
            File file = new File(fileName);
            fos = new FileOutputStream(file);
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
            XmlUtils.writeMapXml(pref.getAll(), fos);
            DownloadManager downloadManager = (DownloadManager) this.getSystemService(DOWNLOAD_SERVICE);
            downloadManager.addCompletedDownload(file.getName(), file.getName(), true, "text/xml", file.getAbsolutePath(), file.length(), true);
        } catch (Exception e) {
            bld.setMessage(this.getString(R.string.prefs_write_info_failure));
            bld.create().show();
            return false;
        }
        bld.setMessage(this.getString(R.string.prefs_write_info_success) + "\n\n" + fileName);
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
            String loadVal = (String) val;
            if (key.equals("home_path")) {
                if (!new File(loadVal).canWrite()) {
                    String defValue;
                    if (!BuildConfig.FLAVOR.equals("origin")) {
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
            return edit.putString(key, ((String) val));
        }
        return edit;
    }

    @SuppressLint("NewApi")
    private void fontFilePicker() {
        doFontFilePicker();
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void doFontFilePicker() {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_info);
        bld.setMessage(this.getString(R.string.font_file_error));
        bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                doStartActivityForResult(intent, REQUEST_FONT_PICKER);
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

    public static final boolean USE_DOCUMENT_TREE_PICKER = true && (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
    public static final int REQUEST_FONT_PICKER = 16;
    public static final int REQUEST_PREFS_READ_PICKER = REQUEST_FONT_PICKER + 1;
    public static final int REQUEST_STORAGE_FONT_PICKER = REQUEST_FONT_PICKER + 2;
    public static final int REQUEST_STORAGE_PREFS_PICKER = REQUEST_FONT_PICKER + 3;
    public static final int REQUEST_HOME_DIRECTORY = REQUEST_FONT_PICKER + 4;
    public static final int REQUEST_STARTUP_DIRECTORY = REQUEST_FONT_PICKER + 5;
    public static final int REQUEST_WRITE_PREFS_PICKER = REQUEST_FONT_PICKER + 6;

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
                                    doFontFilePicker();
                                    break;
                                case REQUEST_STORAGE_PREFS_PICKER:
                                    doPrefsPicker();
                                    break;
                                default:
                                    break;
                            }
                        } else {
                            if (requestCode == REQUEST_STORAGE_FONT_PICKER) {
                                doFontFilePicker();
                            } else {
                                AlertDialog.Builder bld = new AlertDialog.Builder(this);
                                bld.setIcon(android.R.drawable.ic_dialog_alert);
                                bld.setMessage(this.getString(R.string.storage_permission_error));
                                bld.setPositiveButton(this.getString(android.R.string.ok), null);
                                bld.create().show();
                            }
                        }
                    }
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        switch (request) {
            case REQUEST_STARTUP_DIRECTORY:
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    String path = getDirectory(this, uri);
                    if (path != null && new File(path).canWrite()) {
                        ClipboardManagerCompat clip = ClipboardManagerCompatFactory.getManager(getApplicationContext());
                        clip.setText(path);
                        AlertDialog.Builder bld = new AlertDialog.Builder(this);
                        bld.setIcon(android.R.drawable.ic_dialog_info);
                        bld.setTitle(this.getString(R.string.title_startup_chooser_preference));
                        bld.setMessage(this.getString(R.string.copy_startup_dir) + " " + path);
                        bld.setPositiveButton(this.getString(android.R.string.ok), null);
                        bld.create().show();
                    } else {
                        AlertDialog.Builder bld = new AlertDialog.Builder(this);
                        bld.setIcon(android.R.drawable.stat_notify_error);
                        bld.setMessage(this.getString(R.string.invalid_directory));
                        bld.setPositiveButton(this.getString(android.R.string.ok), null);
                        bld.create().show();
                    }
                }
                break;
            case REQUEST_HOME_DIRECTORY:
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    String path;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        path = getDirectory(this, uri);
                        final Activity activity = this;
                        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        final SharedPreferences.Editor editor = prefs.edit();
                        AlertDialog.Builder bld = new AlertDialog.Builder(this);
                        if (path == null) {
                            bld.setIcon(android.R.drawable.ic_dialog_alert);
                            bld.setMessage(activity.getString(R.string.invalid_directory));
                        } else if (new File(path).canWrite()) {
                            editor.putString("home_path", path);
                            editor.apply();
                            bld.setIcon(android.R.drawable.ic_dialog_info);
                            bld.setMessage(activity.getString(R.string.set_home_directory) + " " + path);
                        } else {
                            bld.setIcon(android.R.drawable.ic_dialog_alert);
                            bld.setMessage(activity.getString(R.string.invalid_directory));
                        }
                        bld.setPositiveButton(this.getString(android.R.string.ok), null);
                        bld.create().show();
                        break;
                    }
                }
                break;
            case REQUEST_PREFS_READ_PICKER:
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    if (readPrefs(uri, false)) onCreate(null);
                    break;
                }
                break;
            case REQUEST_FONT_PICKER:
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        DocumentFile pickedFile = DocumentFile.fromSingleUri(this, uri);
                        if (pickedFile != null && pickedFile.getName() != null && pickedFile.getName().matches(".*\\.(?i)(ttf|ttc|otf)")) {
                            try {
                                String path = UriToPath.getPath(this, uri);
                                if (path != null && new File(path).canRead()) {
                                    new Paint().setTypeface(Typeface.createFromFile(new File(path)));
                                } else {
                                    path = TermService.getAPPEXTFILES() + "/font.ttf";
                                    shell("rm -rf " + path);
                                    File dst = new File(path);
                                    InputStream is = getContentResolver().openInputStream(pickedFile.getUri());
                                    BufferedInputStream reader = new BufferedInputStream(is);
                                    OutputStream os = new FileOutputStream(dst);
                                    BufferedOutputStream writer = new BufferedOutputStream(os);
                                    byte[] buf = new byte[4096];
                                    int len;
                                    while ((len = reader.read(buf)) != -1) {
                                        writer.write(buf, 0, len);
                                    }
                                    writer.flush();
                                    writer.close();
                                    reader.close();
                                    new Paint().setTypeface(Typeface.createFromFile(dst));
                                    path = dst.getAbsolutePath();
                                }
                                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                                sp.edit().putString(FONT_FILENAME, path).apply();
                                AlertDialog.Builder bld = new AlertDialog.Builder(this);
                                bld.setIcon(android.R.drawable.ic_dialog_info);
                                bld.setMessage(this.getString(R.string.font_file_changed));
                                bld.setPositiveButton(this.getString(android.R.string.ok), null);
                                bld.create().show();
                            } catch (Exception e) {
                                AlertDialog.Builder bld = new AlertDialog.Builder(this);
                                bld.setIcon(android.R.drawable.ic_dialog_alert);
                                bld.setMessage(this.getString(R.string.font_file_invalid) + "\n\n" + pickedFile.getName());
                                bld.setPositiveButton(this.getString(android.R.string.ok), null);
                                bld.create().show();
                            }
                        } else {
                            AlertDialog.Builder bld = new AlertDialog.Builder(this);
                            bld.setIcon(android.R.drawable.ic_dialog_alert);
                            bld.setMessage(this.getString(R.string.font_file_error));
                            bld.setPositiveButton(this.getString(android.R.string.ok), null);
                            bld.create().show();
                            break;
                        }
                    }
                }
                break;
            case REQUEST_WRITE_PREFS_PICKER:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    if (result == RESULT_OK && data != null) {
                        Uri uri = data.getData();
                        if (uri != null) {
                            DocumentFile pickedFile = DocumentFile.fromSingleUri(this, uri);
                            if (pickedFile != null) {
                                @SuppressLint("SimpleDateFormat") String timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
                                AlertDialog.Builder bld = new AlertDialog.Builder(this);
                                bld.setIcon(android.R.drawable.ic_dialog_info);
                                bld.setPositiveButton(this.getString(android.R.string.ok), null);
                                try {
                                    OutputStream os = getContentResolver().openOutputStream(pickedFile.getUri());
                                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
                                    XmlUtils.writeMapXml(pref.getAll(), os);
                                    os.close();
                                } catch (Exception e) {
                                    bld.setMessage(this.getString(R.string.prefs_write_info_failure));
                                    bld.create().show();
                                    return;
                                }
                                bld.setMessage(this.getString(R.string.prefs_write_info_success));
                                bld.create().show();
                            }
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    private void doStartActivityForResult(Intent intent, int requestCode) {
        PackageManager pm = this.getApplicationContext().getPackageManager();
        if (intent.resolveActivity(pm) != null) startActivityForResult(intent, requestCode);
    }

    public static String getDirectory(Activity activity, Uri uri) {
        if (uri == null) return null;
        String path = null;
        path = UriToPath.getPath(activity.getApplicationContext(), uri);
        if (path != null && new File(path).canWrite()) return path;
        String scheme = uri.getScheme();
        if ("file".equals(scheme)) {
            path = uri.getPath();
        } else if ("content".equals(scheme)) {
            try {
                if ("com.android.externalstorage.documents".equals(uri.getAuthority())) { // ExternalStorageProvider
                    path = uri.getEncodedPath();
                    path = URLDecoder.decode(path, "UTF-8");
                    final String[] split = path.split(":");
                    final String type = split[0];
                    if ("/tree/primary".equalsIgnoreCase(type)) {
                        if (split.length == 1)
                            return Environment.getExternalStorageDirectory().getAbsolutePath();
                        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + split[1];
                    } else {
                        try {
                            if (split.length > 1) {
                                return "/stroage/" + type + "/" + split[1];
                            } else {
                                return "/stroage/" + type;
                            }
                        } catch (Exception e) {
                            return null;
                        }
                    }
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return path;
    }

    private ListPreference setFontList(ListPreference fontFileList) {
        File[] files = new File(FONT_PATH).listFiles();
        ArrayList<File> fonts = new ArrayList<File>();

        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().matches(".*\\.(?i)(ttf|ttc|otf)") && !file.isHidden()) {
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

        for (File file : fonts) {
            items[i] = file.getName();
            values[i] = file.getName();
            i++;
        }

        fontFileList.setEntries(items);
        fontFileList.setEntryValues(values);
        return fontFileList;
    }

    public static class ImePreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_ime);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("ime"));
            bindPreferenceSummaryToValue(findPreference("ime_direct_input_method"));
            bindPreferenceSummaryToValue(findPreference("ime_shortcuts_action_rev2"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public static class FunctionbarPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (AndroidCompat.SDK > Build.VERSION_CODES.KITKAT) {
                addPreferencesFromResource(R.xml.pref_functionbar);
            } else {
                addPreferencesFromResource(R.xml.pref_functionbar_20);
            }
            if (FLAVOR_VIM) {
                findPreference("functionbar_vim_paste").setDefaultValue(true);
            }
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("functionbar_diamond_action_rev2"));
            bindPreferenceSummaryToValue(findPreference("actionbar_invert_action"));
            bindPreferenceSummaryToValue(findPreference("actionbar_user_action"));
            bindPreferenceSummaryToValue(findPreference("actionbar_x_action"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public static class GesturePreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (AndroidCompat.SDK > Build.VERSION_CODES.KITKAT) {
                addPreferencesFromResource(R.xml.pref_gesture);
            } else {
                addPreferencesFromResource(R.xml.pref_gesture_20);
            }
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("backaction"));
            bindPreferenceSummaryToValue(findPreference("double_tap_action"));
            bindPreferenceSummaryToValue(findPreference("right_double_tap_action"));
            bindPreferenceSummaryToValue(findPreference("left_double_tap_action"));
            bindPreferenceSummaryToValue(findPreference("bottom_double_tap_action"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public static class ScreenPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (AndroidCompat.SDK > Build.VERSION_CODES.KITKAT) {
                addPreferencesFromResource(R.xml.pref_screen);
            } else {
                addPreferencesFromResource(R.xml.pref_screen_20);
            }
            final String APP_INFO_KEY = "notification";
            Preference appInfoPref = getPreferenceScreen().findPreference(APP_INFO_KEY);
            appInfoPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (mTermPreference != null) mTermPreference.applicationInfo();
                    return true;
                }
            });
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("orientation"));
            bindPreferenceSummaryToValue(findPreference("cursorstyle"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public static class FontPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (AndroidCompat.SDK >= Build.VERSION_CODES.KITKAT) {
                if (AndroidCompat.SDK > Build.VERSION_CODES.KITKAT) {
                    addPreferencesFromResource(R.xml.pref_font);
                } else {
                    addPreferencesFromResource(R.xml.pref_font_20);
                }
                final String FONT_FILE_PICKER_KEY = "fontfile_picker";
                Preference fontPrefs = getPreferenceScreen().findPreference(FONT_FILE_PICKER_KEY);
                fontPrefs.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (mTermPreference != null) mTermPreference.fontFilePicker();
                        return true;
                    }
                });
            } else {
                addPreferencesFromResource(R.xml.pref_font_18);
                final String FONTFILE = "fontfile";
                ListPreference fontFileList = (ListPreference) findPreference(FONTFILE);
                if (mTermPreference != null) mTermPreference.setFontList(fontFileList);

                Preference fontSelect = findPreference(FONTFILE);
                Resources res = getResources();
                fontSelect.setSummary(res.getString(R.string.summary_fontfile_preference) + String.format(" (%s)", FONT_PATH));
                fontSelect.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        ListPreference fontFileList = (ListPreference) preference;
                        if (mTermPreference != null) mTermPreference.setFontList(fontFileList);
                        return true;
                    }
                });
                fontSelect.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        ListPreference fontFileList = (ListPreference) preference;
                        if (mTermPreference != null) {
                            mTermPreference.setFontList(fontFileList);
                            fontFileList.setDefaultValue(newValue);
                        }
                        return true;
                    }
                });
            }
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public static class KeyboardPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (AndroidCompat.SDK > Build.VERSION_CODES.KITKAT) {
                addPreferencesFromResource(R.xml.pref_keyboard);
            } else {
                addPreferencesFromResource(R.xml.pref_keyboard_20);
            }
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    private static final boolean SCOPED_STORAGE = ShellTermSession.SCOPED_STORAGE;

    public static class ShellPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (AndroidCompat.SDK >= Build.VERSION_CODES.KITKAT) {
                if (SCOPED_STORAGE) {
                    addPreferencesFromResource(R.xml.pref_shell);
                } else if (AndroidCompat.SDK >= Build.VERSION_CODES.LOLLIPOP) {
                    addPreferencesFromResource(R.xml.pref_shell_28);
                } else {
                    addPreferencesFromResource(R.xml.pref_shell_20);
                }
                if (!SCOPED_STORAGE) {
                    final String HOME_KEY = "home_dir_chooser";
                    Preference prefsHome = getPreferenceScreen().findPreference(HOME_KEY);
                    prefsHome.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            if (mTermPreference != null) {
                                mTermPreference.homeDirectoryPicker(getActivity().getString(R.string.choose_home_directory_message));
                            }
                            return true;
                        }
                    });
                    final String STARTUP_KEY = "startup_dir_chooser";
                    Preference prefsStartup = getPreferenceScreen().findPreference(STARTUP_KEY);
                    prefsStartup.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            if (mTermPreference != null) {
                                mTermPreference.startupDirectoryPicker(getActivity().getString(R.string.choose_startup_directory_message));
                            }
                            return true;
                        }
                    });
                }
                if (AndroidCompat.SDK >= Build.VERSION_CODES.LOLLIPOP) {
                    final String FORCE_64BIT_KEY = "force_64bit";
                    Preference force64bitPref = getPreferenceScreen().findPreference(FORCE_64BIT_KEY);
                    force64bitPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            if (mTermPreference != null) mTermPreference.forceLibrary();
                            return true;
                        }
                    });
                }
            } else {
                addPreferencesFromResource(R.xml.pref_shell_18);
            }
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("termtype"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    private void chooseLdLibraryMode() {
        final String[] items = {
                this.getString(R.string.ld_library_path_mode_default),
                this.getString(R.string.ld_library_path_mode_alt_1)};
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int mChecked = prefs.getInt("FATAL_CRASH_RESOLVER", 0);
        final Context context = this;
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(this.getString(R.string.choose_ld_library_path_mode))
                .setSingleChoiceItems(items, mChecked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (which == 0 || which == 1) {
                            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                            final SharedPreferences.Editor editor = prefs.edit();
                            editor.putInt("FATAL_CRASH_RESOLVER", which);
                            editor.apply();
                            AlertDialog.Builder bld = new AlertDialog.Builder(context);
                            bld.setIcon(android.R.drawable.ic_dialog_info);
                            bld.setMessage(context.getString(R.string.change_lib));
                            bld.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                }
                            });
                            bld.show();
                        }
                    }
                })
                .setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int m) {
                        dialog.dismiss();
                    }
                })
                .create();
        dlg.show();
    }

    private void forceLibrary() {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_alert);
        bld.setTitle(R.string.title_change_lib_preference);
        String message = this.getString(R.string.current_library) + " " + TermService.getArch();
        message = message + "\n" + this.getString(R.string.message_change_lib_preference);
        bld.setMessage(message);
        bld.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                chooseLibrary();
            }
        });
        bld.setNegativeButton(this.getString(android.R.string.no), null);
        bld.create().show();
    }

    void chooseLibrary() {
        String[] items = {
                this.getString(R.string.force_64bit),
                this.getString(R.string.force_32bit),
                this.getString(R.string.reset_to_default)
        };

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_change_lib_preference))
                .setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        confirmChangeLib(which);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    void confirmChangeLib(final int which) {
        final Context context = this;
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_alert);
        int messageId = R.string.reset_to_default;
        if (which < 2) messageId = which == 0 ? R.string.force_64bit : R.string.force_32bit;
        bld.setTitle(messageId);
        String message = this.getString(R.string.current_library) + " " + TermService.getArch() + "\n" + this.getString(R.string.confirm_change_lib);
        bld.setMessage(message);
        bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                shell("rm " + TermService.getAPPEXTFILES() + "/.64bit");
                shell("rm " + TermService.getAPPEXTFILES() + "/.32bit");
                if (which == 0) {
                    shell("cat " + TermService.getAPPEXTFILES() + "/version > " + TermService.getAPPEXTFILES() + "/.64bit");
                } else if (which == 1) {
                    shell("cat " + TermService.getAPPEXTFILES() + "/version > " + TermService.getAPPEXTFILES() + "/.32bit");
                }
                uninstallExtraContents();
                AlertDialog.Builder bld = new AlertDialog.Builder(context);
                bld.setIcon(android.R.drawable.ic_dialog_info);
                bld.setMessage(context.getString(R.string.change_lib));
                bld.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int m) {
                        dialog.dismiss();
                    }
                });
                bld.show();
            }
        });
        bld.setNegativeButton(this.getString(android.R.string.no), null);
        bld.show();
    }

    private void uninstallExtraContents() {
        Term.setUninstallExtraContents(true);
    }

    public static class AppsPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            boolean mFiles = !"".equals(Term.getAppFilerPackageName());
            if (mFiles) {
                addPreferencesFromResource(R.xml.pref_apps);
            } else {
                addPreferencesFromResource(R.xml.pref_apps_25);
            }

            String id = "external_app_package_name";
            ListPreference packageName = (ListPreference) getPreferenceScreen().findPreference(id);
            if (mTermPreference != null) {
                if (mLabels != null) packageName.setEntries(mLabels);
                if (mPackageNames != null) packageName.setEntryValues(mPackageNames);
            }
            final String MRU_KEY = "mru_command";
            Preference prefsMru = getPreferenceScreen().findPreference(MRU_KEY);
            prefsMru.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (mTermPreference != null) mTermPreference.mruCommand(MRU_KEY);
                    return true;
                }
            });
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("external_app_package_name"));
            bindPreferenceSummaryToValue(findPreference("external_app_button_mode"));
            bindPreferenceSummaryToValue(findPreference("cloud_dropbox_filepicker"));
            bindPreferenceSummaryToValue(findPreference("cloud_googledrive_filepicker"));
            bindPreferenceSummaryToValue(findPreference("cloud_onedrive_filepicker"));
            bindPreferenceSummaryToValue(findPreference("html_viewer_mode"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    private void mruCommand(String key) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final SharedPreferences.Editor editor = prefs.edit();
        final EditText editText = new EditText(this);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        editText.setText(sp.getString(key, getString(R.string.pref_mru_command_default)));
        editText.setSingleLine();

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_mrucommand_preference))
                .setMessage(getString(R.string.dialog_message_mrucommand_preference))
                .setView(editText)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String cmd = editText.getText().toString();
                        editor.putString(key, cmd);
                        editor.apply();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(this.getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                }).show();
    }

    public static class PrefsPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (FLAVOR_VIM && AndroidCompat.SDK >= Build.VERSION_CODES.KITKAT) {
                addPreferencesFromResource(R.xml.pref_user_setting);
                final String PREFS_KEY = "prefs_rw";
                Preference prefsPicker = getPreferenceScreen().findPreference(PREFS_KEY);
                prefsPicker.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (mTermPreference != null) mTermPreference.prefsPicker();
                        return true;
                    }
                });
            } else {
                addPreferencesFromResource(R.xml.pref_user_setting_18);
            }

            final String APP_INFO_KEY = "app_info";
            Preference appInfoPref = getPreferenceScreen().findPreference(APP_INFO_KEY);
            appInfoPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (mTermPreference != null) mTermPreference.applicationInfo();
                    return true;
                }
            });
            final String LICENSE_KEY = "license";
            Preference licensePrefs = getPreferenceScreen().findPreference(LICENSE_KEY);
            licensePrefs.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (mTermPreference != null) mTermPreference.licensePrefs();
                    return true;
                }
            });
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

}

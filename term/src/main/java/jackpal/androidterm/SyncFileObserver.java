package jackpal.androidterm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Build;
import android.os.FileObserver;
import android.provider.DocumentsContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class SyncFileObserverMru {
    private Uri uri;
    private String path;
    SyncFileObserverMru() {
        this.uri = null;
        this.path = null;
    }

    SyncFileObserverMru(Uri uri, String path) {
        this.uri = uri;
        this.path = path;
    }

    Uri getUri() {
        return uri;
    }

    String getPath() {
        return path;
    }
}

class SyncFileObserver extends RecursiveFileObserver {
    class Info {
        private String uriString;
        private String hash;
        private long time;

        Info() {
            this.uriString = null;
            this.hash = null;
            this.time = -1;
        }

        Info(Uri uri, String hash) {
            this.uriString = uri.toString();
            this.hash = hash;
            this.time = System.currentTimeMillis();
        }

        Info(Uri uri, String hash, long millis) {
            this.uriString = uri.toString();
            this.hash = hash;
            this.time = millis;
        }

        Uri getUri() {
            return Uri.parse(uriString);
        }

        void setUri(Uri uri) {
            if (uri != null) uriString = uri.toString();
        }

        String getHash() {
            return hash;
        }

        void setHash(String hash) {
            if (hash != null) this.hash = hash;
        }

        long getTime() {
            return time;
        }

        void setTime(long millis) {
            time = millis;
        }

        int compareTo(Info value) {
            long c = value.getTime() - time;
            if (c == 0) return 0;
            else return (c > 0 ? 1 : -1);
        }

        @Override
        public String toString() {
            String str = null;
            try {
                JSONObject jsonOneData = new JSONObject();
                jsonOneData.put("uriString", this.uriString);
                jsonOneData.put("hash", this.hash);
                jsonOneData.put("time", String.valueOf(this.time));
                str = jsonOneData.toString(4);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return str;
        }
    }

    static private Map<String, Info> mHashMap = new HashMap<>();
    private File mCacheDir;
    private ContentResolver mContentResolver = null;
    private static Object mObjectActivity = null;
    private boolean mConfirmDeleteFromStorage = false;
    private boolean mActive;

    SyncFileObserver(String path) {
        this(path, ALL_EVENTS);
    }

    private SyncFileObserver(String path, int mask) {
        super(path, mask);
        mCacheDir = new File(path);
        mActive = true;
    }

    @Override
    public void onEvent(int event, String path) {
        if (!mActive || !mHashMap.containsKey(path)) return;
        switch (event) {
            // case FileObserver.DELETE_SELF:
            case FileObserver.DELETE:
                confirmDelete(mHashMap.get(path).getUri(), new File(path), mContentResolver);
                break;
            case FileObserver.OPEN:
                mHashMap.get(path).setTime(System.currentTimeMillis());
                break;
            // case FileObserver.MODIFY:
            case FileObserver.CLOSE_WRITE:
                mHashMap.get(path).setTime(System.currentTimeMillis());
                flushCache(mHashMap.get(path).getUri(), new File(path), mContentResolver);
                break;
            // case FileObserver.ACCESS:
            //     mHashMap.get(path).setTime(System.currentTimeMillis());
            //     break;
            default:
                break;
        }
    }

    void setContentResolver(ContentResolver cr) {
        mContentResolver = cr;
    }

    void setActivity(Activity activity) {
        mObjectActivity = activity;
        if (activity != null) setContentResolver(activity.getContentResolver());
    }

    String getObserverDir() {
        return mCacheDir.getAbsolutePath();
    }

    private static int mMaxSyncFiles = 300;

    public void setMaxSyncFiles(int max) {
        mMaxSyncFiles = max > 100 ? max : 100;
    }

    LinkedList<SyncFileObserverMru> getMRU() {
        if (mCacheDir == null || mHashMap == null) return null;
        List<Map.Entry<String, Info>> list_entries = new ArrayList<>(mHashMap.entrySet());

        Collections.sort(list_entries, new Comparator<Map.Entry<String, Info>>() {
            public int compare(Map.Entry<String, Info> obj1, Map.Entry<String, Info> obj2) {
                return obj1.getValue().compareTo(obj2.getValue());
            }
        });
        LinkedList<SyncFileObserverMru> mru = new LinkedList<SyncFileObserverMru>();//[1]
        for (Map.Entry<String, Info> map : list_entries) {
            Info info = map.getValue();
            mru.add(new SyncFileObserverMru(info.getUri(), map.getKey()));
        }
        return mru;
    }

    void clearOldCache() {
        if (mCacheDir == null || mHashMap == null) return;
        if (mHashMap.size() <= mMaxSyncFiles) return;

        List<Map.Entry<String, Info>> list_entries = new ArrayList<>(mHashMap.entrySet());

        Collections.sort(list_entries, new Comparator<Map.Entry<String, Info>>() {
            public int compare(Map.Entry<String, Info> obj1, Map.Entry<String, Info> obj2) {
                return obj1.getValue().compareTo(obj2.getValue());
            }
        });
        int size = list_entries.size() - 1;
        int minSize = (size * 3) / 4;
        for (int i = size; i > minSize; i--) {
            String path = list_entries.get(i).getKey();
            stopWatching(path);
            list_entries.remove(i);
            new File(path).delete();
        }
        mHashMap.clear();
        for (Map.Entry<String, Info> map : list_entries) {
            mHashMap.put(map.getKey(), map.getValue());
        }
        deleteEmptyDirectory(mCacheDir);
    }

    void clearCache() {
        mActive = true;
        if (mCacheDir == null) return;
        mHashMap.clear();
        if (mCacheDir.isDirectory()) deleteFileOrFolderRecursive(mCacheDir);
        stopWatching();
    }

    /*
     * CAUTION: This function deletes reference directory of symbolic link. (Android N and earlier)
     */
    private boolean deleteFileOrFolderRecursive(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) return true;
        try {
            if (fileOrDirectory.isDirectory()) {
                File[] files = fileOrDirectory.listFiles();
                if (files != null && files.length > 0) {
                    for (File file : files) {
                        deleteFileOrFolderRecursive(file);
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return Files.deleteIfExists(fileOrDirectory.toPath());
            } else {
                return fileOrDirectory.delete();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void deleteEmptyDirectory(File d) {
        if (d == null) return;
        File[] files = d.listFiles();
        if (files == null) {
            return;
        } else if (files.length == 0) {
            d.delete();
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                deleteEmptyDirectory(f);
            }
        }
    }

    private final static String HASH_ERROR = "HASH_ERROR";
    private final static String HASH_ALGORITHM = "SHA-1";

    boolean putUriAndLoad(Uri uri, String path) {
        mActive = true;
        path = path.replaceAll("//", "/");
        path = new File(path).getAbsolutePath();
        String hash = makeCache(uri, new File(path));
        if (!hash.equals(HASH_ERROR)) {
            mHashMap.put(path, new Info(uri, hash, System.currentTimeMillis()));
            return true;
        }
        return false;
    }

    private String makeCache(final Uri uri, final File dst) {
        return makeCache(uri, dst, mContentResolver);
    }

    private String makeCache(final Uri uri, final File dst, final ContentResolver contentResolver) {
        if (dst == null || uri == null || contentResolver == null) return "";

        String hashValue = "";
        mActive = false;
        deleteFileOrFolderRecursive(dst);
        File parent = dst.getParentFile();
        if (parent != null) parent.mkdirs();
        try {
            MessageDigest md;
            try {
                md = MessageDigest.getInstance(HASH_ALGORITHM);
            } catch (NoSuchAlgorithmException e) {
                md = null;
            }

            InputStream is = contentResolver.openInputStream(uri);
            BufferedInputStream reader = new BufferedInputStream(is);

            OutputStream os = new FileOutputStream(dst);
            BufferedOutputStream writer = new BufferedOutputStream(os);
            byte[] buf = new byte[4096];
            int len;
            while ((len = reader.read(buf)) != -1) {
                if (md != null) md.update(buf, 0, len);
                writer.write(buf, 0, len);
            }
            writer.flush();
            writer.close();
            reader.close();
            if (md != null) {
                byte[] digest = md.digest();
                hashValue = toHexString(digest);
            }
        } catch (Exception e) {
            hashValue = HASH_ERROR;
        }
        startWatching(dst.getAbsolutePath());
        mActive = true;
        return hashValue;
    }

    static String digest(InputStream is)
            throws NoSuchAlgorithmException, IOException, DigestException {
        MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);

        byte[] buf = new byte[4096];
        int len;
        while ((len = is.read(buf)) != -1) {
            md.update(buf, 0, len);
        }
        byte[] digest = md.digest();
        return toHexString(digest);
    }

    private static String toHexString(byte[] digest) {
        StringBuilder buff = new StringBuilder();
        for (byte b : digest) {
            buff.append(String.format("%1$02x", b));
        }
        return buff.toString();
    }

    /*
     * HASH_CHECK_MODE
     * 0 : No check
     * 1 : Check if the destination file has changed.
     * 2 : Upload only if the local hash and destination hash are different.
     */
    private static int HASH_CHECK_MODE = 2;

    private void flushCache(final Uri uri, final File file, final ContentResolver contentResolver) {
        if (contentResolver == null) return;
        if (HASH_CHECK_MODE == 0) {
            flushCacheExec(uri, file, contentResolver);
            return;
        }

        final String oldHash = mHashMap.get(file.getAbsolutePath()).getHash();
        try {
            InputStream dstIs = contentResolver.openInputStream(uri);
            if (dstIs != null) {
                String hashDst = digest(dstIs);
                dstIs.close();
                if (hashDst.equals("")) {
                    flushCacheExec(uri, file, contentResolver);
                    return;
                }
                if (HASH_CHECK_MODE == 2) {
                    InputStream srcIs = new FileInputStream(file.getAbsolutePath());
                    String hashSrc = digest(srcIs);
                    srcIs.close();
                    if (hashDst.equals(hashSrc)) return;
                }
                if (hashDst.equals(oldHash)) {
                    flushCacheExec(uri, file, contentResolver);
                } else {
                    hashErrorDialog((Activity) mObjectActivity, uri, file, contentResolver);
                }
            }
        } catch (Exception e) {
            flushCacheExec(uri, file, contentResolver);
        }
    }

    private static boolean mDialogIsActive = false;

    private void flushCacheExec(final Uri uri, final File file, final ContentResolver contentResolver) {
        if (contentResolver == null) return;

        try {
            MessageDigest md;
            try {
                md = MessageDigest.getInstance(HASH_ALGORITHM);
            } catch (NoSuchAlgorithmException e) {
                md = null;
            }

            OutputStream os = contentResolver.openOutputStream(uri);
            BufferedOutputStream writer = new BufferedOutputStream(os);

            InputStream is = new FileInputStream(file);
            BufferedInputStream reader = new BufferedInputStream(is);

            byte[] buf = new byte[16 * 1024];
            int len;
            while ((len = reader.read(buf)) != -1) {
                if (md != null) md.update(buf, 0, len);
                writer.write(buf, 0, len);
            }
            writer.flush();
            writer.close();
            reader.close();
            if (md != null) {
                byte[] digest = md.digest();
                String hashValue = toHexString(digest);
                mHashMap.get(file.getAbsolutePath()).setHash(hashValue);
            }
        } catch (Exception e) {
            writeErrorDialog((Activity) mObjectActivity);
        }
    }

    private void hashErrorDialog(final Activity activity, final Uri uri, final File file, final ContentResolver contentResolver) {
        if (activity != null) activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!mDialogIsActive) {
                    AlertDialog.Builder bld = new AlertDialog.Builder(activity);
                    bld.setIcon(android.R.drawable.ic_dialog_alert);
                    bld.setTitle(R.string.storage_hash_error_title);
                    bld.setMessage(R.string.storage_hash_error);
                    bld.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            mDialogIsActive = false;
                        }
                    });
                    bld.setNeutralButton(R.string.storage_hash_overwrite, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            mDialogIsActive = false;
                            flushCacheExec(uri, file, contentResolver);
                        }
                    });
                    bld.setPositiveButton(R.string.external_storage, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            mDialogIsActive = false;
                            try {
                                Term term = (Term) activity;
                                term.intentFilePicker();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    bld.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            mDialogIsActive = false;
                        }
                    });
                    mDialogIsActive = true;
                    try {
                        bld.create().show();
                    } catch (Exception e) {
                        // do nothing
                    }
                }
            }
        });
    }

    private void writeErrorDialog(final Activity activity) {
        if (activity != null) activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!mDialogIsActive) {
                    AlertDialog.Builder bld = new AlertDialog.Builder(activity);
                    bld.setIcon(android.R.drawable.ic_dialog_alert);
                    bld.setTitle(R.string.storage_write_error_title);
                    bld.setMessage(R.string.storage_write_error);
                    bld.setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                            mDialogIsActive = false;
                        }
                    });
                    bld.setNeutralButton(R.string.external_storage, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                            mDialogIsActive = false;
                            try {
                                Term term = (Term) activity;
                                term.openDrawer();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    bld.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            mDialogIsActive = false;
                        }
                    });
                    mDialogIsActive = true;
                    try {
                        bld.create().show();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @SuppressLint("NewApi")
    private void confirmDelete(final Uri uri, final File path, final ContentResolver contentResolver) {
        if (!mConfirmDeleteFromStorage || mObjectActivity == null) return;
        final Activity activity = (Activity) mObjectActivity;
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String file = path.getName();
                final AlertDialog.Builder b = new AlertDialog.Builder(activity);
                b.setIcon(android.R.drawable.ic_dialog_alert);
                b.setTitle(R.string.storage_delete_file);
                b.setMessage(file);
                b.setPositiveButton(R.string.delete_file, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        try {
                            DocumentsContract.deleteDocument(contentResolver, uri);
                            deleteEmptyDirectory(mCacheDir);
                            dialog.dismiss();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                b.setNegativeButton(android.R.string.no, null);
                b.show();
            }
        });
    }

    boolean restoreHashMap(File hashMapFile) {
        if (hashMapFile.exists()) {
            try {
                FileInputStream fis = new FileInputStream(hashMapFile.getAbsolutePath());
                int size = fis.available();
                byte[] buffer = new byte[size];
                fis.read(buffer);
                fis.close();

                String json = new String(buffer);
                JSONObject jsonObject = new JSONObject(json);
                JSONArray jsonArray = jsonObject.getJSONArray("mHashMap");
                for (int i = 0; i < jsonArray.length(); i++) {
                    try {
                        JSONObject jsonOneRecord = jsonArray.getJSONObject(i);
                        String path = (String) jsonOneRecord.get("path");
                        Info info = new Info();
                        info.uriString = (String) jsonOneRecord.get("info.uriString");
                        info.hash = (String) jsonOneRecord.get("info.hash");
                        info.time = Long.valueOf((String) jsonOneRecord.get("info.time"));
                        mHashMap.put(path, info);
                    } catch (Exception e) {
                        // invalid data
                        e.printStackTrace();
                    }
                }
                fis.close();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        mActive = true;
        return true;
    }

    boolean saveHashMap(File hashMapFile) {
        try {
            JSONObject jsonObject = new JSONObject();
            JSONArray jsonArary = new JSONArray();

            if (mHashMap == null || mHashMap.size() == 0) return true;
            for (Map.Entry<String, Info> entry : mHashMap.entrySet()) {
                JSONObject jsonOneData = new JSONObject();
                jsonOneData.put("path", entry.getKey());
                Info info = entry.getValue();
                jsonOneData.put("info.uriString", info.uriString);
                jsonOneData.put("info.hash", info.hash);
                jsonOneData.put("info.time", String.valueOf(info.time));
                jsonArary.put(jsonOneData);
            }
            jsonObject.put("mHashMap", jsonArary);

            FileWriter fileWriter = new FileWriter(hashMapFile);
            BufferedWriter bw = new BufferedWriter(fileWriter);
            PrintWriter pw = new PrintWriter(bw);
            String str = jsonObject.toString(4);
            pw.write(str);
            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}


package jackpal.androidterm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.FileObserver;
import android.provider.DocumentsContract;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

class SyncFileObserver extends RecursiveFileObserver {

    static private Map<String, String> mHashMap = new HashMap<>();
    static private HashSet mHashSet = new HashSet();
    private File mCacheDir = null;
    private ContentResolver mContentResolver = null;
    private Activity mActivity = null;
    private boolean mDeleteFromStorage;
    private boolean mActive = false;

    SyncFileObserver(String path) {
        this(path, ALL_EVENTS);
    }

    SyncFileObserver(String path, int mask) {
        super(path, mask);
        mCacheDir = new File(path);
        mHashSet.clear();
        mDeleteFromStorage = false;
        mActive = true;
    }

    @Override
    public void onEvent(int event, String path) {
        if (!mActive || !mHashMap.containsKey(path)) return;
        switch (event) {
//            case FileObserver.DELETE_SELF:
            case FileObserver.DELETE:
                confirmDelete(Uri.parse(mHashMap.get(path)), new File(path), mContentResolver);
                break;
            case FileObserver.OPEN:
                // if (!mHashSet.contains(path)) {
                //     makeCache(Uri.parse(mHashMap.get(path)), new File(path));
                //     mHashSet.add(path);
                // }
                break;
            // case FileObserver.MODIFY:
            case FileObserver.CLOSE_WRITE:
                flushCache(Uri.parse(mHashMap.get(path)), new File(path), mContentResolver);
                break;
            default:
                break;
        }
    }

    void setConTentResolver(ContentResolver cr) {
        mContentResolver = cr;
    }

    void setActivity(Activity activity) {
        mActivity = activity;
    }

    String getObserverDir() {
        return mCacheDir.toString();
    }

    Map<String, String> getHashMap() {
        return mHashMap;
    }

    boolean putHashMap(String path, String uri) {
        mActive = true;
        mHashMap.put(path, uri);
        return true;
    }

    boolean clearCache() {
        mActive = true;
        if (mCacheDir == null) return false;
        mHashMap.clear();
        mHashSet.clear();
        if (mCacheDir.isDirectory()) delete(mCacheDir);
        stopWatching();
        return false;
    }

    private static void delete(File f) {
        if(!f.exists()) {
            return;
        }
        if (f.isFile()) {
            f.delete();
        } else if (f.isDirectory()) {
            File[] files = f.listFiles();
            for (File file : files) {
                delete(file);
            }
            f.delete();
        }
    }

    public static void deleteEmptyDirectory(File d) {
        File[] files = d.listFiles();
        if (files == null) {
            return;
        } else if (files.length == 0) {
            d.delete();
            return;
        }
        for (File f: files) {
            if (f.isDirectory()) {
                deleteEmptyDirectory(f);
            }
        }
    }

    boolean putUriAndLoad(Uri uri, String path) {
        mActive = true;
        path = path.replaceAll("//", "/");
        putHashMap(path, uri.toString());
        boolean load = makeCache(uri, new File(path));
        return load;
    }

    private boolean makeCache(final Uri uri, final File dst) {
        return makeCache(uri, dst, mContentResolver);
    }

    private boolean makeCache(final Uri uri, final File dst, final ContentResolver contentResolver) {
        if (dst == null || uri == null || contentResolver == null) return false;

        boolean result = true;
        mActive = false;
        dst.mkdirs();
        if (dst.isDirectory()) delete(dst);
        try {
            InputStream is = contentResolver.openInputStream(uri);
            BufferedInputStream reader = new BufferedInputStream(is);

            OutputStream os = new FileOutputStream(dst);
            BufferedOutputStream writer = new BufferedOutputStream(os);
            byte buf[] = new byte[4096];
            int len;
            while ((len = reader.read(buf)) != -1) {
                writer.write(buf, 0, len);
            }
            writer.flush();
            writer.close();
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            result = false;
        }
        startWatching(dst.toString());
        mActive = true;
        return result;
    }

    static boolean mDialogIsActive = false;
    private void flushCache(final Uri uri, final File file, final ContentResolver contentResolver) {
        if (contentResolver == null) return;

        try {
            OutputStream os = contentResolver.openOutputStream(uri);
            BufferedOutputStream writer = new BufferedOutputStream(os);

            InputStream is = new FileInputStream(file);
            BufferedInputStream reader = new BufferedInputStream(is);

            byte buf[] = new byte[4096];
            int len;
            while ((len = reader.read(buf)) != -1) {
                writer.write(buf, 0, len);
            }
            writer.flush();
            writer.close();
            reader.close();
        } catch (Exception e) {
            if (mActivity != null) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!mDialogIsActive) {
                            AlertDialog.Builder bld = new AlertDialog.Builder(mActivity);
                            bld.setIcon(android.R.drawable.ic_dialog_alert);
                            bld.setTitle(R.string.storage_write_error_title);
                            bld.setMessage(R.string.storage_write_error);
                            bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });
                            bld.setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    mDialogIsActive = false;
                                }
                            });
                            mDialogIsActive = true;
                            bld.create().show();
                        }
                    }
                });
            }
        }
    }

    public void deleteFromStorage(boolean delete) {
        mDeleteFromStorage = delete;
    }

    @SuppressLint("NewApi")
    private void confirmDelete(final Uri uri, final File path, final ContentResolver contentResolver) {
        if (!mDeleteFromStorage || mActivity == null) return;
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) {
            return;
        }
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String file = path.getName();
                final AlertDialog.Builder b = new AlertDialog.Builder(mActivity);
                b.setIcon(android.R.drawable.ic_dialog_alert);
                b.setTitle(R.string.storage_delete_file);
                b.setMessage(file);
                b.setPositiveButton(R.string.delete_file, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        boolean result = DocumentsContract.deleteDocument(contentResolver, uri);
                        deleteEmptyDirectory(mCacheDir);
                        dialog.dismiss();
                    }
                });
                b.setNegativeButton(android.R.string.no, null);
                b.show();
            }
        });
    }
}



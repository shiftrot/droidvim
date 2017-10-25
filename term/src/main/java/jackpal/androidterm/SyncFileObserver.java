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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private final static String HASH_ERROR = "HASH_ERROR";
    private final static String HASH_ALGORITHM = "SHA-1";
    boolean putUriAndLoad(Uri uri, String path) {
        mActive = true;
        path = path.replaceAll("//", "/");
        String hash = makeCache(uri, new File(path));
        if (!hash.equals(HASH_ERROR)) {
            putHashMap(path, uri.toString());
            putHashMap(HASH_ALGORITHM+path, hash);
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
        dst.mkdirs();
        if (dst.isDirectory()) delete(dst);
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
            byte buf[] = new byte[4096];
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
        } catch (IOException e) {
            e.printStackTrace();
            mActive = true;
            return HASH_ERROR;
        }
        startWatching(dst.toString());
        mActive = true;
        return hashValue;
    }

    private static String digest(InputStream is)
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

    private static String toHexString (byte[] digest) {
        StringBuilder buff = new StringBuilder();
        for (byte b : digest) {
            buff.append(String.format("%1$02x", b));
        }
        return buff.toString();
    }

    public static int HASH_CHECK_MODE = 2;
    private void flushCache(final Uri uri, final File file, final ContentResolver contentResolver) {
        if (contentResolver == null) return;
        if (HASH_CHECK_MODE == 0) {
            flushCacheExec(uri, file, contentResolver);
            return;
        }

        final String oldHash = mHashMap.get(HASH_ALGORITHM+file.toString());
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
                    InputStream srcIs = new FileInputStream(file.toString());
                    String hashSrc = digest(srcIs);
                    srcIs.close();
                    if (hashDst.equals(hashSrc)) return;
                }
                if (hashDst.equals(oldHash)) {
                    flushCacheExec(uri, file, contentResolver);
                } else {
                    if (mActivity != null) {
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!mDialogIsActive) {
                                    AlertDialog.Builder bld = new AlertDialog.Builder(mActivity);
                                    bld.setIcon(android.R.drawable.ic_dialog_alert);
                                    bld.setTitle(R.string.storage_hash_error_title);
                                    bld.setMessage(R.string.storage_hash_error);
                                    bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
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

            byte buf[] = new byte[4096];
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
                putHashMap(HASH_ALGORITHM+file.toString(), hashValue);
            }
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
                                    mDialogIsActive = false;
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
                        try {
                            boolean result = DocumentsContract.deleteDocument(contentResolver, uri);
                            deleteEmptyDirectory(mCacheDir);
                            dialog.dismiss();
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                });
                b.setNegativeButton(android.R.string.no, null);
                b.show();
            }
        });
    }
}



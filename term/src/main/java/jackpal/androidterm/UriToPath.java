package jackpal.androidterm;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;

import androidx.appcompat.app.AlertDialog;

import static android.provider.DocumentsContract.getDocumentId;
import static androidx.documentfile.provider.DocumentFile.isDocumentUri;
import static jackpal.androidterm.Term.isInternalPrivateStorageDocument;

public class UriToPath {
    public static String getPath(final Context context, final Uri uri) {
        String realPath = getRealPathFromURI(context, uri);
        if (realPath != null && new File(realPath).exists() && new File(realPath).canWrite())
            return realPath;
        if (uri == null) return null;
        if (isDocumentUri(context, uri)) {
            try {
                File file = new File(getDocumentId(uri));
                String path = file.getAbsolutePath();
                if (path.startsWith("/raw:/")) {
                    file = new File(path.replaceFirst("/raw:", ""));
                }
                if (file.exists() && file.canWrite()) return file.getAbsolutePath();
            } catch (Exception e) {
                Log.d("FilePicker", e.toString());
            }
            if (isExternalStorageDocument(uri)) {
                final String docId = getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                File file;
                if ("primary".equalsIgnoreCase(type)) {
                    file = new File(Environment.getExternalStorageDirectory() + "/" + split[1]);
                    if (file.exists() && file.canWrite()) {
                        return file.toString();
                    }
                }

                String path = uri.getPath();
                path = path.replaceAll(":", "/");
                path = path.replaceFirst("document", "storage");
                file = new File(path);
                if (file.exists() && file.canWrite()) {
                    return file.toString();
                }
                path = uri.getPath();
                final File[] dirs = context.getExternalFilesDirs(null);
                if (dirs != null && dirs.length >= 2) {
                    for (File dir : dirs) {
                        if (dir == null) continue;
                        path = dir.getAbsolutePath().replaceAll(type.concat(".*"), "");
                        path = String.format("%s/%s/%s", path, type, split[1]);
                        path = path.replaceAll("/+", "/");
                        file = new File(path);
                        if (file.exists() && file.canWrite()) {
                            return file.toString();
                        }
                    }
                }
                return null;
            } else if (isInternalPrivateStorageDocument(uri)) {
                String path = uri.getPath();
                path = path.replaceAll(":", "/");
                path = path.replaceFirst("/document/", "");
                File file = new File(path);
                if (file.exists()) {
                    return file.toString();
                }
            }
        }
        String path = Uri.decode(uri.toString());
        final String AUTHORITY_DROIDVIM = "content://" + BuildConfig.APPLICATION_ID + ".storage.documents/tree/";
        if (path != null && path.startsWith(AUTHORITY_DROIDVIM)) {
            path = path.substring(AUTHORITY_DROIDVIM.length());
            if (new File(path).exists()) return path;
        }
        final String AUTHORITY_TRESORIT = "content://com.tresorit.mobile.provider/external_files/";
        if (path != null && path.matches("^" + AUTHORITY_TRESORIT + ".*$")) {
            path = Environment.getExternalStorageDirectory().toString() + path.substring(AUTHORITY_TRESORIT.length() - 1);
            if (new File(path).exists()) return path;
        }
        return null;
    }

    @SuppressLint("NewApi")
    private static String getRealPathFromURI(final Context context, final Uri uri) {
        try {
            // DocumentProvider
            Log.e(TermDebug.LOG_TAG, "uri:" + uri.getAuthority());
            if (isDocumentUri(context, uri)) {
                if ("com.android.externalstorage.documents".equals(
                        uri.getAuthority())) { // ExternalStorageProvider
                    final String docId = getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory() + "/" + split[1];
                    } else {
                        return "/stroage/" + type + "/" + split[1];
                    }
                } else if ("com.android.providers.downloads.documents".equals(
                        uri.getAuthority())) { // DownloadsProvider
                    final String id = getDocumentId(uri);
                    final Uri contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                    return getDataColumn(context, contentUri, null, null);
                } else if ("com.android.providers.media.documents".equals(
                        uri.getAuthority())) { // MediaProvider
                    final String docId = getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    Uri contentUri = null;
                    contentUri = MediaStore.Files.getContentUri("external");
                    final String selection = "_id=?";
                    final String[] selectionArgs = new String[]{
                            split[1]
                    };
                    return getDataColumn(context, contentUri, selection, selectionArgs);
                }
            } else if ("content".equalsIgnoreCase(uri.getScheme())) { // MediaStore
                return getDataColumn(context, uri, null, null);
            } else if ("file".equalsIgnoreCase(uri.getScheme())) { // File
                return uri.getPath();
            }
        } catch (Exception e) {
            AlertDialog.Builder bld = new AlertDialog.Builder(context);
            bld.setMessage(e.getMessage());
            bld.setPositiveButton(android.R.string.ok, null);
            Log.d(TermDebug.LOG_TAG, "Showing alert dialog: " + e.toString());
//            bld.create().show();
        }
        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String[] projection = {
                MediaStore.Files.FileColumns.DATA
        };
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                try {
                    final int cindex = cursor.getColumnIndexOrThrow(projection[0]);
                    return cursor.getString(cindex);
                } catch (Exception e) {
                    // do nothing
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    public static String getPathN(Context context, Uri uri) {
        try {
            String selection = null;
            String[] selectionArgs = null;
            if (DocumentsContract.isDocumentUri(context.getApplicationContext(), uri)) {
                try {
                    File file = new File(getDocumentId(uri));
                    String path = file.getAbsolutePath();
                    if (path.startsWith("/raw:/")) {
                        file = new File(path.replaceFirst("/raw:", ""));
                    }
                    if (file.exists() && file.canWrite()) return file.getAbsolutePath();
                } catch (Exception e) {
                    Log.d("FilePicker", e.toString());
                }
                if (isExternalStorageDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    File file;
                    if ("primary".equalsIgnoreCase(type)) {
                        file = new File(Environment.getExternalStorageDirectory() + "/" + split[1]);
                        if (file.exists() && file.canWrite()) {
                            return file.toString();
                        }
                    }

                    String path = uri.getPath();
                    path = path.replaceAll(":", "/");
                    path = path.replaceFirst("document", "storage");
                    file = new File(path);
                    if (file.exists() && file.canWrite()) {
                        return file.toString();
                    }

                    final File[] dirs = context.getExternalFilesDirs(null);
                    if (dirs != null && dirs.length >= 2) {
                        for (File dir : dirs) {
                            if (dir == null) continue;
                            path = dir.getAbsolutePath().replaceAll(type.concat(".*"), "");
                            path = String.format("%s/%s/%s", path, type, split[1]);
                            path = path.replaceAll("/+", "/");
                            file = new File(path);
                            if (file.exists() && file.canWrite()) {
                                return file.toString();
                            }
                        }
                    }
                    return null;
                } else if (isDownloadsDocument(uri)) {
                    final String id = DocumentsContract.getDocumentId(uri);
                    uri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                } else if (isMediaDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    if ("image".equals(type)) {
                        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    } else if ("video".equals(type)) {
                        uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    } else if ("audio".equals(type)) {
                        uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    }
                    selection = "_id=?";
                    selectionArgs = new String[]{split[1]};
                }
            }
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                String[] projection = {MediaStore.Images.Media.DATA};
                Cursor cursor = null;
                try {
                    cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    if (cursor.moveToFirst()) {
                        String path = cursor.getString(column_index);
                        File file = new File(path);
                        if (file.exists() && file.canWrite()) {
                            return file.toString();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                String path = uri.getPath();
                File file = new File(path);
                if (file.exists() && file.canWrite()) {
                    return file.toString();
                }
            }
        } catch (Exception e) {
            // Do nothing
        }
        String path = Uri.decode(uri.toString());
        final String AUTHORITY_TRESORIT = "content://com.tresorit.mobile.provider/external_files/";
        if (path != null && path.matches("^" + AUTHORITY_TRESORIT + ".*$")) {
            path = Environment.getExternalStorageDirectory().toString() + path.substring(AUTHORITY_TRESORIT.length() - 1);
            if (new File(path).exists()) return path;
        }
        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

}

package jackpal.androidterm;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;
import java.util.List;

import static android.provider.DocumentsContract.getDocumentId;

public class UriToPath {
    public static String getPath(final Context context, final Uri uri) {
        if (context == null || uri == null) return null;
        String path = getPathFromURI(context, uri);
        if (path != null && new File(path).canWrite()) return path;
        return null;
    }

    private static String getPathFromURI(final Context context, final Uri uri) {
        if (context == null || uri == null) return null;
        String path = getLocalStorageProviderPath(uri);
        if (path != null) return path;
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                final List<String> segments = uri.getPathSegments();
                final String docId = getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                final String id = split[1];
                File file;
                if ("primary".equalsIgnoreCase(type)) {
                    file = new File(Environment.getExternalStorageDirectory(), id);
                    if (file.exists() && file.canWrite()) return file.toString();
                } else if ("home".equalsIgnoreCase(type)) {
                    file = new File(Environment.getExternalStorageDirectory(), "/Documents/" + id);
                    if (file.exists() && file.canWrite()) return file.toString();
                } else if (type != null && type.matches("[0-9A-Z]{4}-[0-9A-Z]{4}")) {
                    List<String> storage = Uri.fromFile(Environment.getExternalStorageDirectory()).getPathSegments();
                    file = new File(File.separator + storage.get(0) + File.separator + type + File.separator + id);
                    if (file.exists() && file.canWrite()) return file.toString();
                } else if ("document".equalsIgnoreCase(segments.get(0))) {
                    file = new File(Environment.getExternalStorageDirectory(), File.separator + id);
                    if (file.exists() && file.canWrite()) return file.toString();
                }

                path = uri.getPath();
                file = new File(path.replaceFirst("document", "storage"));
                if (file.exists() && file.canWrite()) return file.toString();

                final File[] dirs = context.getExternalFilesDirs(null);
                if (dirs != null && dirs.length >= 2) {
                    for (File dir : dirs) {
                        if (dir == null) continue;
                        path = dir.getAbsolutePath().replaceAll(type.concat(".*"), "");
                        path = String.format("%s/%s/%s", path, type, split[1]);
                        path = path.replaceAll("/+", "/");
                        file = new File(path);
                        if (file.exists() && file.canWrite()) return file.toString();
                    }
                }
                return null;
            } else if (isDownloadsDocument(uri)) {
                try {
                    final String id = DocumentsContract.getDocumentId(uri);
                    final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(id));
                    return getDataColumn(context, contentUri, null, null);
                } catch (Exception e) {
                    return getDataColumn(context, uri, null, null);
                }
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                final String id = split[1];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                    id
                };
                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            if (isGooglePhotosUri(uri)) return uri.getLastPathSegment();
            path = getDataColumn(context, uri, null, null);
            if (path != null) return path;
            return getFileProviderPath(uri);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        if (context == null || uri == null) return null;
        final String[] projection = {
                MediaStore.Files.FileColumns.DATA
        };
        try {
            Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(projection[0]);
                String data = cursor.getString(index);
                cursor.close();
                return data;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getFileProviderPath(Uri uri) {
        try {
            List<String> uriList = uri.getPathSegments();
            List<String> segments = uriList.subList(1, uriList.size());
            if (uriList.size() > 3) {
                StringBuilder path = new StringBuilder();
                for (String segment : segments) {
                    path.append(File.separator);
                    path.append(segment);
                }

                File file = new File(path.toString());
                if (file.isFile()) return path.toString();
            }

            if (uriList.size() > 1) {
                StringBuilder path = new StringBuilder();
                path.append(Environment.getExternalStorageDirectory());
                for (String segment : segments) {
                    path.append(File.separator);
                    path.append(segment);
                }

                File file = new File(path.toString());
                if (file.isFile()) return path.toString();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static String getLocalStorageProviderPath(final Uri uri) {
        try {
            File file = new File(getDocumentId(uri));
            String path = file.getAbsolutePath();
            if (path.startsWith("/raw:/")) {
                file = new File(path.replaceFirst("/raw:", ""));
            }
            if (file.exists() && file.canWrite()) return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (isLocalStorageProvider(uri)) {
            String path = Uri.decode(uri.toString());
            path = path.replaceFirst(".*/document/", "");
            path = path.replaceAll("/+", "/");
            File file = new File(path);
            if (file.exists()) return file.toString();
        } else {
            final String TRESORIT = "content://com.tresorit.mobile.provider/external_files/";
            String path = Uri.decode(uri.toString());
            if (path != null && path.startsWith(TRESORIT)) {
                path = Environment.getExternalStorageDirectory().toString() + path.substring(TRESORIT.length() - 1);
                path = path.replaceAll("/+", "/");
                File file = new File(path);
                if (file.exists()) return file.toString();
            }
        }
        return null;
    }

    public static boolean isLocalStorageProvider(Uri uri) {
        return (BuildConfig.APPLICATION_ID + ".storage.documents").equals(uri.getAuthority());
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

    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

}

package jackpal.androidterm;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class ASFUtils {
    static AlertDialog mProcessingDialog = null;
    static private boolean mCANCEL = false;

    static public void directoryPicker(final AppCompatActivity activity, final int request, String mes, final ChooserDialog.Result r, int flags) {
        AlertDialog.Builder bld = new AlertDialog.Builder(activity);
        bld.setIcon(android.R.drawable.ic_dialog_info);
        bld.setTitle(activity.getString(R.string.select_directory_message));
        bld.setMessage(mes);
        bld.setPositiveButton(android.R.string.ok, (dialog, id) -> {
            dialog.dismiss();
            documentTreePicker(activity, request, flags);
        });
        bld.setNegativeButton(activity.getString(android.R.string.cancel), (dialog, id) -> dialog.dismiss());
        bld.setNeutralButton(activity.getString(R.string.reset_directory), (dialog, id) -> {
            dialog.dismiss();
            r.onChoosePath(null, null);
        });
        bld.create().show();
    }

    static public void documentTreePicker(final AppCompatActivity activity, int requestCode, int flags) {
        mCANCEL = false;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(flags);
        doStartActivityForResult(activity, intent, requestCode);
    }

    static private void doStartActivityForResult(AppCompatActivity activity, Intent intent, int requestCode) {
        PackageManager pm = activity.getApplicationContext().getPackageManager();
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (intent.resolveActivity(pm) != null)
            activity.startActivityForResult(intent, requestCode);
    }

    static public void backupToTreeUri(final AppCompatActivity activity, final Uri rootUri, final String path) {
        if (rootUri == null) return;
        if (isHomeDirectory(activity, rootUri)) return;
        DocumentFile root = DocumentFile.fromTreeUri(activity, rootUri);
        if (root != null) {
            if (isRootDirectory(root)) {
                alertDialog(activity, activity.getString(R.string.root_directory_error));
                return;
            }
            final AlertDialog dlg = createProcessingDialog(activity);
            mProcessingDialog = dlg;
            showDialog(activity, dlg);
            try {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            doBackupToTreeUri(activity, path, root);
                            alertDialog(activity, activity.getString(R.string.backup_restore_complete));
                        } catch (Exception e) {
                            alertDialog(activity, activity.getString(R.string.backup_restore_error) + "\n\n" + e.getMessage());
                        } finally {
                            dismissDialog(activity, dlg);
                        }
                    }
                }.start();
            } catch (Exception e) {
                // Do nothing
            }
        }
    }

    static private boolean isHomeDirectory(AppCompatActivity activity, Uri rootUri) {
        if (rootUri == null) return false;
        String local = rootUri.getPath();
        if (local != null) local = local.replaceFirst("/tree/", "");
        if (local != null && local.startsWith(TermService.getHOME()) && new File(local).canWrite()) {
            if (activity != null) {
                alertDialog(activity, activity.getString(R.string.root_directory_error));
            }
            return true;
        }
        return false;
    }

    static private boolean isRootDirectory(DocumentFile root) {
        Uri uri = root.getUri();
        String authority = uri.getAuthority();
        if ("com.android.providers.downloads.documents".equals(authority)) return true;
        String path = uri.getPath();
        return path != null && path.endsWith(":");
    }

    static private void alertDialog(AppCompatActivity activity, String message) {
        activity.runOnUiThread(() -> {
            final AlertDialog.Builder bld = new AlertDialog.Builder(activity);
            bld.setMessage(message);
            bld.setPositiveButton(android.R.string.ok, null);
            bld.show();
        });
    }

    static private void doBackupToTreeUri(AppCompatActivity activity, String rootPath, DocumentFile docRoot) throws Exception {
        File dir = new File(rootPath);
        File[] list = dir.listFiles();
        if (list != null) {
            setDialogMessage(activity, mProcessingDialog, rootPath);
            for (File file : list) {
                if (!ASFUtils.isSymlink(file)) {
                    if (mCANCEL) return;
                    if (file.isDirectory()) {
                        String name = file.getName();
                        DocumentFile newDir = docRoot.findFile(name);
                        if (newDir == null) newDir = docRoot.createDirectory(name);
                        if (newDir != null) {
                            Uri newRooturi = newDir.getUri();
                            DocumentFile newDocRoot = DocumentFile.fromTreeUri(activity, newRooturi);
                            doBackupToTreeUri(activity, file.toString(), newDocRoot);
                        }
                    } else {
                        String name = file.getName();
                        String ext = name.substring(name.lastIndexOf("."));
                        ext = ext.toLowerCase();
                        ext = ext.replaceAll("(html?)#.*", "$1");
                        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                        DocumentFile dstDoc = docRoot.findFile(name);
                        File src = new File(rootPath + "/" + name);
                        if (dstDoc == null || src.lastModified() > dstDoc.lastModified()) {
                            if (dstDoc == null)
                                dstDoc = docRoot.createFile(mimeType != null ? mimeType : "application/octet-stream", name);
                            if (dstDoc != null) {
                                InputStream is = new FileInputStream(src);
                                OutputStream os = activity.getContentResolver().openOutputStream(dstDoc.getUri());
                                cpStream(is, os);
                                setLastModified(dstDoc, activity, src.lastModified());
                            }
                        }
                    }
                }
            }
        }
    }

    static private void setLastModified(DocumentFile docFile, Context context, long time) {
        try {
            Uri uri = docFile.getUri();
            String path = UriToPath.getPath(context, uri);
            if (path != null) {
                new File(path).setLastModified(time);
            }
        } catch (Exception ignored) {
        }
    }

    static private void cpStream(InputStream is, OutputStream os) {
        try {
            byte[] buf = new byte[1024 * 4];
            int len;

            while ((len = is.read(buf)) > 0) {
                os.write(buf, 0, len);
                if (mCANCEL) break;
            }
        } catch (Exception e) {
            // Do nothing
        } finally {
            try {
                os.flush();
                is.close();
                os.close();
            } catch (Exception e) {
                // Do nothing
            }
        }
    }

    static public void restoreHomeFromTreeUri(final AppCompatActivity activity, final Uri rootUri, final String path) {
        if (rootUri == null) return;
        DocumentFile root = DocumentFile.fromTreeUri(activity, rootUri);
        if (isRootDirectory(root)) {
            alertDialog(activity, activity.getString(R.string.root_directory_error));
            return;
        }
        final AlertDialog dlg = createProcessingDialog(activity);
        mProcessingDialog = dlg;
        String message = rootUri.getPath();
        if (message != null) setDialogMessage(activity, mProcessingDialog, message);
        showDialog(activity, dlg);
        new Thread() {
            @Override
            public void run() {
                try {
                    doRestoreHomeFromTreeUri(activity, rootUri, path);
                    alertDialog(activity, activity.getString(R.string.backup_restore_complete));
                } catch (Exception e) {
                    alertDialog(activity, activity.getString(R.string.backup_restore_error) + "\n\n" + e.getMessage());
                } finally {
                    dismissDialog(activity, mProcessingDialog);
                }
            }
        }.start();
    }

    static private void doRestoreHomeFromTreeUri(final AppCompatActivity activity, final Uri rootUri, final String path) throws Exception {
        if (rootUri == null) return;
        if (isHomeDirectory(activity, rootUri)) return;
        ContentResolver contentResolver = activity.getContentResolver();
        Uri childrenUri;
        try {
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, DocumentsContract.getDocumentId(rootUri));
        } catch (Exception e) {
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, DocumentsContract.getTreeDocumentId(rootUri));
        }

        Cursor childCursor = contentResolver.query(childrenUri, new String[]{
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        DocumentsContract.Document.COLUMN_FLAGS},
                null, null, null);
        try {
            while (childCursor != null && childCursor.moveToNext()) {
                if (mCANCEL) return;
                String fileName = childCursor.getString(0);
                String mimeType = childCursor.getString(1);
                String docId = childCursor.getString(2);
                long mtime = Long.parseLong((childCursor.getString(3)));
                // int flag = Integer.parseInt(childCursor.getString(4));
                Uri uri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, docId);
                if (!isSymlink(uri)) {
                    if (isDirectory(mimeType)) {
                        File dir = new File(path + "/" + fileName);
                        if (!isSymlink(dir)) {
                            if (dir.isFile()) dir.delete();
                            dir.mkdirs();
                            if (!dir.exists() || dir.isDirectory()) {
                                doRestoreHomeFromTreeUri(activity, uri, dir.toString());
                            }
                        }
                    } else {
                        File dstFile = new File(path + "/" + fileName);
                        if (dstFile.lastModified() < mtime) {
                            InputStream is = activity.getContentResolver().openInputStream(uri);
                            OutputStream os = new FileOutputStream(dstFile);
                            cpStream(is, os);
                            dstFile.setLastModified(mtime);
                        }
                    }
                }
            }
        } finally {
            closeQuietly(childCursor);
        }
    }

    // Util method to check if the mime type is a directory
    static private boolean isDirectory(String mimeType) {
        return DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
    }

    private static boolean isSymlink(Uri uri) {
        return false;
    }

    public static boolean isSymlink(File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Files.isSymbolicLink(file.toPath());
        }
        try {
            if (file == null) return false;
            File canon;
            if (file.getParent() != null) {
                File canonDir = file.getParentFile().getCanonicalFile();
                canon = new File(canonDir, file.getName());
            } else {
                canon = file;
            }
            return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Util method to close a closeable
    static private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception ignore) {
                // ignore exception
            }
        }
    }

    static private AlertDialog createProcessingDialog(AppCompatActivity activity) {
        AlertDialog.Builder bld = new AlertDialog.Builder(activity);
        bld.setMessage(activity.getString(R.string.message_please_wait));
        bld.setNegativeButton(activity.getString(android.R.string.cancel), (dialog, id) -> {
            mCANCEL = true;
            dialog.dismiss();
        });
        bld.setCancelable(false);
        return bld.create();
    }

    static private void setDialogMessage(AppCompatActivity activity, AlertDialog dlg, String message) {
        try {
            dlg.setMessage(activity.getString(R.string.message_please_wait) + "\n - " + message);
        } catch (Exception e) {
            // Do nothing
        }
    }

    static private void dismissDialog(AppCompatActivity activity, AlertDialog dlg) {
        try {
            activity.runOnUiThread(dlg::dismiss);
        } catch (Exception e) {
            // Do nothing
        }
    }

    static private void showDialog(AppCompatActivity activity, AlertDialog dlg) {
        try {
            activity.runOnUiThread(() -> {
                try {
                    dlg.show();
                } catch (Exception e) {
                    // Do nothing
                }
            });
        } catch (Exception e) {
            // Do nothing
        }
    }
}

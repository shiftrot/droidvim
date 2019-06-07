package com.droidvim.storage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;

import androidx.core.content.ContextCompat;
import jackpal.androidterm.R;
import jackpal.androidterm.TermService;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class LocalStorageProvider extends DocumentsProvider {
    private static final String TAG = "LocalStorageProvider";
    private static final String TITLE = "DroidVim";
    @SuppressLint("SdCardPath")
    private static final String BASE_DEFAULT_DIR = "/data/data/com.droidvim/files/home";
    private static String mBASEDIR = BASE_DEFAULT_DIR;

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[]{
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE
    };

    private static final int MAX_SEARCH_RESULTS = 20;
    private static final int MAX_LAST_MODIFIED = 5;

    @Override
    public boolean onCreate() {
        try {
            if (getContext() != null) mBASEDIR = getContext().getFilesDir().getPath();
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getContext());
            mBASEDIR = pref.getString("home_path", BASE_DEFAULT_DIR);
        } catch (Exception e) {
            // Do nothing
        }
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_ROOT_PROJECTION);
        MatrixCursor.RowBuilder row;
        String title = TITLE;
        if (getContext() != null) {
            title = getContext().getString(R.string.application_term_app);
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getContext());
            mBASEDIR = pref.getString("home_path", mBASEDIR);
        }
        row = result.newRow();
        File homeDir = new File(mBASEDIR);
        row.add(Root.COLUMN_ROOT_ID, getDocIdForFile(homeDir));
        row.add(Root.COLUMN_DOCUMENT_ID, getDocIdForFile(homeDir));
        row.add(Root.COLUMN_TITLE, title);
        row.add(Root.COLUMN_SUMMARY, getContext().getString(R.string.title_home_path_preference));
        row.add(Root.COLUMN_ICON, R.mipmap.ic_launcher);
        int FLAGS = Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_SEARCH | Root.FLAG_SUPPORTS_RECENTS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            FLAGS |= Root.FLAG_SUPPORTS_IS_CHILD;
        row.add(Root.COLUMN_FLAGS, FLAGS);
        row.add(Root.COLUMN_MIME_TYPES, "*/*");
        row.add(Root.COLUMN_AVAILABLE_BYTES, homeDir.getFreeSpace());

        homeDir = Environment.getExternalStorageDirectory();
        String summary = "$INTERNAL_STORAGE";

        if (TextUtils.equals(Environment.getExternalStorageState(), Environment.MEDIA_MOUNTED)) {
            row = result.newRow();
            row.add(Root.COLUMN_ROOT_ID, getDocIdForFile(homeDir));
            row.add(Root.COLUMN_DOCUMENT_ID, getDocIdForFile(homeDir));
            row.add(Root.COLUMN_TITLE, title);
            row.add(Root.COLUMN_SUMMARY, summary);
            row.add(Root.COLUMN_ICON, R.drawable.ic_folder);
            row.add(Root.COLUMN_FLAGS, FLAGS);
            row.add(Root.COLUMN_MIME_TYPES, "*/*");
            row.add(Root.COLUMN_AVAILABLE_BYTES, new StatFs(getDocIdForFile(homeDir)).getAvailableBytes());
        }
        return result;
    }

    private static String getDocIdForFile(File file) {
        return file.getAbsolutePath();
    }

    private static File getFileForDocId(String docId) throws FileNotFoundException {
        final File file = new File(docId);
        if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath() + " not found");
        return file;
    }

    private boolean isMissingPermission(String documentId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return false;
        try {
            if (!getFileForDocId(documentId).getAbsolutePath().startsWith("/data")) {
                Context context = getContext();
                if (context != null)
                    return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
            }
            return false;
        } catch (FileNotFoundException e) {
            return true;
        }
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        includeFile(result, documentId, null);
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        final File parent = getFileForDocId(parentDocumentId);
        for (File file : parent.listFiles()) {
            includeFile(result, null, file);
        }
        return result;
    }

    private void includeFile(MatrixCursor result, String docId, File file) throws FileNotFoundException {
        if (docId == null) {
            docId = getDocIdForFile(file);
        } else {
            file = getFileForDocId(docId);
        }

        int flags = 0;
        if (file.isDirectory()) {
            if (file.canWrite())
                flags |= Document.FLAG_DIR_SUPPORTS_CREATE | Document.FLAG_SUPPORTS_DELETE;
        } else if (file.canWrite()) {
            flags |= Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            flags |= Document.FLAG_SUPPORTS_RENAME;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            flags |= Document.FLAG_SUPPORTS_MOVE;
            flags |= Document.FLAG_SUPPORTS_COPY;
            flags |= Document.FLAG_SUPPORTS_REMOVE;
        }

        final String displayName = file.getName();
        final String mimeType = getMimeType(file);
        if (mimeType.startsWith("image/")) flags |= Document.FLAG_SUPPORTS_THUMBNAIL;

        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, docId);
        row.add(Document.COLUMN_DISPLAY_NAME, displayName);
        row.add(Document.COLUMN_SIZE, file.length());
        row.add(Document.COLUMN_MIME_TYPE, mimeType);
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(Document.COLUMN_FLAGS, flags);
        row.add(Document.COLUMN_ICON, R.mipmap.ic_launcher);
    }

    private static String getMimeType(File file) {
        if (file.isDirectory()) {
            return Document.MIME_TYPE_DIR;
        } else {
            final String name = file.getName();
            final int lastDot = name.lastIndexOf('.');
            if (lastDot >= 0) {
                final String extension = name.substring(lastDot + 1);
                final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (mime != null) return mime;
            }
            return "application/octet-stream";
        }
    }

    @Override
    public ParcelFileDescriptor openDocument(final String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        final File file = getFileForDocId(documentId);
        final int accessMode = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(file, accessMode);
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(String documentId, Point sizeHint, CancellationSignal signal) throws FileNotFoundException {
        final File file = getFileForDocId(documentId);
        final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(pfd, 0, file.length());
    }

    @Override
    public String createDocument(final String documentId, final String mimeType, final String displayName) throws FileNotFoundException {
        String canonicalName = displayName.replaceAll("\\\\", "_");
        File file = new File(documentId, canonicalName);
        try {
            if (Document.MIME_TYPE_DIR.equals(mimeType)) {
                file.mkdirs();
            } else {
                file.createNewFile();
            }
            file.setWritable(true);
            file.setReadable(true);
            return getDocIdForFile(file);
        } catch (Exception e) {
            Log.v(TAG, "Failed to create document with id " + documentId);
            throw new FileNotFoundException("Failed to create document with id " + documentId);
        }
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        File file = getFileForDocId(documentId);
        deleteFileOrFolder(file);
        if (file.exists()) {
            throw new FileNotFoundException("Failed to delete document with id " + documentId);
        }
    }

    /*
     * This function requires "implementation 'commons-io:commons-io:2.6'" in build.gradle
     */
    private void deleteFileOrFolder(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) return;
        try {
            if (fileOrDirectory.isDirectory()) {
                FileUtils.deleteDirectory(fileOrDirectory);
            } else {
                if (!fileOrDirectory.delete()) {
                    Log.v(TAG, "Unable to delete " + (fileOrDirectory.isDirectory() ? "directory " : "file ") + fileOrDirectory.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            Log.v(TAG, "Exception in FileUtils.deleteDirectory(). " + e.toString() + " " + fileOrDirectory.getAbsolutePath());
        }
        if (!fileOrDirectory.exists()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                revokeDocumentPermission(getDocIdForFile(fileOrDirectory));
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void removeDocument(String documentId, String parentDocumentId) throws FileNotFoundException {
        File file = getFileForDocId(documentId);
        deleteDocument(file.getAbsolutePath());
    }

    /*
     * This function requires "implementation 'commons-io:commons-io:2.6'" in build.gradle
     */
    @Override
    public String copyDocument(String sourceDocumentId, String targetParentDocumentId) throws FileNotFoundException {
        File src = getFileForDocId(sourceDocumentId);
        File dst = getFileForDocId(targetParentDocumentId);
        if (src.isDirectory() && dst.isDirectory()) {
            try {
                FileUtils.copyDirectoryToDirectory(src, dst);
            } catch (Exception e) {
                throw new FileNotFoundException("Unable to copy " + sourceDocumentId + " to " + targetParentDocumentId);
            }
        } else if (src.isFile() && dst.isDirectory()) {
            try {
                FileUtils.copyFileToDirectory(src, dst);
            } catch (IOException e) {
                throw new FileNotFoundException("Unable to copy " + sourceDocumentId + " to " + targetParentDocumentId);
            }
        } else {
            return null;
        }
        return getDocIdForFile(dst);
    }

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public String moveDocument(String sourceDocumentId, String sourceParentDocumentId, String targetParentDocumentId) throws FileNotFoundException {
        try {
            String result = copyDocument(sourceDocumentId, targetParentDocumentId);
            if (result != null) {
                deleteDocument(sourceDocumentId);
                File existingFile = getFileForDocId(targetParentDocumentId);
                return getDocIdForFile(existingFile);
            }
        } catch (Exception e) {
            throw new FileNotFoundException("Unable to move " + sourceDocumentId + " to " + targetParentDocumentId);
        }
        return null;
    }

    @Override
    public String renameDocument(final String documentId, final String displayName) throws FileNotFoundException {
        File existingFile = getFileForDocId(documentId);
        if (!existingFile.exists()) {
            throw new FileNotFoundException(documentId + " does not exist");
        }
        String canonicalName = displayName.replaceAll("\\\\", "_");
        if (existingFile.getName().equals(canonicalName)) {
            return getDocIdForFile(existingFile);
        }
        File parentDirectory = existingFile.getParentFile();
        File newFile = new File(parentDirectory, canonicalName);
        boolean success = existingFile.renameTo(newFile);
        if (!success) {
            throw new FileNotFoundException("Unable to rename " + documentId + " to " + existingFile.getAbsolutePath());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            revokeDocumentPermission(documentId);
        }
        return getDocIdForFile(newFile);
    }

    @Override
    public Cursor querySearchDocuments(String rootId, String query, String[] projection)
            throws FileNotFoundException {
        Log.v(TAG, "querySearchDocuments");

        final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        final File parent = getFileForDocId(rootId);

        final LinkedList<File> pending = new LinkedList<>();

        pending.add(parent);

        while (!pending.isEmpty() && result.getCount() < MAX_SEARCH_RESULTS) {
            final File file = pending.removeFirst();
            if (file.isDirectory()) {
                Collections.addAll(pending, file.listFiles());
            } else {
                if (file.getName().toLowerCase().contains(query)) {
                    includeFile(result, null, file);
                }
            }
        }
        return result;
    }

    @Override
    public Cursor queryRecentDocuments(String rootId, String[] projection)
            throws FileNotFoundException {
        Log.v(TAG, "queryRecentDocuments");

        final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);

        final File parent = getFileForDocId(rootId);

        PriorityQueue<File> lastModifiedFiles = new PriorityQueue<>(5, new Comparator<File>() {
            public int compare(File i, File j) {
                return Long.compare(i.lastModified(), j.lastModified());
            }
        });

        final LinkedList<File> pending = new LinkedList<>();

        pending.add(parent);

        while (!pending.isEmpty()) {
            final File file = pending.removeFirst();
            if (file.isDirectory()) {
                Collections.addAll(pending, file.listFiles());
            } else {
                lastModifiedFiles.add(file);
            }
        }

        for (int i = 0; i < Math.min(MAX_LAST_MODIFIED + 1, lastModifiedFiles.size()); i++) {
            final File file = lastModifiedFiles.remove();
            includeFile(result, null, file);
        }
        return result;
    }

}


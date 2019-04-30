package com.droidvim.storage;

import android.content.SharedPreferences;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;

import jackpal.androidterm.R;

import android.annotation.TargetApi;
@TargetApi(Build.VERSION_CODES.KITKAT)

public class LocalStorageProvider extends DocumentsProvider {
    private static final String TAG = "LocalStorageProvider";
    private static String BASE_DEFAULT_DIR = "/data/data/com.droidvim/files/home";

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
        BASE_DEFAULT_DIR = getContext().getFilesDir().getPath();
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_ROOT_PROJECTION);
        MatrixCursor.RowBuilder row;
        String title = "DroidVim";
        if (getContext() != null) {
            title = getContext().getString(R.string.application_term_app);
        }

        row = result.newRow();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this.getContext());
        File homeDir = new File(pref.getString("home_path", BASE_DEFAULT_DIR));
        row.add(Root.COLUMN_ROOT_ID, getDocIdForFile(homeDir));
        row.add(Root.COLUMN_DOCUMENT_ID, getDocIdForFile(homeDir));
        row.add(Root.COLUMN_TITLE, title);
        row.add(Root.COLUMN_SUMMARY, getContext().getString(R.string.title_home_path_preference));
        row.add(Root.COLUMN_ICON, R.mipmap.ic_launcher);
        row.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_SEARCH);
        row.add(Root.COLUMN_MIME_TYPES, "*/*");
        row.add(Root.COLUMN_AVAILABLE_BYTES, homeDir.getFreeSpace());

        homeDir = Environment.getExternalStorageDirectory();
        if (TextUtils.equals(Environment.getExternalStorageState(), Environment.MEDIA_MOUNTED)) {
            row = result.newRow();
            row.add(Root.COLUMN_ROOT_ID, getDocIdForFile(homeDir));
            row.add(Root.COLUMN_DOCUMENT_ID, getDocIdForFile(homeDir));
            row.add(Root.COLUMN_TITLE, title);
            row.add(Root.COLUMN_SUMMARY, homeDir.getAbsolutePath());
            row.add(Root.COLUMN_ICON, R.drawable.ic_folder);
            row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_SEARCH);
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
            if (file.isDirectory() && file.canWrite()) flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
        } else if (file.canWrite()) {
            flags |= Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE;
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

        final boolean isWrite = mode.matches(".*w.*");
        if (isWrite) {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
        } else {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        }
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(String documentId, Point sizeHint, CancellationSignal signal) throws FileNotFoundException {
        final File file = getFileForDocId(documentId);
        final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(pfd, 0, file.length());
    }

    @Override
    public String createDocument(final String parentDocumentId, final String mimeType, final String displayName) throws FileNotFoundException {
        File file = new File(parentDocumentId, displayName);
        try {
            file.createNewFile();
            file.setWritable(true);
            file.setReadable(true);
            return getDocIdForFile(file);
        } catch (IOException e) {
            Log.v (TAG, "Failed to delete document with id " + parentDocumentId);
        }
        return null;
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        File file = getFileForDocId(documentId);
        if (!file.delete()) {
            throw new FileNotFoundException("Failed to delete document with id " + documentId);
        }
    }

    @Override
    public String renameDocument(final String documentId, final String displayName) throws FileNotFoundException {
        File existingFile = new File(documentId);
        if (!existingFile.exists()) {
            throw new FileNotFoundException(documentId + " does not exist");
        }
        if (existingFile.getName().equals(displayName)) {
            return null;
        }
        File parentDirectory = existingFile.getParentFile();
        File newFile = new File(parentDirectory, displayName);
        int conflictIndex = 1;
        while (newFile.exists()) {
            newFile = new File(parentDirectory, displayName + "_" + conflictIndex++);
        }
        boolean success = existingFile.renameTo(newFile);
        if (!success) {
            throw new FileNotFoundException("Unable to rename " + documentId + " to " + existingFile.getAbsolutePath());
        }
        return existingFile.getAbsolutePath();
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


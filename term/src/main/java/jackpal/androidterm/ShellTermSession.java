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

import android.os.Build;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jackpal.androidterm.compat.FileCompat;
import jackpal.androidterm.util.TermSettings;

/**
 * A terminal session, controlling the process attached to the session (usually
 * a shell). It keeps track of process PID and destroys it's process group
 * upon stopping.
 */
public class ShellTermSession extends GenericTermSession {
    private int mProcId;
    private Thread mWatcherThread;

    private String mInitialCommand;

    private static final int PROCESS_EXITED = 1;
    private Handler mMsgHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (!isRunning()) {
                return;
            }
            if (msg.what == PROCESS_EXITED) {
                onProcessExit((Integer) msg.obj);
            }
        }
    };

    public ShellTermSession(TermSettings settings, String initialCommand) throws IOException {
        super(ParcelFileDescriptor.open(new File("/dev/ptmx"), ParcelFileDescriptor.MODE_READ_WRITE),
                settings, false);

        initializeSession();

        setTermOut(new ParcelFileDescriptor.AutoCloseOutputStream(mTermFd));
        setTermIn(new ParcelFileDescriptor.AutoCloseInputStream(mTermFd));

        mInitialCommand = initialCommand;

        mWatcherThread = new Thread() {
            @Override
            public void run() {
                Log.i(TermDebug.LOG_TAG, "waiting for: " + mProcId);
                int result = TermExec.waitFor(mProcId);
                Log.i(TermDebug.LOG_TAG, "Subprocess exited: " + result);
                mMsgHandler.sendMessage(mMsgHandler.obtainMessage(PROCESS_EXITED, result));
            }
        };
        mWatcherThread.setName("Process watcher");
    }

    static private boolean mFirst = true;
    static private String mEnvInitialCommand = "";

    private void initializeSession() throws IOException {
        TermSettings settings = mSettings;

        String path = System.getenv("PATH");
        if (settings.doPathExtensions()) {
            String appendPath = settings.getAppendPath();
            if (appendPath != null && appendPath.length() > 0) {
                path = path + ":" + appendPath;
            }

            if (settings.allowPathPrepend()) {
                String prependPath = settings.getPrependPath();
                if (prependPath != null && prependPath.length() > 0) {
                    path = prependPath + ":" + path;
                }
            }
        }
        if (settings.verifyPath()) {
            path = checkPath(path);
        }

        int size = 9;
        String[] env = new String[size];
        env[0] = "PATH=" + path;
        env[1] = "HOME=" + TermService.getHOME();
        env[2] = "TMPDIR=" + TermService.getTMPDIR();
        env[3] = "APPBASE=" + TermService.getAPPBASE();
        env[4] = "APPFILES=" + TermService.getAPPFILES();
        env[5] = "APPEXTFILES=" + TermService.getAPPEXTFILES();
        env[6] = "INTERNAL_STORAGE=" + TermService.getEXTSTORAGE();
        env[7] = "TERM=" + settings.getTermType();
        env[8] = "COLORFGBG=" + settings.getCOLORFGBG();

        String[] envCmd = env;
        if (mFirst) {
            for (String str : env) {
                if (!"".equals(str)) mEnvInitialCommand += "export " + str + "\r";
            }
            envCmd = new String[1];
            envCmd[0] = "";
        }

        String shell = settings.getShell();
        mProcId = createSubprocess(shell, envCmd);
    }

    private String checkPath(String path) {
        String[] dirs = path.split(":");
        StringBuilder checkedPath = new StringBuilder(path.length());
        for (String dirname : dirs) {
            File dir = new File(dirname);
            if (dir.isDirectory() && FileCompat.canExecute(dir)) {
                checkedPath.append(dirname);
                checkedPath.append(":");
            }
        }
        return checkedPath.substring(0, checkedPath.length() - 1);
    }

    static private String mPostCmd = null;

    @Override
    public void initializeEmulator(int columns, int rows) {
        super.initializeEmulator(columns, rows);

        mWatcherThread.start();
        sendInitialCommand(mInitialCommand);
        if (mPostCmd != null) {
            sendInitialCommand(mPostCmd);
            mPostCmd = null;
        }
    }

    static public void setPostCmd(String cmd) {
        mPostCmd = cmd;
    }

    private void sendInitialCommand(final String initialCommand) {
        if (mFirst) {
            write(mEnvInitialCommand + '\r');
            mFirst = false;
        }
        sendCommand(getAdditionalEnv());
        sendCommand(getProotCommand());

        if (initialCommand != null && initialCommand.length() > 0) {
            write(initialCommand + '\r');
        }
    }

    static private String[] getAdditionalEnv() {
        List<String> AdditonalCommands = new ArrayList<>();
        String locale;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            LocaleList localeList = LocaleList.getDefault();
            locale = localeList.get(0).toLanguageTag();
        } else {
            locale = getprop("persist.sys.locale");
            if ("".equals(locale)) {
                String language = getprop("persist.sys.language");
                String country = getprop("persist.sys.country");
                if (!"".equals(language) && !"".equals(country)) {
                    locale = language + "_" + country;
                }
            }
        }
        if ("".equals(locale)) locale = "en-US";
        locale = locale.replace("-", "_");
        AdditonalCommands.add("export LANG=" + locale + ".UTF-8");
        return AdditonalCommands.toArray(new String[0]);
    }

    static private String getprop(String propName) {
        String GETPROP_EXECUTABLE_PATH = "/system/bin/getprop";
        String TAG = "getprop";

        Process process = null;
        BufferedReader bufferedReader = null;

        try {
            process = new ProcessBuilder().command(GETPROP_EXECUTABLE_PATH, propName).redirectErrorStream(true).start();
            bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = bufferedReader.readLine();
            if (line == null) {
                line = "";
            }
            Log.i(TAG, "read System Property: " + propName + "=" + line);
            return line;
        } catch (Exception e) {
            Log.e(TAG, "Failed to read System Property " + propName, e);
            return "";
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    // Do nothing
                }
            }
            if (process != null) {
                process.destroy();
            }
        }
    }

    static public String[] getProotCommand() {
        return getProotCommand(new String[]{});
    }

    static String[] getProotCommand(String... commands) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) return new String[]{};
        String appLib = TermService.getAPPLIB();
        List<String> prootCommands = new ArrayList<>();
        prootCommands.add("export APPLIB=" + appLib);
        if (!new File(appLib + "/libproot.so").canExecute()) return prootCommands.toArray(new String[0]);

        prootCommands.add("export PROOT_TMP_DIR=" + TermService.getTMPDIR());
        prootCommands.add("export PROOT_LOADER=$APPLIB/libloader.so");
        if (new File(appLib + "/libloader-m32.so").canExecute()) {
            prootCommands.add("export PROOT_LOADER_32=$APPLIB/libloader-m32.so");
        }
        prootCommands.add("$APPLIB/libproot.so /system/bin/sh");
        if (commands != null && !Arrays.equals(commands, new String[]{})) {
            prootCommands.addAll(Arrays.asList(commands));
        }
        return prootCommands.toArray(new String[0]);
    }

    private void sendCommand(String[] commands) {
        if (Arrays.equals(commands, new String[]{})) return;
        if (commands == null) return;
        for (String cmd : commands) {
            write(cmd + '\r');
        }
    }

    private int createSubprocess(String shell, String[] env) throws IOException {
        if (shell == null || "system".equals(shell) || "".equals(shell) || !shell.matches("/.*")) {
            shell = "/system/bin/sh -";
        }

        ArrayList<String> argList = parse(shell);
        String arg0;
        String[] args;

        try {
            arg0 = argList.get(0);
            File file = new File(arg0);
            if (!file.exists()) {
                Log.e(TermDebug.LOG_TAG, "Shell " + arg0 + " not found!");
                throw new FileNotFoundException(arg0);
            } else if (!FileCompat.canExecute(file)) {
                Log.e(TermDebug.LOG_TAG, "Shell " + arg0 + " not executable!");
                throw new FileNotFoundException(arg0);
            }
            args = argList.toArray(new String[1]);
        } catch (Exception e) {
            argList = parse(mSettings.getFailsafeShell());
            arg0 = argList.get(0);
            args = argList.toArray(new String[1]);
        }

        return TermExec.createSubprocess(mTermFd, arg0, args, env);
    }

    private ArrayList<String> parse(String cmd) {
        final int PLAIN = 0;
        final int WHITESPACE = 1;
        final int INQUOTE = 2;
        int state = WHITESPACE;
        ArrayList<String> result = new ArrayList<String>();
        int cmdLen = cmd.length();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cmdLen; i++) {
            char c = cmd.charAt(i);
            if (state == PLAIN) {
                if (Character.isWhitespace(c)) {
                    result.add(builder.toString());
                    builder.delete(0, builder.length());
                    state = WHITESPACE;
                } else if (c == '"') {
                    state = INQUOTE;
                } else {
                    builder.append(c);
                }
            } else if (state == WHITESPACE) {
                if (Character.isWhitespace(c)) {
                    // do nothing
                } else if (c == '"') {
                    state = INQUOTE;
                } else {
                    state = PLAIN;
                    builder.append(c);
                }
            } else if (state == INQUOTE) {
                if (c == '\\') {
                    if (i + 1 < cmdLen) {
                        i += 1;
                        builder.append(cmd.charAt(i));
                    }
                } else if (c == '"') {
                    state = PLAIN;
                } else {
                    builder.append(c);
                }
            }
        }
        if (builder.length() > 0) {
            result.add(builder.toString());
        }
        return result;
    }

    private void onProcessExit(int result) {
        onProcessExit();
    }

    @Override
    public void finish() {
        hangupProcessGroup();
        super.finish();
    }

    /**
     * Send SIGHUP to a process group, SIGHUP notifies a terminal client, that the terminal have been disconnected,
     * and usually results in client's death, unless it's process is a daemon or have been somehow else detached
     * from the terminal (for example, by the "nohup" utility).
     */
    void hangupProcessGroup() {
        TermExec.sendSignal(-mProcId, 1);
    }
}

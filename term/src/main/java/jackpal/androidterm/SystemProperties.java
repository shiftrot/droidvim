package jackpal.androidterm;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SystemProperties {
    public static String getprop(String propName) {
        final String GETPROP_EXECUTABLE_PATH = "/system/bin/getprop";
        final String TAG = "GETPROP";
        Process process = null;
        BufferedReader bufferedReader = null;

        try {
            process = new ProcessBuilder().command(GETPROP_EXECUTABLE_PATH, propName).redirectErrorStream(true).start();
            bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = bufferedReader.readLine();
            return (line != null) ? line : "";
        } catch (Exception e) {
            Log.e(TAG,"Failed to read System Property " + propName,e);
            return "";
        } finally {
            try {
                if (bufferedReader != null) bufferedReader.close();
            } catch (Exception e) {
                // Do nothing
            }
            if (process != null) process.destroy();
        }
    }
}

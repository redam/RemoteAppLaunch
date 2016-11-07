package fr.damongeot.remoteapplaunch;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * helper methods.
 */
public class NetworkListenService extends IntentService {
    private final static String TAG = "NetworkListenService";

    private int mPort;
    private ServerSocket mServerSocket;
    private boolean mIsRunning; //server is running
    private SharedPreferences mSP;

    public NetworkListenService() {
        super("NetworkListenService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            mPort = intent.getIntExtra(MainActivity.LISTENING_PORT,MainActivity.LISTENING_PORT_DEF);
            mIsRunning = true;
            startServer();
        }
    }

    /**
     * Listen on network and serve requests
     */
    private void startServer() {
        try {
            mServerSocket = new ServerSocket(mPort);
            while (mIsRunning) {
                Log.d(TAG,"Listening on port "+mPort);
                Socket socket = mServerSocket.accept();
                handleRequest(socket);
                socket.close();
            }
        } catch (SocketException e) {
            // The server was stopped; ignore.
            Log.d(TAG,e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "Web server error.", e);
        }
    }

    /**
     * Respond to a request from a client.
     *
     * @param socket The client socket.
     * @throws IOException
     */
    private void handleRequest(Socket socket) throws IOException {
        BufferedReader reader = null;
        PrintStream output = null;
        boolean foundGetRequest = false;
        try {
            String route = null;

            // Read HTTP headers and parse out the route.
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;

            Pattern p = Pattern.compile("GET /([^ ]*).*");

            while (!TextUtils.isEmpty(line = reader.readLine())) {
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    Log.d(TAG,m.group(1));
                    foundGetRequest = true;
                    launchApp(m.group(1));
                    break;
                } else {
                    //Log.d(TAG,"Unknow line : "+line);
                }
            }

            // Output stream that we send the response to
            output = new PrintStream(socket.getOutputStream());

            // Prepare the content to send.
            if (! foundGetRequest) {
                output.println("HTTP/1.0 404 Not Found");
                output.flush();
            } else {
                // Send out the content.
                output.println("HTTP/1.0 200 No Content");
                output.flush();
            }
        } finally {
            if (null != output) {
                output.close();
            }
            if (null != reader) {
                reader.close();
            }
        }
    }

    /**
     * Start app from package name if user has added it to launchable app list
     */
    private void launchApp(String packageName) {
        mSP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        for(String pkgName : mSP.getStringSet(MainActivity.APP_LIST,new HashSet<String>(0))) {
            if(packageName.equals(pkgName)) {
                //launch app
                Log.d(TAG,"Starting app " + packageName);
                Intent intent = getPackageManager().getLaunchIntentForPackage(pkgName);
                startActivity(intent);
                return;
            }
        }

        Log.w(TAG,"App "+packageName+" is not authorized to be started remotly");
    }
}

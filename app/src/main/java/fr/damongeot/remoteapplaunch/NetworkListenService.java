package fr.damongeot.remoteapplaunch;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
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
import android.util.Base64;
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
    private final static int ACTION_START = 1, ACTION_STOP = 2;

    private int mPort;
    private ServerSocket mServerSocket;
    private boolean mIsRunning; //server is running
    private SharedPreferences mSP;
    private boolean httpAuth; //is http authentication enabled ?
    private String httpUsername,httpPassword;
    private ActivityManager am;

    public NetworkListenService() {
        super("NetworkListenService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            mSP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            httpAuth = mSP.getBoolean(MainActivity.HTTP_AUTHENTICATION,MainActivity.HTTP_AUTHENTICATION_DEF);
            httpUsername = mSP.getString(MainActivity.HTTP_AUTHENTICATION_USER,MainActivity.HTTP_AUTHENTICATION_USER_DEF);
            httpPassword = mSP.getString(MainActivity.HTTP_AUTHENTICATION_PASSWORD,MainActivity.HTTP_AUTHENTICATION_PASSWORD_DEF);
            mPort = intent.getIntExtra(MainActivity.LISTENING_PORT,MainActivity.LISTENING_PORT_DEF);
            mIsRunning = true;
            am = (ActivityManager) getBaseContext().getSystemService(Context.ACTIVITY_SERVICE);
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
        String packageName = null;
        boolean foundAuthHeader = false;
        boolean authSucceed = false;
        int action = ACTION_START;
        String outputMessage = "";

        try {
            // Read HTTP headers
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;

            Pattern pGet = Pattern.compile("GET /(start|stop)/([^ ]*).*"); //match GET request
            Pattern pAuth = Pattern.compile("Authorization: Basic (.*)"); //match Authorization header

            while (!TextUtils.isEmpty(line = reader.readLine())) {
                Matcher m = pAuth.matcher(line);
                if(m.matches()) {
                    //Log.d(TAG,"found authorization header : "+line);
                    authSucceed = checkAuth(m.group(1));
                } else {
                    m = pGet.matcher(line);
                    if (m.matches()) {
                        Log.d(TAG, m.group(2));
                        packageName = m.group(2);
                        action = m.group(1).equals("start") ? ACTION_START:ACTION_STOP;
                    } else {
                        //Log.d(TAG,"Unknow line : "+line);
                    }
                }
            }

            // GET header arrives before authorization so we cant tread it in while loop
            if(packageName!=null) {
                if (!httpAuth || authSucceed) {
                    switch (action) {
                        case ACTION_START:
                            try {
                                launchApp(packageName);
                                outputMessage = "App launched";
                            } catch (Exception e) {
                                outputMessage = e.getMessage();
                            }
                            break;
                        case ACTION_STOP: killPackageProcesses(packageName);
                            break;
                    }

                }
            }

            // Output stream that we send the response to
            output = new PrintStream(socket.getOutputStream());

            // Prepare the content to send.
            if (packageName == null) {
                output.println("HTTP/1.0 404 Not Found");
                output.flush();
            } else {
                // Send out the content.
                //if no authorization sent while http auth enabled, send auth headers
                if(httpAuth && ! authSucceed) {
                    output.println("HTTP/1.0 401 Unauthorized");
                    output.println("WWW-Authenticate: Basic realm=\"RemoteAppLaunch\"");
                } else {
                    output.println("HTTP/1.0 200 OK");
                    output.println("");
                    output.println(outputMessage);
                }
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
     * Check if username/password is matching from HTTP Basic header (user:password encoded in base64)
     * @param authHeader
     * @return
     */
    private boolean checkAuth(String authHeader) {
        String userpass = new String(Base64.decode(authHeader.getBytes(),Base64.DEFAULT));
        //Log.d(TAG,"HTTP Request contains authorization header : "+userpass);
        if(userpass.equals(httpUsername+":"+httpPassword)) {
            return true;
        } else {
            Log.w(TAG,"User/password did not match : "+userpass);
            return false;
        }
    }

    /**
     * Start app from package name if user has added it to launchable app list
     */
    private void launchApp(String packageName) throws Exception {
        for(String pkgName : mSP.getStringSet(MainActivity.APP_LIST,new HashSet<String>(0))) {
            if(packageName.equals(pkgName)) {
                //launch app
                Log.d(TAG,"Starting app " + packageName);
                Intent intent = getPackageManager().getLaunchIntentForPackage(pkgName);
                startActivity(intent);
                return;
            }
        }

        throw new Exception("App "+packageName+" is not authorized to be started remotely (or is an invalid package name)");
    }

    public int findPIDbyPackageName(String packagename) {
        int result = -1;

        if (am != null) {
            for (ActivityManager.RunningAppProcessInfo pi : am.getRunningAppProcesses()){
                if (pi.processName.equalsIgnoreCase(packagename)) {
                    result = pi.pid;
                }
                if (result != -1) break;
            }
        } else {
            result = -1;
        }

        return result;
    }

    public boolean isPackageRunning(String packagename) {
        return findPIDbyPackageName(packagename) != -1;
    }

    /**
     * Kill processes from a given package name
     * @param packagename
     * @return
     */
    public boolean killPackageProcesses(String packagename) {
        boolean result = false;

        if (am != null) {
            am.killBackgroundProcesses(packagename);
            result = !isPackageRunning(packagename);
        } else {
            result = false;
        }

        return result;
    }
}

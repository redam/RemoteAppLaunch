package fr.damongeot.remoteapplaunch;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

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
    private boolean isFlashOn = false;
    private Camera mCamera;
    private CameraManager mCamManager;
    private Camera.Parameters params;

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
            while (!TextUtils.isEmpty(line = reader.readLine())) {
                if (line.startsWith("GET /on")) {
                    foundGetRequest = true;
                    Log.d(TAG,"GET /on requests, setting flash light to ON");
                    setFlash(true);
                } else if(line.startsWith("GET /off")) {
                    foundGetRequest = true;
                    Log.d(TAG,"GET /off requests, setting flash light to OFF");
                    setFlash(false);
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
     * Turn on or off flash
     * @param state true = flash on, false = flash off
     */
    @SuppressLint("NewApi")
    private void setFlash(boolean state) {
        if(isFlashOn == state) return;

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //use camera1 API before LOLLIPOP

            //get camera parameters if not already done
            if (mCamera == null) {
                try {
                    mCamera = Camera.open();
                    params = mCamera.getParameters();
                } catch (RuntimeException e) {
                    Log.d(TAG, e.getMessage());
                    return;
                }
            }

            if (state) {
                params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                mCamera.setParameters(params);
                mCamera.startPreview();
                Log.d(TAG, "setFlash() : flash on");
            } else {
                params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mCamera.setParameters(params);
                mCamera.stopPreview();
                Log.d(TAG, "setFlash() : flash on");
            }
        } else {
            //use camera2 api for LOLLIPOP and UP

            CameraManager mCamManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String cameraId = null; // Usually front camera is at 0 position.
            try {
                cameraId = mCamManager.getCameraIdList()[0];
                mCamManager.setTorchMode(cameraId, state);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        isFlashOn = state;
    }
}

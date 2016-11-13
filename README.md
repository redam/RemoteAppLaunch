# RemoteAppLaunch
Launch apps on your android phone remotely using HTTP GET requests.

For example, to launch firefox, use your browser to request :
```
http://yourPhoneIP:8888/start/org.mozilla.firefox
```

The app need to be requested by its package name (RemoteAppLaunch will show the package name of each app on your phone, don't worry).

To stop the app remotely, you just need to switch from "start" to "stop" in the URL :
```
http://yourPhoneIP:8888/stop/org.mozilla.firefox
```

Each app you want to start remotely need to be added in a white list within RemoteAppLaunch (for security reasons). You can add one more security layer by using HTTP Authentication with a user/password needed to start/stop apps. In that case, to launch an app just use the following URL :
```
http://username:password@yourPhoneIP:8888/start/org.mozilla.firefox
```

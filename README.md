# RemoteAppLaunch
Launch app on you android remotely using HTTP GET requests.

For example, to launch firefox, use your browser to request :
http://yourPhoneIP:8888/start/org.mozilla.firefox

The app need to be requested by its package name (but RemoteAppLaunch will show it to you if you don't know the package name of the app).

To stop the app remotely, you just need to switch from start to stop in the URL :
http://yourPhoneIP:8888/stop/org.mozilla.firefox

Each app you want to start remotely need to be added in a list within RemoteAppLaunch (for security reasons). You can add one more security layer by using HTTP Authentication with a user/password needed to start/stop apps. In that case, to launch an app just use the following URL :
http://username:password@yourPhoneIP:8888/start/org.mozilla.firefox

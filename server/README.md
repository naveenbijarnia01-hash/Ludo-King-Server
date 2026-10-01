# Ludo multiplayer server

## Run
1. Install Node.js 18+
2. `cd server`
3. `npm install`
4. `npm start`

The Android app currently uses `http://10.0.2.2:3000` for the Android Emulator. For a real phone, change `SERVER_URL` in `OnlineLobbyActivity.java` to the PC/server LAN IP (for example `http://192.168.1.10:3000`) or your public HTTPS/WSS endpoint.

This prototype intentionally trusts the client dice value, as requested.

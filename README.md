# Dashcam App

A complete solution for recording dashcam videos on Android and uploading them to a Node.js backend server with a modern web gallery.

---

## Features
- **Android App**: Record dashcam videos manually or automatically on sudden motion.
- **Automatic Upload**: Videos are uploaded to the backend server when recording stops.
- **Node.js Backend**: Receives and stores uploaded videos.
- **Web Gallery**: View and download all uploaded videos in a modern, responsive gallery.

---

## Project Structure
```
├── app/                 # Android app source code
├── server.js            # Node.js backend server
├── package.json         # Backend dependencies
├── uploads/             # Folder where uploaded videos are stored
└── README.md            # Project documentation
```

---

## Getting Started

### 1. Clone the Repository
```sh
git clone https://github.com/PallempatiRishi/dashcamapp.git
cd dashcamapp
```

### 2. Backend Setup
```sh
npm install
npm start
```
- The server will run at `http://localhost:3000`.
- Uploaded videos are saved in the `uploads/` folder.
- View uploaded videos at [http://localhost:3000/videos](http://localhost:3000/videos).

### 3. Android App Setup
- Open the `app/` folder in Android Studio.
- Update the upload URL in `MainActivity.java` to match your server's IP (e.g., `http://<your-ip>:3000/upload`).
- Build and run the app on your Android device.
- Grant all requested permissions.

### 4. Usage
- **Record a video** using the app (manually or via motion detection).
- **Stop recording** to trigger automatic upload to the backend.
- **View your videos** in the web gallery at `/videos`.

---

## Requirements
- Node.js (v14+ recommended)
- npm
- Android Studio
- Android device (API 24+)

---

## Customization
- To remove the manual upload web page, delete `public/index.html` and the related static middleware in `server.js`.
- To change the upload folder, edit the `uploadDir` variable in `server.js`.

---

## License
MIT 

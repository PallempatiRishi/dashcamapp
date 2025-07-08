const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const app = express();
const PORT = 3000;

// Ensure uploads directory exists
const uploadDir = path.join(__dirname, 'uploads');
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir);
}

const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    cb(null, uploadDir);
  },
  filename: function (req, file, cb) {
    cb(null, Date.now() + '-' + file.originalname);
  }
});
const upload = multer({ storage: storage });

app.use(express.static('public'));

app.post('/upload', upload.single('video'), (req, res) => {
  if (!req.file) {
    return res.status(400).send('No file uploaded.');
  }
  res.send('File uploaded successfully: ' + req.file.filename);
});

app.get('/videos', (req, res) => {
  fs.readdir(uploadDir, (err, files) => {
    if (err) {
      return res.status(500).send('Unable to list videos');
    }
    // Filter for mp4 files
    const videoFiles = files.filter(f => f.endsWith('.mp4'));
    let html = `
    <html>
    <head>
      <title>Uploaded Dashcam Videos</title>
      <style>
        body {
          background: #181c20;
          color: #f5f6fa;
          font-family: 'Segoe UI', Arial, sans-serif;
          margin: 0;
          padding: 0;
        }
        .container {
          max-width: 900px;
          margin: 40px auto;
          padding: 24px;
          background: #23272b;
          border-radius: 16px;
          box-shadow: 0 4px 24px rgba(0,0,0,0.3);
        }
        h1 {
          text-align: center;
          margin-bottom: 32px;
          font-size: 2.5rem;
          letter-spacing: 1px;
        }
        .gallery {
          display: flex;
          flex-wrap: wrap;
          gap: 32px;
          justify-content: center;
        }
        .video-card {
          background: #2d3238;
          border-radius: 12px;
          box-shadow: 0 2px 8px rgba(0,0,0,0.2);
          padding: 16px;
          width: 320px;
          display: flex;
          flex-direction: column;
          align-items: center;
        }
        video {
          border-radius: 8px;
          width: 100%;
          margin-bottom: 8px;
        }
        .filename {
          font-size: 1rem;
          color: #b2becd;
          word-break: break-all;
          margin-bottom: 4px;
        }
        .download-link {
          color: #00b894;
          text-decoration: none;
          font-weight: bold;
          margin-top: 4px;
        }
        .download-link:hover {
          text-decoration: underline;
        }
      </style>
    </head>
    <body>
      <div class="container">
        <h1>Uploaded Dashcam Videos</h1>
        <div class="gallery">
    `;
    if (videoFiles.length === 0) {
      html += '<p>No videos uploaded yet.</p>';
    } else {
      videoFiles.forEach(file => {
        html += `
          <div class="video-card">
            <video controls src="/uploads/${file}"></video>
            <div class="filename">${file}</div>
            <a class="download-link" href="/uploads/${file}" download>Download</a>
          </div>
        `;
      });
    }
    html += `</div></div></body></html>`;
    res.send(html);
  });
});

app.use('/uploads', express.static(uploadDir));

app.listen(PORT, () => {
  console.log(`Server running on http://localhost:${PORT}`);
}); 
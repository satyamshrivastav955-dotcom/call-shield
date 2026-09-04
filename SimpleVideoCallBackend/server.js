const WebSocket = require('ws');
const http = require('http');
const ConnectionHandler = require('./connectionHandler');  // Import the ConnectionHandler class

// Data structure to hold all connected clients by username
const clients = {};

// Create an HTTP server
const server = http.createServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('WebSocket Server is running');
});

// Create a WebSocket server attached to the HTTP server
const wss = new WebSocket.Server({ server, handleProtocols: (protocols, req) => '*' });

// WebSocket connection event
wss.on('connection', (ws, req) => {
    // Extract username from the query parameters
    const urlParams = new URLSearchParams(req.url.split('?')[1]);
    const username = urlParams.get('username');
    const token = urlParams.get('token');

    if (username) {
        // Create a new connection handler for this connection
        console.log("user connected ",username, token)
        ConnectionHandler.handleConnection(token,username,ws)
        ws.send(JSON.stringify({type:"welcome"}))
    } else {
        // If no username is provided in query params, close the connection
        ws.close();
    }
});

// Start the HTTP server on port 8080
server.listen(3007, () => {
    console.log('Server running on http://localhost:8080');
});

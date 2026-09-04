const usersList = {}                 // username -> websocket
const userTokens = {}               // username -> FCM token (only if firebase is configured)

// ---------------------------------------------------------------------------
// FCM is OPTIONAL. The JSON credential file may not exist in a local dev
// checkout; if it does not, we simply skip firebase-admin and offline-call
// notifications fall back to a plain UserOffline reply.
// ---------------------------------------------------------------------------
let admin = null;
let fcmAvailable = false;
const fs = require('fs');
const path = require('path');

const CRED_FILE = path.join(__dirname, 'call-notificaton-firebase-adminsdk-fbsvc-822eb0a964.json');
if (fs.existsSync(CRED_FILE)) {
    try {
        admin = require('firebase-admin');
        const serviceAccount = require(CRED_FILE);
        admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
        fcmAvailable = true;
        console.log('Firebase Admin initialised (FCM notifications enabled)');
    } catch (e) {
        console.warn('Firebase init failed, FCM disabled:', e.message);
    }
} else {
    console.log('FCM credentials not found, offline-call notifications disabled');
}

class ConnectionHandler {
    static handleConnection(token, username, socket) {
        // Save the user’s FCM token
        userTokens[username] = token;

        // Store the websocket reference
        usersList[username] = socket;

        socket.on('close', () => {
            delete usersList[username];
            // Optional: If you want to delete the token on disconnect
            // delete userTokens[username];
            console.log(`${username} disconnected`);
        });

        socket.on('message', async (message) => {
            let normalizedMessage = JSON.parse(message);
            console.log("Received:", normalizedMessage.type);

            switch (normalizedMessage.type) {
                case SignalTypes().findUser:
                    await handleFindUser(normalizedMessage, socket);
                    break;

                default:
                    forwardMessage(normalizedMessage, socket);
                    break;
            }
        });
    }}

// ----------------------
// SEND FCM NOTIFICATION
// ----------------------

/**
 * message = {
 *   type: "SendCallNotification",
 *   from: "Alice",
 *   target: "Bob",
 *   callId: "12345"
 * }
 */


const handleFindUser = async (message, socket) => {
    const targetUser = message.target;

    if (usersList[targetUser]) {  // Check if the user is online
        // If the user is online, send a "userOnline" response
        const successMessage = { ...message, type: SignalTypes().userOnline };
        sendMessageToClient(successMessage, socket);
    } else {  // User is offline
        console.log(`User ${targetUser} is offline, attempting to send call notification...`);

        // Try sending the call notification to their FCM token
        if (!fcmAvailable || !userTokens[targetUser]) {
            console.log("Target user has no FCM token:", targetUser);
            // If no FCM token is available, just send a "userOffline" response
            const failureMessage = { ...message, type: SignalTypes().userOffline };
            sendMessageToClient(failureMessage, socket);
            return;
        }

        const targetToken = userTokens[targetUser];
        const payload = {
            token: targetToken,
            data: message,
            android: {
                priority: "high"
            }
        };

        try {
            const result = await admin.messaging().send(payload);  // Send FCM message
            console.log("FCM sent:", result);

            // If FCM was successful, treat as if the user is online and send a "userOnline" response
            const successMessage = { ...message, type: SignalTypes().userOfflineWithNotification };
            sendMessageToClient(successMessage, socket);
        } catch (err) {
            console.error("FCM ERROR:", err);

            // If FCM fails, send the "userOffline" response
            const failureMessage = { ...message, type: SignalTypes().userOffline };
            sendMessageToClient(failureMessage, socket);
        }
    }
};

const forwardMessage = (message, socket) => {
    let userToFind = message.target
    if (userToFind && usersList[userToFind]) {
        let socketToSend = usersList[userToFind]
        sendMessageToClient(message, socketToSend)
    } else {
        const failureMessage = message
        failureMessage.type = SignalTypes().userOffline
        sendMessageToClient(failureMessage, socket)
    }
}
const sendMessageToClient = (message, socket) => {
    socket.send(JSON.stringify(message))
}

const SignalTypes = () => {
    return {
        findUser: "FindUser",
        userOnline: "UserOnline",
        userOffline: "UserOffline",
        userOfflineWithNotification: "UserOfflineWithNotification",
    }
}

module.exports = ConnectionHandler
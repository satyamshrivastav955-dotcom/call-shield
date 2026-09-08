/**
 * antAI WebRTC Signaling Server - Cloudflare Worker
 * 
 * Replaces or mirrors the Node.js ws signaling server (port 3007).
 * Uses Cloudflare Durable Objects to maintain shared state across global edge locations,
 * enabling WebRTC peers anywhere in the world to discover each other and exchange
 * SDP offers, answers, and ICE candidates in real time.
 */

export class SignalingRoom {
    constructor(state, env) {
        this.state = state;
        this.env = env;
        // In-memory active connections within the Durable Object
        this.users = new Map();       // username -> WebSocket
        this.wsToUser = new Map();    // WebSocket -> username
        this.userTokens = new Map();  // username -> token (e.g. FCM token)
    }

    async fetch(request) {
        const url = new URL(request.url);

        // HTTP health/status check
        if (request.headers.get("Upgrade") !== "websocket") {
            return new Response(JSON.stringify({
                status: "ok",
                service: "antAI WebRTC Signaling Server (Cloudflare Worker)",
                onlineUsers: Array.from(this.users.keys()),
                timestamp: new Date().toISOString()
            }, null, 2), {
                headers: {
                    "Content-Type": "application/json",
                    "Access-Control-Allow-Origin": "*"
                }
            });
        }

        // WebSocket connection upgrade
        const username = url.searchParams.get("username");
        const token = url.searchParams.get("token") || "";

        if (!username) {
            return new Response("Missing 'username' query parameter", { status: 400 });
        }

        const pair = new WebSocketPair();
        const [clientWs, serverWs] = Object.values(pair);

        // Accept WebSocket connection in the Durable Object
        serverWs.accept();

        // If user is already connected on an old socket, clean up the old one
        if (this.users.has(username)) {
            try {
                const oldWs = this.users.get(username);
                oldWs.close(1000, "Replaced by new connection");
            } catch (_) {}
        }

        this.users.set(username, serverWs);
        this.wsToUser.set(serverWs, username);
        if (token) {
            this.userTokens.set(username, token);
        }

        console.log(`[SignalingRoom] User connected: ${username}`);

        // Listen for messages on this WebSocket
        serverWs.addEventListener("message", (event) => {
            try {
                const message = JSON.parse(event.data);
                this.handleSignalMessage(username, serverWs, message);
            } catch (err) {
                console.error(`[SignalingRoom] Bad message from ${username}:`, err.message);
            }
        });

        const cleanup = () => {
            if (this.users.get(username) === serverWs) {
                this.users.delete(username);
                this.wsToUser.delete(serverWs);
                console.log(`[SignalingRoom] User disconnected: ${username}`);
            }
        };

        serverWs.addEventListener("close", cleanup);
        serverWs.addEventListener("error", cleanup);

        // Handshake: Send welcome frame (matches Node.js backend behavior)
        serverWs.send(JSON.stringify({ type: "welcome" }));

        return new Response(null, {
            status: 101,
            webSocket: clientWs
        });
    }

    handleSignalMessage(sender, socket, message) {
        switch (message.type) {
            case "FindUser":
                this.handleFindUser(message, socket);
                break;
            default:
                this.forwardMessage(message, socket);
                break;
        }
    }

    handleFindUser(message, socket) {
        const targetUser = message.target;
        if (targetUser && this.users.has(targetUser)) {
            // Target is online
            const response = { ...message, type: "UserOnline" };
            socket.send(JSON.stringify(response));
        } else {
            // Target is offline
            console.log(`[SignalingRoom] FindUser: ${targetUser} is offline`);
            const response = { ...message, type: "UserOffline" };
            socket.send(JSON.stringify(response));
        }
    }

    forwardMessage(message, socket) {
        const targetUser = message.target;
        if (targetUser && this.users.has(targetUser)) {
            const targetWs = this.users.get(targetUser);
            targetWs.send(JSON.stringify(message));
        } else {
            const failureResponse = { ...message, type: "UserOffline" };
            socket.send(JSON.stringify(failureResponse));
        }
    }
}

export default {
    async fetch(request, env, ctx) {
        // Route all signaling WebSocket traffic to the global Durable Object instance
        const id = env.SIGNALING_ROOM.idFromName("global");
        const stub = env.SIGNALING_ROOM.get(id);
        return stub.fetch(request);
    }
};

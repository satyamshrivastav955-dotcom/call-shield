// Quick smoke test for the signaling server: two clients, FindUser + message relay.
const WebSocket = require('ws');

const URL = 'ws://127.0.0.1:3007/';

function client(name, token = 'tok-' + name) {
    const ws = new WebSocket(URL + '?username=' + name + '&token=' + token);
    const received = [];
    ws.on('open', () => console.log(`[${name}] opened`));
    ws.on('message', (m) => {
        const msg = JSON.parse(m.toString());
        received.push(msg.type);
        console.log(`[${name}] <- ${msg.type}`);
        if (msg.type === 'welcome' && name === 'alice') {
            ws.send(JSON.stringify({ type: 'FindUser', sender: 'alice', target: 'bob' }));
        }
        if (msg.type === 'UserOnline' && name === 'alice') {
            ws.send(JSON.stringify({ type: 'HelloBob', sender: 'alice', target: 'bob', data: 'ping' }));
        }
        if (msg.type === 'HelloBob' && name === 'bob') {
            console.log('RELAY OK - message forwarded between peers');
            process.exit(0);
        }
    });
    ws.on('error', (e) => { console.error(`[${name}] error`, e.message); process.exit(1); });
}

client('alice');
client('bob');
setTimeout(() => { console.error('TIMEOUT - relay failed'); process.exit(1); }, 8000);
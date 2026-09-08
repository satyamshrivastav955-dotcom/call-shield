// Quick smoke test for the signaling server: two clients, FindUser + message relay.
const WebSocketClient = typeof WebSocket !== 'undefined' ? WebSocket : require('ws');

let defaultPort = '3007';
const portArg = process.argv.find(arg => arg.startsWith('--port=') || (process.argv[process.argv.indexOf(arg) - 1] === '--port'));
if (portArg) {
    defaultPort = portArg.includes('=') ? portArg.split('=')[1] : portArg;
} else if (process.argv[2] && !process.argv[2].startsWith('--')) {
    defaultPort = process.argv[2];
}
const URL = process.env.SIGNALING_URL || (defaultPort.startsWith('ws') ? defaultPort : `ws://127.0.0.1:${defaultPort}/`);
console.log(`Connecting smoke test to: ${URL}`);

function client(name, token = 'tok-' + name) {
    const ws = new WebSocketClient(URL + '?username=' + name + '&token=' + token);
    const onOpen = () => console.log(`[${name}] opened`);
    const onMessage = (event) => {
        const raw = event.data !== undefined ? event.data : event;
        const msg = JSON.parse(raw.toString());
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
    };
    const onError = (e) => {
        console.error(`[${name}] error`, e.message || e);
        process.exit(1);
    };

    if (typeof ws.addEventListener === 'function') {
        ws.addEventListener('open', onOpen);
        ws.addEventListener('message', onMessage);
        ws.addEventListener('error', onError);
    } else {
        ws.on('open', onOpen);
        ws.on('message', onMessage);
        ws.on('error', onError);
    }
}

client('alice');
client('bob');
setTimeout(() => { console.error('TIMEOUT - relay failed'); process.exit(1); }, 8000);
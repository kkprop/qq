// Simple WebSocket client test
const WebSocket = require('ws');

console.log('🔌 Connecting to WebSocket server...');
const ws = new WebSocket('ws://localhost:9091/terminal/test');

ws.on('open', function() {
    console.log('✅ WebSocket connected');
    
    // Send a test message
    const message = {
        type: 'command',
        content: 'What is 2 + 2?',
        session: 'qq-default'
    };
    
    console.log('📤 Sending message:', message);
    ws.send(JSON.stringify(message));
});

ws.on('message', function(data) {
    console.log('📨 Received:', data.toString());
});

ws.on('error', function(error) {
    console.log('❌ Error:', error);
});

ws.on('close', function() {
    console.log('🔌 Connection closed');
});

// Keep alive for 10 seconds
setTimeout(() => {
    ws.close();
    process.exit(0);
}, 10000);

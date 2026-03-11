const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const app = express();
app.use(cors());

const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: "*", // allow all in dev
        methods: ["GET", "POST"]
    }
});

io.on('connection', (socket) => {
    console.log(`User connected: ${socket.id}`);

    // [1단계] 방에 접속하는 로직
    socket.on('join_room', (roomId) => {
        const room = io.sockets.adapter.rooms.get(roomId);
        const numClients = room ? room.size : 0;

        socket.join(roomId); // 사용자가 특정 roomId (예: test-room) 모앙의 방에 들어감
        console.log(`User ${socket.id} joined room ${roomId}. Total clients: ${numClients + 1}`);

        // Notify others in the room
        // 방에 있던 기존 사람에게 "새 멤버가 왔어!" 라고 알림
        socket.to(roomId).emit('user_joined', socket.id);

        // 만약 방에 누군가 이미 있었다면, 방금 들어온 사람에게 "이제 통신 시작해도 돼!"(ready) 라고 알림
        if (numClients > 0) {
            socket.emit('ready');
        }
    });

    // WebRTC Offer
    socket.on('offer', (data) => {
        console.log(`Offer received for room ${data.roomId} from ${socket.id}`);
        socket.to(data.roomId).emit('offer', data);
    });

    // WebRTC Answer
    socket.on('answer', (data) => {
        console.log(`Answer received for room ${data.roomId} from ${socket.id}`);
        socket.to(data.roomId).emit('answer', data);
    });

    // ICE Candidate
    socket.on('ice_candidate', (data) => {
        socket.to(data.roomId).emit('ice_candidate', data);
    });

    // Remote media state toggles (mic/video)
    socket.on('media_state_change', (data) => {
        socket.to(data.roomId).emit('media_state_change', data);
    });

    // End call
    socket.on('end_call', (roomId) => {
        socket.to(roomId).emit('call_ended');
    });

    socket.on('disconnect', () => {
        console.log(`User disconnected: ${socket.id}`);
    });
});

const PORT = 3001;
server.listen(PORT, () => {
    console.log(`WebRTC Signaling server running on port ${PORT}`);
});

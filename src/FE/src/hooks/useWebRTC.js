import { useEffect, useRef, useState, useCallback } from 'react';
import { io } from 'socket.io-client';

const SIGNALING_SERVER_URL = 'http://localhost:3001';

export const useWebRTC = (roomId, isInitiator) => {
    const localVideoRef = useRef(null);
    const remoteVideoRef = useRef(null);

    const [localStream, setLocalStream] = useState(null);
    const [remoteStream, setRemoteStream] = useState(null);
    const [isConnected, setIsConnected] = useState(false);

    const socketRef = useRef(null);
    const peerConnectionRef = useRef(null);
    const isInitializingRef = useRef(false); // 카메라 중복 실행 방지용 플래그

    // Get user media
    const initCamera = useCallback(async (videoEnabled = true, micEnabled = true) => {
        if (isInitializingRef.current || localStream) return localStream; // 이미 켜져 있거나 실행 중이면 조기 종료
        isInitializingRef.current = true;
        try {
            const stream = await navigator.mediaDevices.getUserMedia({
                video: videoEnabled,
                audio: micEnabled
            });
            setLocalStream(stream);
            if (localVideoRef.current) {
                localVideoRef.current.srcObject = stream;
            }
            isInitializingRef.current = false;
            return stream;
        } catch (err) {
            console.error("Error accessing media devices.", err);
            isInitializingRef.current = false;
            return null;
        }
    }, [localStream]);

    // Toggle tracks
    const toggleMedia = useCallback(async (type, enabled) => {
        if (!localStream) return;

        // 오디오는 단순히 활성화/비활성화만 토글합니다.
        if (type === 'audio') {
            localStream.getAudioTracks().forEach(track => {
                track.enabled = enabled;
            });
        }
        // 비디오의 경우, 완전히 껐다가 켜야 카메라 불빛이 꺼지므로 트랙을 조작합니다.
        else if (type === 'video') {
            const videoTracks = localStream.getVideoTracks();

            if (!enabled) {
                // 비디오 끄기: 기존 트랙을 중지하고 스트림에서 제거합니다.
                videoTracks.forEach(track => {
                    track.stop();
                    localStream.removeTrack(track);
                });

                // 불필요한 빈 검은화면 송출 방지 및 UI 업데이트 유도
                if (localVideoRef.current) {
                    localVideoRef.current.srcObject = localStream;
                }
            } else {
                // 비디오 켜기: 이미 트랙이 있다면 무시, 없다면 새로 카메라 권한을 요청합니다.
                if (videoTracks.length === 0) {
                    try {
                        const newStream = await navigator.mediaDevices.getUserMedia({ video: true });
                        const newVideoTrack = newStream.getVideoTracks()[0];

                        // 새 비디오 트랙을 기존 로컬 스트림에 추가합니다.
                        localStream.addTrack(newVideoTrack);

                        // HTML 비디오 요소에 새 스트림을 다시 연결합니다.
                        if (localVideoRef.current) {
                            localVideoRef.current.srcObject = localStream;
                        }

                        // 만약 이미 PeerConnection이 연결되어 있다면 Sender의 트랙도 교체해 주어야 방에 있는 상대방에게 화면이 전송됩니다.
                        if (peerConnectionRef.current) {
                            const senders = peerConnectionRef.current.getSenders();
                            const videoSender = senders.find(sender => sender.track && sender.track.kind === 'video');

                            if (videoSender) {
                                videoSender.replaceTrack(newVideoTrack);
                            } else {
                                // 기존에 비디오 없이 입장했다면 새로 추가합니다.
                                peerConnectionRef.current.addTrack(newVideoTrack, localStream);
                            }
                        }
                    } catch (err) {
                        console.error('카메라를 다시 켜는 데 실패했습니다:', err);
                    }
                }
            }
        }

        // 상태 변경을 서버를 통해 상대방에게 알림 (UI 동기화용)
        if (socketRef.current) {
            socketRef.current.emit('media_state_change', { roomId, type, enabled });
        }
    }, [localStream, roomId]);

    useEffect(() => {
        // Connect to Socket.io signaling server
        socketRef.current = io(SIGNALING_SERVER_URL);

        // Setup RTCPeerConnection
        const configuration = {
            iceServers: [
                { urls: 'stun:stun.l.google.com:19302' },
                { urls: 'stun:stun1.l.google.com:19302' }
            ]
        };
        const pc = new RTCPeerConnection(configuration);
        peerConnectionRef.current = pc;

        // [4단계 - 3] 서로의 네트워크 길 찾기 (ICE Candidate)
        // 내 컴퓨터가 네트워크 연결 경로(Candidate)를 찾아내면 상대방에게 던짐
        pc.onicecandidate = (event) => {
            if (event.candidate) {
                socketRef.current.emit('ice_candidate', {
                    roomId,
                    candidate: event.candidate
                });
            }
        };

        // [5단계] 화면에 영상 띄우기
        // 상대방의 미디어 스트림 트랙이 도착하면 발생하는 이벤트
        pc.ontrack = (event) => {
            setRemoteStream(event.streams[0]); // 전역 상태에 상대방 영상 저장

            // HTML <video ref={...} /> 태그에 상대방 영상을 꽂아 넣음!!
            if (remoteVideoRef.current) {
                remoteVideoRef.current.srcObject = event.streams[0];
            }
            setIsConnected(true);
        };

        // [4단계 - 1] 의사의 제안 (Offer 생성 및 전송)
        // 의사(isInitiator) 브라우저에서 환자가 들어왔다는 소식(user_joined)을 듣고 실행
        const initiateCall = async () => {
            if (isInitiator) { // 내가 의사라면
                try {
                    const offer = await pc.createOffer(); // 내 화상 설정(SDP)을 담은 제안서 생성
                    await pc.setLocalDescription(offer);  // 내 로컬에 제안서 저장
                    socketRef.current.emit('offer', { roomId, offer }); // 서버를 통해 환자에게 제안서 발송!
                } catch (e) {
                    console.error("Error creating offer", e);
                }
            }
        };

        // Socket Events
        socketRef.current.on('user_joined', initiateCall);
        socketRef.current.on('ready', initiateCall);

        // [4단계 - 2] 환자의 수락 (Answer 생성 및 전송)
        // 환자 브라우저에서 의사의 제안서(Offer)를 받은 경우 실행
        socketRef.current.on('offer', async (data) => {
            if (!isInitiator) { // 내가 환자라면
                try {
                    await pc.setRemoteDescription(new RTCSessionDescription(data.offer)); // 의사의 제안서 등록
                    const answer = await pc.createAnswer(); // 수락서 생성
                    await pc.setLocalDescription(answer);   // 내 로컬에 수락서 저장
                    socketRef.current.emit('answer', { roomId, answer }); // 서버를 통해 의사에게 수락서 뿅!
                } catch (e) {
                    console.error("Error handling offer", e);
                }
            }
        });

        socketRef.current.on('answer', async (data) => {
            if (isInitiator) {
                try {
                    await pc.setRemoteDescription(new RTCSessionDescription(data.answer));
                } catch (e) {
                    console.error("Error handling answer", e);
                }
            }
        });

        // [4단계 - 3] 서로의 네트워크 길 찾기 (ICE Candidate)
        // 상대방의 네트워크 경로를 전달받으면 내 연결 설정에 추가
        socketRef.current.on('ice_candidate', async (data) => {
            try {
                if (data.candidate) {
                    await pc.addIceCandidate(new RTCIceCandidate(data.candidate));
                }
            } catch (e) {
                console.error("Error adding ice candidate", e);
            }
        });

        // Cleanup
        return () => {
            // Stop media tracks only if they exist on the local stream when unmounting
            // Note: In strict mode, React runs unmount multiple times, so be careful.
            pc.close();
            if (socketRef.current) socketRef.current.disconnect();
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [roomId, isInitiator]);

    // Ensure the remote video element is updated when the stream arrives
    useEffect(() => {
        if (remoteVideoRef.current && remoteStream) {
            if (remoteVideoRef.current.srcObject !== remoteStream) {
                remoteVideoRef.current.srcObject = remoteStream;
            }
        }
    }, [remoteStream, remoteVideoRef]);

    // Attach local stream to peer connection and join room
    const joinRoom = useCallback(async (existingStream) => {
        let stream = existingStream || localStream;
        if (!stream) {
            stream = await initCamera();
        }

        if (stream && peerConnectionRef.current) {
            // Only add tracks that are not already added
            const senders = peerConnectionRef.current.getSenders();
            stream.getTracks().forEach(track => {
                const isAlreadySender = senders.find(s => s.track === track);
                if (!isAlreadySender) {
                    peerConnectionRef.current.addTrack(track, stream);
                }
            });
        }

        socketRef.current.emit('join_room', roomId);
    }, [localStream, initCamera, roomId]);

    // Cleanup tracks manually if needed (e.g. when ending call)
    const cleanupMedia = useCallback(() => {
        if (localStream) {
            localStream.getTracks().forEach(track => track.stop());
            setLocalStream(null);
        }
    }, [localStream]);

    return {
        localVideoRef,
        remoteVideoRef,
        localStream,
        remoteStream,
        isConnected,
        initCamera,
        joinRoom,
        toggleMedia,
        cleanupMedia
    };
};

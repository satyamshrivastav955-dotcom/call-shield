import React, {useState, useEffect, useRef} from "react";
import {FaMicrophone, FaVideo} from "react-icons/fa";
import "./CallComponent.css";
import SignalTypes from "../utils/SignalTypes.jsx";
import Utils from "../utils/Utils.jsx";
import configuration from "../utils/IceServers.jsx";

const CallComponent = ({onEndCall, callback, receivedMessage, sender, target}) => {
    const [isMicEnabled, setMicEnabled] = useState(true);
    const [isCameraEnabled, setCameraEnabled] = useState(true);

    const [localStream, setLocalStream] = useState(null);
    const peerConnectionRef = useRef(null);

    // Request both audio and video permissions
    useEffect(() => {
        const getMediaPermissions = async () => {
            try {
                const stream = await navigator.mediaDevices.getUserMedia({
                    video: isCameraEnabled,
                    audio: isMicEnabled,
                });
                setLocalStream(stream);
                document.getElementById("local-video").srcObject = stream;
            } catch (error) {
                console.error("Permission denied for camera or microphone", error);
                alert("Please allow camera and microphone permissions.");
            }
        };

        getMediaPermissions();

        return () => {
            if (localStream) {
                localStream.getTracks().forEach((track) => track.stop());
            }
        };
    }, [isCameraEnabled, isMicEnabled]);

    // Setup PeerConnection with ICE Servers
    const setupPeerConnection = () => {
        const pc = new RTCPeerConnection(configuration);

        if (localStream) {
            localStream.getTracks().forEach((track) => {
                pc.addTrack(track, localStream);
            });
        }
        pc.onicegatheringstatechange = e => {
            console.log("ice state", e)
        }

        pc.oniceconnectionstatechange = e => {
            console.log("ice state222", e)

        }
        pc.onicecandidate = async (event) => {
            if (event.candidate) {
                await pc.addIceCandidate(event.candidate)
                callback({
                    type: SignalTypes.ICE,
                    sender: sender,
                    target: target,
                    data: JSON.stringify({
                        adapterType: "UNKNOWN", // You can leave it as "UNKNOWN"
                        sdp: event.candidate.candidate, // ICE candidate
                        sdpMLineIndex: event.candidate.sdpMLineIndex, // MLine index
                        sdpMid: event.candidate.sdpMid, // MLine ID (MID)
                        serverUrl: event.candidate.address
                    }),
                });
            }
        };

        pc.onconnectionstatechange = state => {
            console.log("state of connection", state)
        }

        pc.ontrack = (event) => {
            document.getElementById("remote-video").srcObject = event.streams[0];
        };

        return pc;
    };

    // Start Call (Create Offer)
    const startCall = async (sender, target) => {
        const pc = await setupPeerConnection();
        peerConnectionRef.current = pc;

        try {
            const offer = await pc.createOffer(Utils.getCallOptions);
            await pc.setLocalDescription(offer);
            callback({
                type: SignalTypes.Offer,
                sender: sender,
                target: target,
                data: pc.localDescription.sdp,
            });
        } catch (error) {
            console.error("Error creating offer", error);
        }
    };

    // Answer Call (Create Answer)
    const answerCall = async (sender, target, sdp) => {
        const pc = await setupPeerConnection();
        peerConnectionRef.current = pc;

        try {
            await offerReceived(pc, sdp);
            const answer = await pc.createAnswer(Utils.getCallOptions);
            await pc.setLocalDescription(answer);
            callback({
                type: SignalTypes.Answer,
                sender: sender,
                target: target,
                data: pc.localDescription.sdp,
            });
        } catch (error) {
            console.error("Error creating answer", error);
        }
    };

    const offerReceived = async (pc, sdp) => {
        if (pc) {
            await pc.setRemoteDescription(new RTCSessionDescription({type: "offer", sdp}));
        } else {
            console.error("Peer connection is not initialized yet");
        }
    };

    const answerReceived = async (sdp) => {
        if (peerConnectionRef.current) {
            await peerConnectionRef.current.setRemoteDescription(new RTCSessionDescription({type: "answer", sdp}));
        } else {
            console.error("Peer connection is not initialized yet");
        }
    };

    const addRemoteIceCandidate = async (candidateData) => {
        if (peerConnectionRef.current && peerConnectionRef.current.remoteDescription) {
            try {
                const parsedData = JSON.parse(candidateData);
                const candidate = new RTCIceCandidate({
                    candidate: parsedData.sdp,
                    sdpMLineIndex: parsedData.sdpMLineIndex,
                    sdpMid: parsedData.sdpMid,
                });
                console.log(`received ice converted ${candidate.candidate}`, "    ", candidate.sdpMLineIndex, "   ", candidate.sdpMid)
                await peerConnectionRef.current.addIceCandidate(candidate);
            } catch (error) {
                console.error("Error adding ICE candidate", error);
            }
        } else {
            console.warn("Remote description is not set yet, skipping ICE candidate");
        }
    };

    useEffect(() => {
        const handleReceivedMessage = async () => {
            if (receivedMessage) {
                console.log("Received message ", receivedMessage);
                switch (receivedMessage.type) {
                    case SignalTypes.AcceptCall:
                        await startCall(receivedMessage.target, receivedMessage.sender);
                        break;
                    case SignalTypes.Offer:
                        await answerCall(receivedMessage.target, receivedMessage.sender, receivedMessage.data);
                        break;
                    case SignalTypes.Answer:
                        await answerReceived(receivedMessage.data);
                        break;
                    case SignalTypes.ICE:
                        await addRemoteIceCandidate(receivedMessage.data);
                        break;
                    default:
                        console.log("Unknown message type", receivedMessage);
                }
            }
        };

        handleReceivedMessage();
    }, [receivedMessage]);

    return (
        <div className="call-component">
            <div className="video-container">
                <video id="remote-video" className="remote-video" autoPlay playsInline></video>
                <video id="local-video" className="local-video" autoPlay muted playsInline></video>
            </div>

            <div className="call-controls">

                <button onClick={onEndCall}>
                    <img src="/end_call.png" alt="End call" style={{width: "20px", height: "20px"}}/>
                </button>


                <button onClick={() => setMicEnabled(!isMicEnabled)}>
                    <FaMicrophone color={isMicEnabled ? "white" : "red"}/>
                </button>

                <button onClick={() => setCameraEnabled(!isCameraEnabled)}>
                    <FaVideo color={isCameraEnabled ? "white" : "red"}/>
                </button>

            </div>
        </div>
    );
};

export default CallComponent;

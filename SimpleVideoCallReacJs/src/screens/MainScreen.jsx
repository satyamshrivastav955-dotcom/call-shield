import {useEffect, useRef, useState} from "react";
import SocketClient from "../socket/SocketClient.jsx";
import Utils from "../utils/Utils.jsx";
import RandomString from "../utils/RandomString.jsx";
import CallState from "../utils/CallState.jsx";
import MainHeader from "../components/MainHeader.jsx";
import YourIdCard from "../components/YourIdCard.jsx";
import SocketState from "../utils/SocketState.jsx";
import WhoToCall from "../components/WhoToCall.jsx";
import IncomingCallSnackBar from "../components/IncomingCallSnackBar.jsx";
import SignalTypes from "../utils/SignalTypes.jsx";
import SignalSender from "../socket/SignalSender.jsx";
import CallComponent from "../components/CallComponent.jsx";

const MainScreen =()=>{
    const myUserId = useRef(RandomString.generateRandomString())
    const target = useRef("")
    const [socketClient, setSocketClient] = useState(null)
    const [callStatus,setCallStatus] = useState(CallState.WAITING)
    const [socketStatus,setSocketStatus] = useState(SocketState.DISCONNECTED)
    const [signalSender,setSignalSender] = useState(null)
    const [receivedMessage,setReceivedMessage] = useState(null)

    useEffect(() => {
        const socketClient = new SocketClient()
        socketClient.init(Utils.getBaseUrl(myUserId.current),listener)
        setSocketClient(socketClient)
        const signalSenderInstance = new SignalSender(socketClient)
        setSignalSender(signalSenderInstance)

        return () => {
            socketClient.close()
        }
    }, []);


    const listener = {
        onRemoteSocketClientOpened:()=>{
            setSocketStatus(SocketState.CONNECTED)
        },
        onRemoteSocketClientClosed:() =>{
            setSocketStatus(SocketState.DISCONNECTED)
        },
        onRemoteSocketClientNewMessage: (message) =>{
            console.log(message)
            handleIncomingMessages(message)
        }
    }

    const handleIncomingMessages = message => {
        switch (message.type) {
            case SignalTypes.StartCall:
                target.current = message.sender
                setCallStatus(CallState.RECEIVED_CALL)
                break
            case SignalTypes.AcceptCall:
                target.current = message.sender
                setCallStatus(CallState.ON_CALL)
                break
            case SignalTypes.EndCall:
                setCallStatus(CallState.WAITING)
                break
        }
        setReceivedMessage(message)
    }

    const onCallTargetPressed = target => {
        signalSender.sendStartCall(myUserId.current,target)
    }
    const acceptIncomingCall = () => {
        setCallStatus(CallState.ON_CALL)
        signalSender.sendAcceptCall(myUserId.current,target.current)
    }
    const rejectIncomingCall = () => {
        setCallStatus(CallState.WAITING)
        signalSender.sendRejectCall(myUserId.current,target.current)
    }

    const  callComponentListener = message => {
        socketClient.sendDataToHost(message)
    }

    const endCallListener = () => {
        signalSender.sendEndCall(myUserId.current, target.current)
        setCallStatus(CallState.WAITING)
    }

    return (
        <div className={"main-screen"}>
            {callStatus !== CallState.ON_CALL && <MainHeader callStatus={callStatus}/>}
            {callStatus !== CallState.ON_CALL && <YourIdCard userId={myUserId.current}/>}
            {callStatus === CallState.WAITING && socketStatus === SocketState.CONNECTED &&
                (<WhoToCall onCallClick={onCallTargetPressed}/> )}
            {callStatus ===CallState.ON_CALL && callStatus !== CallState.WAITING &&
                (<CallComponent
                    callback={callComponentListener}
                    onEndCall={endCallListener}
                    receivedMessage={receivedMessage}
                    sender={myUserId.current}
                    target={target.current}/>
                )}
            {callStatus === CallState.RECEIVED_CALL &&
                (<IncomingCallSnackBar callerId={target.current} onAccept={acceptIncomingCall} onReject={rejectIncomingCall}/> )}
        </div>
    )
}

export default MainScreen
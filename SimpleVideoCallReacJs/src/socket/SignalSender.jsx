import SignalTypes from "../utils/SignalTypes.jsx";

class SignalSender {
    constructor(socketClient) {
        if (!socketClient){
            throw new Error("Socket client is required to initialize SignalSender")
        }
        this.socketClient = socketClient
    }

    sendRejectCall(sender,target){
        const message = {
            type : SignalTypes.RejectCall,
            sender : sender,
            target : target
        }
        this.sendData(message)
    }

    sendStartCall(sender,target){
        const message = {
            type : SignalTypes.StartCall,
            sender : sender,
            target : target
        }
        this.sendData(message)
    }

    sendAcceptCall(sender,target){
        const message = {
            type : SignalTypes.AcceptCall,
            sender : sender,
            target : target
        }
        this.sendData(message)
    }

    sendEndCall(sender,target){
        const message = {
            type : SignalTypes.EndCall,
            sender : sender,
            target : target
        }
        this.sendData(message)
    }

    sendData(data){
        try {
            this.socketClient.sendDataToHost(data)
        }catch (e) {
            console.error("Failed to send data ",data)
        }
    }
}

export default SignalSender
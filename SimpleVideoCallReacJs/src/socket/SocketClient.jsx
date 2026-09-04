class SocketClient {

    socketClient = null

    init(socketUrl,listener){
        if (this.socketClient === null){
            this.socketClient = new WebSocket(socketUrl)

            this.socketClient.onopen = () => {
                listener.onRemoteSocketClientOpened()
            }

            this.socketClient.onmessage = event => {
                try{
                    const message = JSON.parse(event.data)
                    listener.onRemoteSocketClientNewMessage(message)
                }catch (e) {
                    console.error("Error parsing message", e)
                }
            }

            this.socketClient.onClose = () => {
                listener.onRemoteSocketClientClosed()
            }
        } else {
            listener.onRemoteSocketClientOpened()
        }
    }

    sendDataToHost(data){
        try {
            const jsonData = JSON.stringify(data)
            this.socketClient.send(jsonData)
        } catch (e) {
            console.error("Error sending data",data)
        }
    }

    close() {
        if (this.socketClient){
            this.socketClient.close()
        }
    }
}

export default SocketClient
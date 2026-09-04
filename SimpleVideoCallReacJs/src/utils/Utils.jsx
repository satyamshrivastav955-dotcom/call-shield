class Utils {

    static getBaseUrl(myUserId){
        return `ws://localhost:3007/?username=${myUserId}`
    }

    static getCallOptions() {
        return {offerToReceiveAudio: true, offerToReceiveVideo: false}
    }
}

export default Utils
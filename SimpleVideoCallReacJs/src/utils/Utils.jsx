class Utils {

    static getBaseUrl(myUserId){
        const customUrl = (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_SIGNALING_URL) 
            || (typeof window !== 'undefined' && window.__SIGNALING_URL__);
        if (customUrl) {
            const base = customUrl.replace(/\/$/, "");
            return `${base}/?username=${myUserId}`;
        }
        return `ws://localhost:3007/?username=${myUserId}`;
    }

    static getCallOptions() {
        return {offerToReceiveAudio: true, offerToReceiveVideo: false};
    }
}

export default Utils
import React from 'react';
import CallState from "../utils/CallState.jsx";
import './MainHeader.css';

const MainHeader = ({ callStatus }) => {
    return (
        <div className="header">
            {callStatus !== CallState.ON_CALL && (
                <>
                    <div
                        className="image-container"
                        onClick={() => window.open('https://www.youtube.com/@codewithkael')}
                    >
                        <img src="webrtc.png" alt="WebRTC" />
                    </div>
                    <h1 className="header-title">Simple Video Call with WebRTC</h1>
                    <p className="header-description">Enter your friends ID and press call !!</p>
                </>
            )}
        </div>
    );
};

export default MainHeader;

import React, { useState, useEffect } from 'react';
import './IncomingCallSnackBar.css';

const IncomingCallSnackBar = ({ callerId, onAccept, onReject }) => {
    const [showSnackBar, setShowSnackBar] = useState(true);

    useEffect(() => {
        const timer = setTimeout(() => {
            setShowSnackBar(false);
            if (onReject) {
                onReject(callerId); // Notify main screen when the call times out
            }
        }, 10000);

        return () => clearTimeout(timer);
    }, [callerId, onReject]);

    return (
        showSnackBar && (
            <div className="incoming-call-snackbar">
                <div className="snackbar-content">
                    <h3>Incoming Call from</h3>
                    <h2>{callerId}</h2>

                    <div className="snackbar-buttons">
                        <button className="reject-button" onClick={() => onReject()}>
                            No
                        </button>
                        <button className="accept-button" onClick={() => onAccept()}>
                            Yes
                        </button>
                    </div>
                </div>
            </div>
        )
    );
};

export default IncomingCallSnackBar;

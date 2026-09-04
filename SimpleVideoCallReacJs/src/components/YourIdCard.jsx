import React, { useState } from 'react';
import { FaClipboard } from 'react-icons/fa';
import './YourIdCard.css';

const YourIdCard = ({ userId }) => {
    const [showToast, setShowToast] = useState(false);

    const copyToClipboard = () => {
        navigator.clipboard.writeText(userId)
            .then(() => {
                setShowToast(true); // Show toast
                setTimeout(() => setShowToast(false), 1500); // Hide toast after 3 seconds
            })
            .catch(err => {
                console.error('Failed to copy to clipboard: ', err);
            });
    };

    return (
        <div className="your-id-card-container">
            <div className="your-id-card" onClick={copyToClipboard}>
                <FaClipboard />
                <p>Your ID: {userId}</p>
            </div>
            {showToast && <div className="toast">Copied to clipboard!</div>}
        </div>
    );
};

export default YourIdCard;

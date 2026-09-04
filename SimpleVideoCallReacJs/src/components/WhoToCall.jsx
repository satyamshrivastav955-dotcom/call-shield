import React, { useState } from 'react';
import './WhoToCall.css';

const WhoToCall = ({ onCallClick }) => {
    const [targetUsername, setTargetUsername] = useState('');

    return (
        <div className="who-to-call">
            <h3>Who to Call?</h3>
            <input
                type="text"
                value={targetUsername}
                onChange={(e) => setTargetUsername(e.target.value)}
                placeholder="Enter an ID to call"
            />
            <button onClick={() => targetUsername && onCallClick(targetUsername)}>
                Call
            </button>
        </div>
    );
};

export default WhoToCall;

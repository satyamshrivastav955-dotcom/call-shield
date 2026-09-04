import './App.css'
import { BrowserRouter, Routes, Route } from "react-router-dom";
import MainScreen from "./screens/MainScreen.jsx";

const App = () => (
    <BrowserRouter>
        <Routes>
            <Route path="/" element={<MainScreen />} />
        </Routes>
    </BrowserRouter>
);

export default App;

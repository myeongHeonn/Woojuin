import { Route, Routes } from 'react-router-dom';
import HomePage from './pages/HomePage';
import ShareTargetPage from './pages/ShareTargetPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      {/* Web Share Target 진입점 (manifest.share_target.action) */}
      <Route path="/share-target" element={<ShareTargetPage />} />
    </Routes>
  );
}

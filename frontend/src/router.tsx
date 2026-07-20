import { createBrowserRouter } from 'react-router-dom';
import HomePage from './pages/HomePage';
import ShareTargetPage from './pages/ShareTargetPage';

export const router = createBrowserRouter([
  { path: '/', element: <HomePage /> },
  // Web Share Target 진입점 (manifest.share_target.action)
  { path: '/share-target', element: <ShareTargetPage /> },
]);

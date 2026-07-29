import { useEffect } from 'react';

export interface ToastMessage {
  message: string;
  tone: 'success' | 'error' | 'info';
}

interface ToastProps {
  toast: ToastMessage | null;
  onDismiss: () => void;
}

const Toast = ({ toast, onDismiss }: ToastProps) => {
  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(onDismiss, 2200);
    return () => window.clearTimeout(timer);
  }, [onDismiss, toast]);

  if (!toast) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      data-tone={toast.tone}
      className="fixed bottom-24 left-1/2 z-[110] -translate-x-1/2 rounded-md border border-border bg-surface-2 px-5 py-[11px] text-[13.5px] text-text-1 shadow-pop desktop:bottom-10"
    >
      {toast.message}
    </div>
  );
};

export default Toast;

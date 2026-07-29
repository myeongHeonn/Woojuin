import { publicApiFetch } from '@/api/client';
import { clearTokens, saveTokens } from '@/storage/authStorage';

interface TokenResponse { accessToken: string; refreshToken: string }

export async function login(email: string, password: string): Promise<void> {
  const tokens = await publicApiFetch<TokenResponse>('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  await saveTokens(tokens.accessToken, tokens.refreshToken);
}

export { clearTokens as logout };

import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import MemberList from '@/components/domain/share/MemberList';
import type { WorkspaceMember } from '@/services/workspaces';

const members: WorkspaceMember[] = [
  {
    userId: 1,
    nickname: '우현',
    email: 'a@x.com',
    role: 'OWNER',
    joinedAt: '',
    avatarColor: 'BLUE',
  },
  {
    userId: 2,
    nickname: '지수',
    email: 'b@x.com',
    role: 'MEMBER',
    joinedAt: '',
    avatarColor: 'PINK',
  },
];

const kickBtn = (c: HTMLElement, name: string) =>
  c.querySelector(`button[aria-label="${name} 내보내기"]`);

describe('MemberList', () => {
  it('이름과 역할을 보여주고, 나에겐 "(나)" 를 붙인다', async () => {
    const { container } = await render(<MemberList members={members} myUserId={1} />);
    expect(container.textContent).toContain('우현');
    expect(container.textContent).toContain('OWNER');
    expect(container.textContent).toContain('지수');
    expect(container.textContent).toContain('(나)');
  });

  it('canKick 이면 다른 멤버엔 내보내기가 뜨고, 자기 자신엔 안 뜬다', async () => {
    const onKick = vi.fn();
    const { container } = await render(
      <MemberList members={members} myUserId={1} canKick onKick={onKick} />,
    );
    expect(kickBtn(container, '우현')).toBeNull(); // 나(1) — 강퇴 없음
    const other = kickBtn(container, '지수')!;
    expect(other).not.toBeNull();

    await userEvent.click(other);
    expect(onKick).toHaveBeenCalledWith(2);
  });

  it('canKick 이 아니면 아무에게도 내보내기가 없다', async () => {
    const { container } = await render(<MemberList members={members} myUserId={2} />);
    expect(kickBtn(container, '우현')).toBeNull();
    expect(kickBtn(container, '지수')).toBeNull();
  });
});

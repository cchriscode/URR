import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SiteHeader } from '@/components/site-header';

// Mock storage module
vi.mock('@/lib/storage', () => ({
  getUser: vi.fn(),
  clearAuth: vi.fn(),
}));

// Mock api-client
vi.mock('@/lib/api-client', () => ({
  authApi: {
    logout: vi.fn().mockResolvedValue({}),
  },
}));

import { getUser } from '@/lib/storage';

describe('SiteHeader', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders logo and nav links', () => {
    vi.mocked(getUser).mockReturnValue(null);
    render(<SiteHeader />);
    expect(screen.getByText('URR')).toBeInTheDocument();
    expect(screen.getByText('아티스트')).toBeInTheDocument();
    expect(screen.getByText('News')).toBeInTheDocument();
  });

  it('shows login/register links when not authenticated', () => {
    vi.mocked(getUser).mockReturnValue(null);
    render(<SiteHeader />);
    expect(screen.getByText('로그인')).toBeInTheDocument();
    expect(screen.getByText('회원가입')).toBeInTheDocument();
    expect(screen.queryByText('로그아웃')).not.toBeInTheDocument();
  });

  it('shows user name and logout when authenticated', () => {
    vi.mocked(getUser).mockReturnValue({
      id: '1',
      email: 'test@test.com',
      name: '테스터',
      role: 'user',
    });
    render(<SiteHeader />);
    expect(screen.getByText('테스터')).toBeInTheDocument();
    expect(screen.getByText('로그아웃')).toBeInTheDocument();
    expect(screen.getByText('내 예매')).toBeInTheDocument();
    expect(screen.queryByText('로그인')).not.toBeInTheDocument();
  });

  it('shows admin link for admin users', () => {
    vi.mocked(getUser).mockReturnValue({
      id: '1',
      email: 'admin@test.com',
      name: '어드민',
      role: 'admin',
    });
    render(<SiteHeader />);
    expect(screen.getByRole('link', { name: '관리자' })).toBeInTheDocument();
  });

  it('hides admin link for regular users', () => {
    vi.mocked(getUser).mockReturnValue({
      id: '1',
      email: 'user@test.com',
      name: '유저',
      role: 'user',
    });
    render(<SiteHeader />);
    expect(screen.queryByText('관리자')).not.toBeInTheDocument();
  });

  it('renders search form', () => {
    vi.mocked(getUser).mockReturnValue(null);
    render(<SiteHeader />);
    expect(screen.getByPlaceholderText('공연, 아티스트 검색')).toBeInTheDocument();
    expect(screen.getByText('검색')).toBeInTheDocument();
  });
});

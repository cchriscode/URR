import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SiteHeader } from '@/components/site-header';

// Mock auth-context
const mockUseAuth = vi.fn();
vi.mock('@/lib/auth-context', () => ({
  useAuth: () => mockUseAuth(),
}));

// Mock api-client
vi.mock('@/lib/api-client', () => ({
  authApi: {
    logout: vi.fn().mockResolvedValue({}),
  },
}));

describe('SiteHeader', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockLogout = vi.fn().mockResolvedValue(undefined);

  function setAuth(user: { id: string; email: string; name: string; role: string } | null) {
    mockUseAuth.mockReturnValue({
      user,
      isLoading: false,
      isAuthenticated: !!user,
      refresh: vi.fn(),
      logout: mockLogout,
    });
  }

  it('renders logo and nav links', () => {
    setAuth(null);
    render(<SiteHeader />);
    expect(screen.getByText('URR')).toBeInTheDocument();
    expect(screen.getByText('아티스트')).toBeInTheDocument();
  });

  it('shows login/register links when not authenticated', () => {
    setAuth(null);
    render(<SiteHeader />);
    expect(screen.getByText('로그인')).toBeInTheDocument();
    expect(screen.getByText('회원가입')).toBeInTheDocument();
    expect(screen.queryByText('로그아웃')).not.toBeInTheDocument();
  });

  it('shows user name and logout when authenticated', () => {
    setAuth({
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
    setAuth({
      id: '1',
      email: 'admin@test.com',
      name: '어드민',
      role: 'admin',
    });
    render(<SiteHeader />);
    expect(screen.getByRole('link', { name: '관리자' })).toBeInTheDocument();
  });

  it('hides admin link for regular users', () => {
    setAuth({
      id: '1',
      email: 'user@test.com',
      name: '유저',
      role: 'user',
    });
    render(<SiteHeader />);
    expect(screen.queryByText('관리자')).not.toBeInTheDocument();
  });

  it('renders search form', () => {
    setAuth(null);
    render(<SiteHeader />);
    expect(screen.getByPlaceholderText('공연, 아티스트 검색')).toBeInTheDocument();
    expect(screen.getByText('검색')).toBeInTheDocument();
  });
});

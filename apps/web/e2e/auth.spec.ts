import { test, expect } from '@playwright/test';

test.describe('Authentication Flow', () => {
  test('register page renders all form fields', async ({ page }) => {
    await page.goto('/register');

    await expect(page.locator('h1')).toContainText('회원가입');
    await expect(page.locator('input[placeholder="이름 입력"]')).toBeVisible();
    await expect(page.locator('input[type="email"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
    await expect(page.locator('input[placeholder="010-0000-0000"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toContainText('회원가입');
  });

  test('register page has link to login', async ({ page }) => {
    await page.goto('/register');

    const loginLink = page.locator('a[href="/login"]');
    await expect(loginLink).toBeVisible();
    await expect(loginLink).toContainText('로그인');
  });

  test('login page renders form and has register link', async ({ page }) => {
    await page.goto('/login');

    await expect(page.locator('h1')).toContainText('로그인');
    await expect(page.locator('input[type="email"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toContainText('로그인');

    const registerLink = page.locator('a[href="/register"]');
    await expect(registerLink).toBeVisible();
    await expect(registerLink).toContainText('회원가입');
  });

  test('login form shows error on invalid credentials', async ({ page }) => {
    await page.goto('/login');

    await page.locator('input[type="email"]').fill('invalid@example.com');
    await page.locator('input[type="password"]').fill('wrongpassword');
    await page.locator('button[type="submit"]').click();

    // Should show error message (either from API or network error)
    const errorMessage = page.locator('.bg-red-50');
    await expect(errorMessage).toBeVisible({ timeout: 10000 });
  });

  test('login button shows loading state on submit', async ({ page }) => {
    await page.goto('/login');

    await page.locator('input[type="email"]').fill('test@example.com');
    await page.locator('input[type="password"]').fill('password123');
    await page.locator('button[type="submit"]').click();

    // Button should show loading text briefly
    await expect(page.locator('button[type="submit"]')).toBeDisabled();
  });

  test('register form validates required fields', async ({ page }) => {
    await page.goto('/register');

    // Try to submit empty form - browser validation should prevent it
    await page.locator('button[type="submit"]').click();

    // Should still be on register page (form not submitted)
    await expect(page).toHaveURL(/\/register/);
  });

  test('navigate between login and register', async ({ page }) => {
    await page.goto('/login');

    // Go to register
    await page.click('a[href="/register"]');
    await expect(page).toHaveURL(/\/register/);
    await expect(page.locator('h1')).toContainText('회원가입');

    // Go back to login
    await page.click('a[href="/login"]');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.locator('h1')).toContainText('로그인');
  });
});

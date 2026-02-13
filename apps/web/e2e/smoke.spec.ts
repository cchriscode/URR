import { test, expect } from '@playwright/test';

test.describe('Smoke Tests', () => {
  test('home page loads and shows events', async ({ page }) => {
    await page.goto('/');
    // Header should be visible
    await expect(page.locator('text=URR')).toBeVisible();
    // Wait for events to load
    await expect(page.locator('[href*="/events/"]').first()).toBeVisible({ timeout: 10000 });
  });

  test('login page renders form', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('input[type="email"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
  });

  test('register page renders form', async ({ page }) => {
    await page.goto('/register');
    await expect(page.locator('input[type="email"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
  });

  test('artists page loads', async ({ page }) => {
    await page.goto('/artists');
    await expect(page.locator('text=아티스트')).toBeVisible();
  });

  test('news page loads', async ({ page }) => {
    await page.goto('/news');
    await expect(page.locator('text=공지사항')).toBeVisible();
  });

  test('navigation links work', async ({ page }) => {
    await page.goto('/');
    await page.click('text=아티스트');
    await expect(page).toHaveURL(/\/artists/);
    await page.click('text=News');
    await expect(page).toHaveURL(/\/news/);
  });
});

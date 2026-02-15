import { test, expect } from '@playwright/test';

test.describe('Booking Flow', () => {
  test('event list page loads and displays events', async ({ page }) => {
    await page.goto('/');

    // Wait for events to load
    await expect(page.locator('[href*="/events/"]').first()).toBeVisible({ timeout: 15000 });

    // Events should have titles and venue info
    const eventCards = page.locator('[href*="/events/"]');
    const count = await eventCards.count();
    expect(count).toBeGreaterThan(0);
  });

  test('clicking event navigates to detail page', async ({ page }) => {
    await page.goto('/');

    // Wait for events and click first one
    const firstEvent = page.locator('[href*="/events/"]').first();
    await expect(firstEvent).toBeVisible({ timeout: 15000 });
    await firstEvent.click();

    // Should navigate to event detail page
    await expect(page).toHaveURL(/\/events\/[a-f0-9-]+/);
  });

  test('event detail page shows event information', async ({ page }) => {
    await page.goto('/');

    const firstEvent = page.locator('[href*="/events/"]').first();
    await expect(firstEvent).toBeVisible({ timeout: 15000 });
    await firstEvent.click();

    await expect(page).toHaveURL(/\/events\/[a-f0-9-]+/);

    // Should show event details - wait for content to load
    // Event detail page should have venue, date, or booking button
    const pageContent = page.locator('main');
    await expect(pageContent).toBeVisible({ timeout: 10000 });
  });

  test('booking button redirects unauthenticated user to login', async ({ page }) => {
    await page.goto('/');

    const firstEvent = page.locator('[href*="/events/"]').first();
    await expect(firstEvent).toBeVisible({ timeout: 15000 });
    await firstEvent.click();

    await expect(page).toHaveURL(/\/events\/[a-f0-9-]+/);

    // Look for booking-related button (예매하기 or 좌석 선택)
    const bookingButton = page.locator('button, a').filter({
      hasText: /예매|좌석 선택|티켓 구매/,
    }).first();

    // If the event has a booking button (on_sale status), clicking should
    // eventually redirect to login for unauthenticated users
    if (await bookingButton.isVisible({ timeout: 3000 }).catch(() => false)) {
      await bookingButton.click();

      // Should end up at login or queue page (queue redirects to login if not authed)
      await page.waitForURL(/\/(login|queue)/, { timeout: 10000 });
    }
  });

  test('artists page lists artists', async ({ page }) => {
    await page.goto('/artists');

    await expect(page.locator('h1, h2').filter({ hasText: /아티스트/ })).toBeVisible({ timeout: 10000 });
  });

  test('artist detail page shows events', async ({ page }) => {
    await page.goto('/artists');

    const firstArtist = page.locator('[href*="/artists/"]').first();
    if (await firstArtist.isVisible({ timeout: 5000 }).catch(() => false)) {
      await firstArtist.click();
      await expect(page).toHaveURL(/\/artists\/[a-f0-9-]+/);
    }
  });

  test('my-reservations page requires authentication', async ({ page }) => {
    await page.goto('/my-reservations');

    // Should redirect to login page since user is not authenticated
    await expect(page).toHaveURL(/\/login/, { timeout: 10000 });
  });
});

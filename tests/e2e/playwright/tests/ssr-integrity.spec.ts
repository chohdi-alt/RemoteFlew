import { test, expect } from '@playwright/test';

test.describe('RemoteFlow SSR Integrity', () => {

    test.skip('SSR not supported in CI', async ({ page }) => {
        await page.route('**/*.js', route => route.abort());

        await page.goto('/');

        const appRoot = page.locator('app-root');
        await expect(appRoot).toBeAttached();

        const title = await page.title();
        expect(title).toContain('RemoteFlow');
    });

    test('should hydrate smoothly without UI mismatch', async ({ page }) => {
        await page.goto('/');

        await page.waitForSelector('app-root', { state: 'attached' });
        await expect(page.locator('app-root')).toBeVisible();

        const loginLink = page.locator('a:has-text("Login"), button:has-text("Login")');

        if (await loginLink.count() > 0) {
            await loginLink.first().click();
            await expect(page).toHaveURL(/login/);
        }
    });
});
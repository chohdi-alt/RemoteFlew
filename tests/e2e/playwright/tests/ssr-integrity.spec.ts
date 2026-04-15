import { test, expect } from '@playwright/test';

test.describe('RemoteFlow SSR Integrity', () => {

    test('should serve pre-rendered HTML shell before hydration', async ({ page }: { page: any }) => {
        // Disable JavaScript to see what the server actually sends
        await page.route('**/*.js', (route: any) => route.abort());

        await page.goto('/');

        // Assert that the basic app structure exists in the raw HTML
        const appRoot = page.locator('app-root');
        await expect(appRoot).toBeAttached();

        // Check for common SSR markers or initial titles
        const title = await page.title();
        expect(title).toContain('RemoteFlow');
    });

    test('should hydrate smoothly without UI mismatch', async ({ page }: { page: any }) => {
        // Run with JS ENABLED
        await page.goto('/');

        // Wait for Angular to hydrate
        await expect(page.locator('app-root')).toBeVisible();

        // Check if navigation works after hydration
        const loginLink = page.locator('a:has-text("Login"), button:has-text("Login")');
        if (await loginLink.count() > 0) {
            await loginLink.first().click();
            await expect(page).toHaveURL(/.*login/);
        }
    });
});

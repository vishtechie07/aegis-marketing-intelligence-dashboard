import { expect, test } from '@playwright/test'

test('dashboard shell renders', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('AEGIS')).toBeVisible()
  await expect(page.getByText('Competitor Intelligence Engine')).toBeVisible()
})

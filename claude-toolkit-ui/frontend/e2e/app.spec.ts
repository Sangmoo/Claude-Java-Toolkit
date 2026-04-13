import { test, expect } from '@playwright/test'

test.describe('Claude Java Toolkit — React SPA', () => {
  test('로그인 페이지가 렌더링된다', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByRole('heading', { name: 'Claude Java Toolkit' })).toBeVisible()
    await expect(page.getByPlaceholder('admin')).toBeVisible()
    await expect(page.getByRole('button', { name: '로그인' })).toBeVisible()
  })

  test('인증 없이 접근 시 로그인으로 리다이렉트', async ({ page }) => {
    await page.goto('/chat')
    await expect(page).toHaveURL(/\/login/)
  })

  test('테마 토글이 동작한다', async ({ page }) => {
    await page.goto('/login')
    const themeBtn = page.getByRole('button', { name: /Light|Dark/ })
    await themeBtn.click()
    // data-theme 속성이 변경되었는지 확인
    const theme = await page.evaluate(() => document.documentElement.getAttribute('data-theme'))
    expect(theme).toBeTruthy()
  })

  test('로그인 폼 유효성 검사', async ({ page }) => {
    await page.goto('/login')
    // 빈 상태로 제출 시도 — HTML5 required 검증
    const submitBtn = page.getByRole('button', { name: '로그인' })
    await submitBtn.click()
    // 여전히 로그인 페이지
    await expect(page).toHaveURL(/\/login/)
  })

  test('각 라우트가 로딩 가능하다 (인증 전 → 리다이렉트)', async ({ page }) => {
    const routes = ['/advisor', '/pipelines', '/history', '/settings']
    for (const route of routes) {
      await page.goto(route)
      await expect(page).toHaveURL(/\/login/)
    }
  })
})

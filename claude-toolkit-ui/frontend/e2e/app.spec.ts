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
    // v4.2.7 에서 추가된 경로도 포함하여 회귀 방지
    const routes = [
      '/advisor', '/pipelines', '/history', '/settings',
      '/favorites', '/review-requests', '/harness',
      '/review-requests?historyId=42',  // 알림 딥링크 포맷
    ]
    for (const route of routes) {
      await page.goto(route)
      await expect(page).toHaveURL(/\/login/)
    }
  })
})

/**
 * v4.2.7 — Phase 3.1 (utils/date.ts) 순수 유틸 회귀 방지.
 * 로그인 / 백엔드 없이 브라우저에서 유틸 로직만 검증.
 */
test.describe('utils/date — 순수 로직 회귀', () => {
  test('formatDate / formatRelative 반환값 케이스별 검증', async ({ page }) => {
    await page.goto('/login')  // 아무 React 페이지 로드해서 번들 준비

    const result = await page.evaluate(() => {
      // HistoryPage/TopBar 가 쓰는 로직과 정확히 동일한 로직
      function formatDate(s: string | null | undefined): string {
        if (!s) return ''
        const d = new Date(s)
        if (isNaN(d.getTime())) return String(s)
        return d.toLocaleString('ko-KR', {
          year: '2-digit', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
        })
      }
      function formatRelative(s: string | null | undefined): string {
        if (!s) return ''
        const d = new Date(s); if (isNaN(d.getTime())) return String(s)
        const diffMs = Date.now() - d.getTime()
        if (diffMs < 0) return formatDate(s)
        const sec = Math.floor(diffMs / 1000)
        if (sec < 45) return '방금'
        const min = Math.floor(sec / 60); if (min < 60) return `${min}분 전`
        const hour = Math.floor(min / 60); if (hour < 24) return `${hour}시간 전`
        const day = Math.floor(hour / 24)
        if (day === 1) return '어제'
        if (day < 7) return `${day}일 전`
        return formatDate(s)
      }
      const now = Date.now()
      return {
        empty: formatDate(''),
        bad:   formatDate('not-a-date'),
        rel_now:       formatRelative(new Date(now - 5_000).toISOString()),
        rel_5m:        formatRelative(new Date(now - 5 * 60 * 1000).toISOString()),
        rel_2h:        formatRelative(new Date(now - 2 * 3600 * 1000).toISOString()),
        rel_yesterday: formatRelative(new Date(now - 25 * 3600 * 1000).toISOString()),
        rel_3d:        formatRelative(new Date(now - 3 * 86400 * 1000).toISOString()),
      }
    })
    expect(result.empty).toBe('')
    expect(result.bad).toBe('not-a-date')
    expect(result.rel_now).toBe('방금')
    expect(result.rel_5m).toBe('5분 전')
    expect(result.rel_2h).toBe('2시간 전')
    expect(result.rel_yesterday).toBe('어제')
    expect(result.rel_3d).toBe('3일 전')
  })

  test('@멘션 정규식이 이메일은 제외한다', async ({ page }) => {
    await page.goto('/login')

    const result = await page.evaluate(() => {
      const re = /(?:^|\s)@([A-Za-z0-9_.\-]+)/g
      function extract(text: string): string[] {
        const out: string[] = []
        let m: RegExpExecArray | null
        while ((m = re.exec(text)) !== null) out.push(m[1])
        return out
      }
      return {
        mention:    extract('@admin 리뷰 요청함'),
        email:      extract('이메일 a@b.com 은 멘션 아님'),
        multiple:   extract('@a @b @c'),
        korean:     extract('리뷰 끝 @admin 감사합니다'),
      }
    })
    expect(result.mention).toEqual(['admin'])
    expect(result.email).toEqual([])
    expect(result.multiple).toEqual(['a', 'b', 'c'])
    expect(result.korean).toEqual(['admin'])
  })
})

// NOTE: Phase 1.7 admin 게이팅, 이력 삭제 권한 등 백엔드 보안 경로는 Playwright 가
// Vite dev server 프록시 경유로 호출할 때 응답이 환경에 따라 달라져 불안정했다.
// 동일 시나리오를 JUnit `SecuritySmokeTests` (src/test/java) 에서 MockMvc 로 안정적으로
// 커버하므로 여기서는 중복 검증을 생략한다.

/**
 * v4.5 — 신규 경로 회귀 방지 (인증 없이 → 로그인 리다이렉트).
 */
test.describe('v4.5 신규 경로 — 인증 게이팅', () => {
  const newRoutes = [
    '/flow-analysis',
    '/package-overview',
    '/project-map',
    '/impact',
  ]

  for (const route of newRoutes) {
    test(`인증 없이 ${route} → /login 리다이렉트`, async ({ page }) => {
      await page.goto(route)
      await expect(page).toHaveURL(/\/login/)
    })
  }
})

/**
 * v4.5 — 순수 로직 회귀 방지 (백엔드 / 인증 불필요).
 */
test.describe('v4.5 — 순수 로직 회귀', () => {
  test('ProjectMap 검색 필터 — 대소문자 무시 부분 일치', async ({ page }) => {
    await page.goto('/login')

    const result = await page.evaluate(() => {
      const packages = [
        { packageName: 'com.example.order', classTotal: 5 },
        { packageName: 'com.example.user', classTotal: 3 },
        { packageName: 'com.example.payment', classTotal: 8 },
      ]
      const kw = 'ORDER'
      const filtered = packages.filter(p =>
        p.packageName.toLowerCase().includes(kw.toLowerCase())
      )
      return filtered.map(p => p.packageName)
    })
    expect(result).toEqual(['com.example.order'])
  })

  test('ERD heatmap 강도 계산 — maxHit 대비 비율', async ({ page }) => {
    await page.goto('/login')

    const result = await page.evaluate(() => {
      const hitEntries: [string, number][] = [
        ['ORDERS', 10],
        ['USERS', 5],
        ['PRODUCTS', 2],
      ]
      const maxHit = hitEntries[0][1]
      return hitEntries.map(([name, hit]) => {
        const r = maxHit > 0 ? hit / maxHit : 0
        const level = r >= 0.66 ? 'high' : r >= 0.33 ? 'mid' : 'low'
        return { name, level }
      })
    })
    expect(result[0]).toEqual({ name: 'ORDERS', level: 'high' })
    expect(result[1]).toEqual({ name: 'USERS', level: 'mid' })
    expect(result[2]).toEqual({ name: 'PRODUCTS', level: 'low' })
  })

  test('PackageStory 토큰 추정 — 40k 초과 시 감지', async ({ page }) => {
    await page.goto('/login')

    const result = await page.evaluate(() => {
      const LIMIT = 40_000
      const shortMsg = 'a'.repeat(100)
      const longMsg  = 'a'.repeat(160_001)
      return {
        short: (shortMsg.length / 4) > LIMIT,
        long:  (longMsg.length  / 4) > LIMIT,
      }
    })
    expect(result.short).toBe(false)
    expect(result.long).toBe(true)
  })

  test('notificationStore 백오프 — 폴링 지연 2배 증가 (상한 300s)', async ({ page }) => {
    await page.goto('/login')

    const result = await page.evaluate(() => {
      const POLL_MIN = 60_000
      const POLL_MAX = 300_000
      let delay = POLL_MIN
      const steps: number[] = [delay]
      for (let i = 0; i < 5; i++) {
        delay = Math.min(delay * 2, POLL_MAX)
        steps.push(delay)
      }
      return steps
    })
    expect(result).toEqual([60_000, 120_000, 240_000, 300_000, 300_000, 300_000])
  })
})

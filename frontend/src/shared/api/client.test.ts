import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { api, ApiError, clearSession, getAccessToken, setAccessToken, setSessionExpiredHandler } from './client'

/**
 * 401 재발급 큐잉 (12-frontend-plan.md §6.3-1·2).
 * 동시 401이 refresh를 한 번만 부르는지가 핵심이다 — 여러 번 부르면 회전된 토큰이 동시에 쓰여
 * 서버가 REUSE_DETECTED로 세션 전체를 폐기한다.
 */

const BASE = 'http://localhost/api/v1'
const server = setupServer()

const unauthorized = () => HttpResponse.json({ code: 'TOKEN_EXPIRED', message: '', fieldErrors: [] }, { status: 401 })

beforeAll(() => {
  api.defaults.baseURL = BASE
  server.listen({ onUnhandledRequest: 'error' })
})
afterEach(() => {
  server.resetHandlers()
  clearSession()
  setSessionExpiredHandler(() => {})
})
afterAll(() => server.close())

describe('401 재발급 큐잉', () => {
  it('동시 401 여러 건에 refresh는 한 번만 부르고, 전부 새 토큰으로 재시도한다', async () => {
    let refreshCalls = 0
    server.use(
      http.get(`${BASE}/customers`, ({ request }) =>
        request.headers.get('Authorization') === 'Bearer new' ? HttpResponse.json({ ok: true }) : unauthorized(),
      ),
      http.post(`${BASE}/auth/refresh`, () => {
        refreshCalls += 1
        return HttpResponse.json({ accessToken: 'new' })
      }),
    )
    setAccessToken('old')

    const results = await Promise.all([api.get('/customers'), api.get('/customers'), api.get('/customers')])

    expect(results.map((r) => r.data)).toEqual([{ ok: true }, { ok: true }, { ok: true }])
    expect(refreshCalls).toBe(1)
    expect(getAccessToken()).toBe('new')
  })

  it('refresh가 실패하면 세션을 비우고 만료 핸들러를 부른다', async () => {
    server.use(
      http.get(`${BASE}/customers`, () => unauthorized()),
      http.post(`${BASE}/auth/refresh`, () =>
        HttpResponse.json({ code: 'REFRESH_TOKEN_NOT_ACTIVE', message: '', fieldErrors: [] }, { status: 401 }),
      ),
    )
    const expired = vi.fn()
    setSessionExpiredHandler(expired)
    setAccessToken('old')

    await expect(api.get('/customers')).rejects.toBeInstanceOf(ApiError)

    expect(expired).toHaveBeenCalled()
    expect(getAccessToken()).toBeNull()
  })

  it('재시도는 한 번만 한다 — 새 토큰으로도 401이면 refresh를 다시 부르지 않는다', async () => {
    let refreshCalls = 0
    server.use(
      http.get(`${BASE}/customers`, () => unauthorized()),
      http.post(`${BASE}/auth/refresh`, () => {
        refreshCalls += 1
        return HttpResponse.json({ accessToken: 'new' })
      }),
    )
    setAccessToken('old')

    await expect(api.get('/customers')).rejects.toBeInstanceOf(ApiError)
    expect(refreshCalls).toBe(1)
  })
})

describe('에러 정규화', () => {
  it('ErrorResponse를 ApiError(code · status · fieldErrors)로 바꾼다', async () => {
    server.use(
      http.post(`${BASE}/customers`, () =>
        HttpResponse.json(
          { code: 'VALIDATION_FAILED', message: '', fieldErrors: [{ field: 'name', reason: '고객사명을 입력해 주세요.' }] },
          { status: 400 },
        ),
      ),
    )

    const error = await api.post('/customers', {}).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('VALIDATION_FAILED')
    expect((error as ApiError).status).toBe(400)
    expect((error as ApiError).reasonOf('name')).toBe('고객사명을 입력해 주세요.')
  })

  it('네트워크 단절은 INTERNAL_ERROR로 채운다 — 화면이 code 없는 에러를 만나지 않게', async () => {
    server.use(http.get(`${BASE}/customers`, () => HttpResponse.error()))

    const error = await api.get('/customers').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('INTERNAL_ERROR')
  })
})

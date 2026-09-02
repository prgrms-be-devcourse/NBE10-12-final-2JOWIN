import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import type { ErrorResponse } from './types'

/**
 * API 클라이언트 — 12-frontend-plan.md §6.3의 8개 항목을 여기서 처리한다.
 *
 * 가장 중요한 것은 **재발급 큐잉**이다. access가 만료된 뒤 여러 요청이 동시에 401을 받으면,
 * 각자 refresh를 호출하는 순간 회전된 토큰이 동시에 쓰인다 → 서버가 재사용으로 판단해
 * **세션 전체를 폐기한다**(REUSE_DETECTED, 전이표 §9). 그래서 refresh는 한 번만 부르고
 * 나머지는 그 약속을 기다렸다가 새 토큰으로 재시도한다.
 *
 * 토큰 취급 규칙 (§6.3-6·7·8):
 *  - refresh는 **HttpOnly 쿠키**(`2jo_rt`)라 JS가 접근할 수 없다. 그게 목적이다
 *  - access는 **메모리에만** 둔다 — localStorage에 넣는 코드는 리뷰에서 막는다
 *  - 새로고침하면 access가 사라지므로, 부팅 시 `restoreSession()`으로 한 번 복구한다
 */

export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

export const api = axios.create({
  baseURL: API_BASE_URL,
  // 인증 계열 요청(/auth/*)만 쿠키가 필요하지만, 서브도메인 분리(SameSite=Lax) 구성에서는
  // 전역 true여도 Path 한정(2jo_rt → /api/v1/auth) 덕에 일반 요청에는 쿠키가 실리지 않는다
  withCredentials: true,
})

/** 계정 없는 고객이 쓰는 경로 — 토큰이 곧 인증이라 Authorization도 쿠키도 붙이지 않는다 */
export const publicApi = axios.create({ baseURL: '/public/api/v1' })

// ── access token (메모리) ────────────────────────────────────────────────────

let accessToken: string | null = null

export const setAccessToken = (token: string | null) => {
  accessToken = token
}
export const getAccessToken = () => accessToken

/** 로그아웃 — 서버가 쿠키를 지우므로 프론트는 메모리만 비운다 (§6.3-8) */
export function clearSession() {
  accessToken = null
}

// ── 재발급 큐잉 ──────────────────────────────────────────────────────────────

/** 진행 중인 refresh 약속. null이면 아직 아무도 부르지 않았다는 뜻 */
let refreshing: Promise<string> | null = null

/** 세션이 끊겼을 때 앱이 할 일 — 라우터를 모르는 모듈이라 주입받는다 */
let onSessionExpired: () => void = () => {}
export const setSessionExpiredHandler = (handler: () => void) => {
  onSessionExpired = handler
}

function refreshAccessToken(): Promise<string> {
  refreshing ??= api
    .post('/auth/refresh')
    .then((response) => {
      const token: string = response.data.accessToken
      setAccessToken(token)
      return token
    })
    .finally(() => {
      // 성공이든 실패든 다음 만료 때 다시 시도할 수 있도록 비운다
      refreshing = null
    })
  return refreshing
}

/** 부팅 시 1회 — 쿠키가 살아 있으면 로그인 상태가 이어진다 (§6.3-7) */
export async function restoreSession(): Promise<boolean> {
  try {
    await refreshAccessToken()
    return true
  } catch {
    return false
  }
}

// ── 인터셉터 ────────────────────────────────────────────────────────────────

api.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
  return config
})

interface RetriableConfig extends AxiosRequestConfig {
  /** 재시도는 한 번만 — 무한 루프 방지 */
  _retried?: boolean
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ErrorResponse>) => {
    const config = error.config as RetriableConfig | undefined
    const code = error.response?.data?.code

    // refresh 자체가 실패하면 되살릴 방법이 없다 — 로그인 화면으로 (AU-12)
    if (config?.url === '/auth/refresh') {
      clearSession()
      onSessionExpired()
      return Promise.reject(toApiError(error))
    }

    if (error.response?.status === 401 && config && !config._retried) {
      config._retried = true
      try {
        const token = await refreshAccessToken() // 동시 401은 이 약속 하나를 함께 기다린다
        config.headers = { ...config.headers, Authorization: `Bearer ${token}` }
        return api.request(config)
      } catch {
        clearSession()
        onSessionExpired()
      }
    }

    if (code === 'REFRESH_TOKEN_NOT_ACTIVE') {
      clearSession()
      onSessionExpired()
    }

    return Promise.reject(toApiError(error))
  },
)

publicApi.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ErrorResponse>) => Promise.reject(toApiError(error)),
)

// ── 에러 정규화 ──────────────────────────────────────────────────────────────

/**
 * 화면은 `error.code`만 보고 문구는 `messageOf(code)`로 얻는다 (§6.3-3).
 * 서버가 죽었거나 네트워크가 끊긴 경우에도 code가 비지 않도록 INTERNAL_ERROR로 채운다.
 */
export class ApiError extends Error {
  readonly code: string
  readonly status: number | undefined
  readonly fieldErrors: ErrorResponse['fieldErrors']

  constructor(code: string, status: number | undefined, fieldErrors: ErrorResponse['fieldErrors'] = []) {
    super(code)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.fieldErrors = fieldErrors
  }

  /** 필드별 오류를 TextField 아래에 붙일 때 쓴다 (§6.3-표 400행) */
  reasonOf(field: string): string | undefined {
    return this.fieldErrors.find((fe) => fe.field === field)?.reason
  }
}

function toApiError(error: AxiosError<ErrorResponse>): ApiError {
  const body = error.response?.data
  return new ApiError(body?.code ?? 'INTERNAL_ERROR', error.response?.status, body?.fieldErrors ?? [])
}

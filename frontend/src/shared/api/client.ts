import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig } from 'axios'
import type { ErrorResponse } from './types'

/**
 * API 클라이언트 — 12-frontend-plan.md §6.3의 8개 항목을 여기서 처리한다.
 *
 * 가장 중요한 것은 재발급 큐잉이다. access가 만료된 뒤 여러 요청이 동시에 401을 받으면,
 * 각자 refresh를 호출하는 순간 회전된 토큰이 동시에 쓰인다 → 서버가 재사용으로 판단해
 * 세션 전체를 폐기한다(REUSE_DETECTED, 전이표 §9). 그래서 refresh는 한 번만 부르고
 * 나머지는 그 약속을 기다렸다가 새 토큰으로 재시도한다.
 *
 * 토큰 취급 규칙 (§6.3-6·7·8):
 *  - refresh는 HttpOnly 쿠키(`2jo_rt` · 관리자 `2jo_admin_rt`)라 JS가 접근할 수 없다. 그게 목적이다
 *  - access는 메모리에만 둔다 — localStorage에 넣는 코드는 리뷰에서 막는다
 *  - 새로고침하면 access가 사라진다. 첫 요청의 401을 인터셉터가 refresh로 복구한다
 *
 * 구성원(/api/v1)과 플랫폼 관리자(/admin/api/v1)는 **세션이 따로**다 (AU-08 · Q-28) —
 * 쿠키 이름·Path가 분리돼 한 브라우저에서 두 세션이 공존하므로 클라이언트도 둘로 나눈다.
 */

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

/** unknown → ApiError.code. 화면의 catch 블록에서 반복되는 `instanceof` 분기를 줄인다 */
export const codeOf = (error: unknown): string => (error instanceof ApiError ? error.code : 'INTERNAL_ERROR')

function toApiError(error: AxiosError<ErrorResponse>): ApiError {
  const body = error.response?.data
  return new ApiError(body?.code ?? 'INTERNAL_ERROR', error.response?.status, body?.fieldErrors ?? [])
}

// ── 인증 세션 클라이언트 (구성원 · 플랫폼 관리자 공용 골격) ──────────────────

interface RetriableConfig extends AxiosRequestConfig {
  /** 재시도는 한 번만 — 무한 루프 방지 */
  _retried?: boolean
}

interface AuthedClient {
  api: AxiosInstance
  setAccessToken: (token: string | null) => void
  getAccessToken: () => string | null
  clearSession: () => void
  setSessionExpiredHandler: (handler: () => void) => void
  /** 새로고침 직후처럼 access가 없을 때 쿠키로 세션을 되살린다 — 실패하면 ApiError */
  refresh: () => Promise<string>
}

function createAuthedClient(baseURL: string): AuthedClient {
  const api = axios.create({
    baseURL,
    // 인증 계열 요청(/auth/*)만 쿠키가 필요하지만, 서브도메인 분리(SameSite=Lax) 구성에서는
    // 전역 true여도 Path 한정 덕에 일반 요청에는 쿠키가 실리지 않는다
    withCredentials: true,
  })

  let accessToken: string | null = null
  /** 진행 중인 refresh 약속. null이면 아직 아무도 부르지 않았다는 뜻 */
  let refreshing: Promise<string> | null = null
  /** 세션이 끊겼을 때 앱이 할 일 — 라우터를 모르는 모듈이라 주입받는다 */
  let onSessionExpired: () => void = () => {}

  const clearSession = () => {
    accessToken = null
  }

  function refreshAccessToken(): Promise<string> {
    refreshing ??= api
      .post('/auth/refresh')
      .then((response) => {
        const token: string = response.data.accessToken
        accessToken = token
        return token
      })
      .finally(() => {
        // 성공이든 실패든 다음 만료 때 다시 시도할 수 있도록 비운다
        refreshing = null
      })
    return refreshing
  }

  api.interceptors.request.use((config) => {
    if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
    return config
  })

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

  return {
    api,
    setAccessToken: (token) => {
      accessToken = token
    },
    getAccessToken: () => accessToken,
    clearSession,
    setSessionExpiredHandler: (handler) => {
      onSessionExpired = handler
    },
    refresh: refreshAccessToken,
  }
}

// ── 구성원 (/api/v1) ────────────────────────────────────────────────────────

export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

const member = createAuthedClient(API_BASE_URL)

export const api = member.api
export const setAccessToken = member.setAccessToken
export const getAccessToken = member.getAccessToken
/** 로그아웃 — 서버가 쿠키를 지우므로 프론트는 메모리만 비운다 (§6.3-8) */
export const clearSession = member.clearSession
export const setSessionExpiredHandler = member.setSessionExpiredHandler

// ── 플랫폼 관리자 (/admin/api/v1) — 별도 세션 (AU-08) ───────────────────────

export const ADMIN_API_BASE_URL: string = import.meta.env.VITE_ADMIN_API_BASE_URL ?? '/admin/api/v1'

const admin = createAuthedClient(ADMIN_API_BASE_URL)

export const adminApi = admin.api
export const setAdminAccessToken = admin.setAccessToken
export const getAdminAccessToken = admin.getAccessToken
export const clearAdminSession = admin.clearSession
export const setAdminSessionExpiredHandler = admin.setSessionExpiredHandler
/** 관리자에게는 /me가 없다(09 본인 계정 행) — 새로고침 후 세션 확인은 refresh 성공 여부로 한다 */
export const refreshAdminSession = admin.refresh

// ── 고객 · 방문자 (/public/api/v1) ──────────────────────────────────────────

/** 계정 없는 고객이 쓰는 경로 — 토큰이 곧 인증이라 Authorization도 쿠키도 붙이지 않는다 */
export const PUBLIC_API_BASE_URL: string = import.meta.env.VITE_PUBLIC_API_BASE_URL ?? '/public/api/v1'

export const publicApi = axios.create({ baseURL: PUBLIC_API_BASE_URL })

publicApi.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ErrorResponse>) => Promise.reject(toApiError(error)),
)

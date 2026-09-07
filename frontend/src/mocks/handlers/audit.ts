import type { RequestHandler } from 'msw'

/** 감사 로그 목 자리 — 감사 도메인 PR에서 교체된다 (AC-11) */
export const auditHandlers: RequestHandler[] = []

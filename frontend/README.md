# 2JO 프론트엔드

구성원 앱 · 관리자 페이지 · 고객 열람 페이지를 하나의 SPA로 제공한다.
계획은 `docs/12-frontend-plan.md`, 화면 설계는 `docs/10-screen-design.md`가 정본이다.

## 실행

```bash
npm install
npm run dev        # http://localhost:5173 — MSW 목으로 동작
npm test           # vitest
npm run lint       # oxlint
npm run build      # tsc + vite build
```

목 계정 (`src/mocks/fixtures.ts`)

| 이메일 | 비밀번호 | 역할 |
| --- | --- | --- |
| `seoyeon@hanbit.co.kr` | `test1234!` | 기업 관리자 |
| `jihun@hanbit.co.kr` | `test1234!` | 영업 담당자 |

데모 링크: 고객 열람 `/q/demo-dodam-14` · 만료 `/q/demo-mirae-05` · 초대 `/invite/demo-invite`

## 구조 (12 §6.1)

```
src/
  app/            라우터 · AuthGuard · session · layouts · theme.css
  shared/
    api/          client(인터셉터) · errors(부록 상수) · types(DTO 미러)
    ui/           PageHeader · EmptyState · ErrorCallout · ConfirmDialog · Money · StatusBadge
    lib/          날짜·금액 포맷
    brand/        로고 · 문구 · 색
  domains/{도메인}/
    api.ts        이 도메인의 HTTP 호출 — 다른 곳에서 fetch 금지
    hooks.ts      TanStack Query 훅 (queryKey: [도메인, 리소스, 파라미터])
    components/
    pages/
  mocks/
    handlers/     도메인별 MSW 핸들러
    fixtures.ts   시연 데이터 = 백엔드 시드
```

도메인 폴더는 백엔드 모듈과 1:1이고 소유자도 같다. 다른 도메인의 코드를 import하지 않는다 —
공유가 필요하면 `shared/`로, 다른 도메인의 데이터는 API 응답으로 받는다.

## 내 화면 붙이기

1. `src/domains/customer/`를 열어 구조를 본다. 목록(`pages/CustomerListPage.tsx`)과 상세(`pages/CustomerDetailPage.tsx`)가 예제다.
2. `src/domains/{도메인}/`에 `api.ts` → `hooks.ts` → `components/` → `pages/` 순으로 만든다.
3. `src/shared/api/types.ts`에 DTO를 `docs/08-dto.md`에서 옮겨 적는다. 화면에서 임의 타입을 만들지 않는다.
4. `src/app/router.tsx`의 Placeholder를 내 페이지로 바꾼다.
5. 공통 컴포넌트는 `/_ui`(개발 서버에서만)에서 실제 렌더링을 보고 고른다. 없으면 `shared/ui`에 추가한다.

## 내 도메인 목 만들기

1. `src/mocks/handlers/customer.ts`를 본떠 `handlers/{도메인}.ts`를 만든다.
   - 응답은 08 DTO와 1:1 · 데이터는 `fixtures.ts`에서만 · 에러는 공통 `ErrorResponse` · 실패 경로(400·404·409) 포함
2. `handlers/index.ts`의 `BY_DOMAIN`에 등록한다.
3. 백엔드가 준비되면 `.env.development`의 `VITE_MOCK_DOMAINS`에서 내 도메인 이름을 뺀다. 요청이 Vite 프록시를 거쳐 `localhost:8080`으로 간다. 코드는 바꾸지 않는다.

`auth`를 빼야 실제 로그인이 되므로, 다른 도메인보다 먼저 전환한다.

## 규칙 (12 §8 · 13 §2)

- 색·간격은 Radix 토큰만. 임의 hex 금지. 의미 색은 blue(기본)·amber(주목)·green(성공)·red(위험) 네 가지 (10 §2.3)
- 에러 문구는 `messageOf(code)`만. 화면에서 문구를 새로 쓰지 않는다
- access 토큰은 메모리에만. `localStorage`에 넣지 않는다
- 401 재발급 큐잉은 `client.ts`가 처리한다. 화면은 신경 쓰지 않는다

## 환경변수

| 변수 | 개발 | 배포(Vercel) |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `/api/v1` (프록시) | `https://api.…/api/v1` |
| `VITE_PUBLIC_API_BASE_URL` | `/public/api/v1` | `https://api.…/public/api/v1` |
| `VITE_MOCK_DOMAINS` | 목으로 돌릴 도메인 목록 | 사용 안 함 (프로덕션에서 MSW 비활성) |

배포 시 `vercel.json`의 CSP `connect-src`를 API 도메인으로 바꾼다.

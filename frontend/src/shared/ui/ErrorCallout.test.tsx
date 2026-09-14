import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { ErrorCallout } from './ErrorCallout'

/**
 * [새로고침]은 폼 안에 놓여도 제출하지 않아야 한다 (#361).
 *
 * DealFormDialog는 이 컴포넌트를 `<form onSubmit>` 안에 둔다. 버튼에 type이 없으면 기본 submit이라
 * 누르는 순간 재조회와 함께 **옛 version으로 저장 요청이 한 번 더** 나가 409가 또 났다.
 * 클릭 이벤트를 흉내 낼 DOM 도구가 없어 마크업의 type 속성으로 고정한다 — 브라우저가 제출 여부를 그 속성으로 정한다.
 */
describe('ErrorCallout [새로고침]', () => {
  const refreshButton = (html: string) => html.match(/<button[^>]*>[^<]*새로고침[^<]*<\/button>/)?.[0]

  it('STALE_VERSION에 onRetry를 넘기면 type="button"인 새로고침 버튼이 붙는다', () => {
    const html = renderToStaticMarkup(
      <form>
        <ErrorCallout code="STALE_VERSION" onRetry={() => {}} />
      </form>,
    )

    expect(refreshButton(html)).toContain('type="button"')
  })

  it('다른 코드이거나 onRetry가 없으면 새로고침 버튼이 없다', () => {
    expect(refreshButton(renderToStaticMarkup(<ErrorCallout code="QUOTE_NOT_DRAFT" onRetry={() => {}} />))).toBeUndefined()
    expect(refreshButton(renderToStaticMarkup(<ErrorCallout code="STALE_VERSION" />))).toBeUndefined()
  })
})

import { useNavigate, useParams } from 'react-router'
import { Box, Button, Callout, Flex } from '@radix-ui/themes'
import { EyeOpenIcon } from '@radix-ui/react-icons'
import { BackLink, NotFound } from '../../../shared/ui'
import { codeOf } from '../../../shared/api/client'
import { useQuotePreview } from '../hooks'
import { QuoteDocument } from '../components/QuoteDocument'
import { QuoteSkeleton } from './QuoteViewPage'

/**
 * 견적 미리보기 (QT-12) — `GET /quotes/{id}/preview`는 고객 열람과 같은 PublicQuoteResponse를 준다 (08 §C 주석).
 * 화면도 `QuoteDocument`를 그대로 써서 "고객이 보는 화면과 동일한 구성"을 보장한다.
 * 담당자 블록은 지금 시점의 Deal 담당자다 (AP-18) — 발송 뒤 담당자가 바뀌면 고객 화면도 함께 바뀐다.
 */
export function QuotePreviewPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { data, isPending, error, refetch } = useQuotePreview(id)

  return (
    <Box mx="auto" style={{ maxWidth: 720 }}>
      <BackLink to={`/quotes/${id}`} label="견적으로" />
      <Callout.Root color="blue" mb="4" size="1">
        <Callout.Icon>
          <EyeOpenIcon />
        </Callout.Icon>
        <Callout.Text>고객이 보는 화면입니다. 승인·반려 버튼은 고객 링크에서만 나타납니다.</Callout.Text>
      </Callout.Root>

      {isPending ? (
        <QuoteSkeleton />
      ) : error || !data ? (
        <NotFound code={codeOf(error)} backLabel="견적 목록으로" onBack={() => navigate('/quotes')} onRetry={() => refetch()} />
      ) : !data.companyName || !data.assignee ? (
        // #114 전까지 서버 미리보기는 boundary PublicQuoteView 모양이라 회사·담당자 블록이 없다 — 크래시 대신 안내.
        // #114가 머지되면 이 분기와 함께 지운다
        <Callout.Root color="amber" size="2">
          <Callout.Text>
            미리보기 응답이 아직 고객 화면 형식이 아닙니다 (백엔드 #114 대기). 금액·항목은 견적 상세에서 확인해 주세요.
          </Callout.Text>
          <Flex justify="center" mt="3" className="no-print">
            <Button size="2" variant="soft" onClick={() => navigate(`/quotes/${id}`)}>견적으로 돌아가기</Button>
          </Flex>
        </Callout.Root>
      ) : (
        <QuoteDocument quote={data} showRemaining={data.status === 'DRAFT' || data.status === 'SENT' || data.status === 'VIEWED'}>
          <Flex justify="center" mt="5" className="no-print">
            <Button size="3" variant="soft" onClick={() => navigate(`/quotes/${id}`)}>
              {data.status === 'DRAFT' ? '편집으로 돌아가기' : '견적으로 돌아가기'}
            </Button>
          </Flex>
        </QuoteDocument>
      )}
    </Box>
  )
}

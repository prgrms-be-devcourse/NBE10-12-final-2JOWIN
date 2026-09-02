import { useState, type ReactNode } from 'react'
import {
  Badge, Box, Button, Callout, Card, Code, Flex, Grid, Heading, Separator,
  Table, Text, TextField,
} from '@radix-ui/themes'
import { InfoCircledIcon, PlusIcon } from '@radix-ui/react-icons'
import {
  AutoBadge, ConfirmDialog, DealStageBadge, DEAL_STAGES, EmptyState, ErrorCallout,
  Money, PageHeader, QuoteStatusBadge, QUOTE_STATUSES, RemainingBadge, ViewedBadge,
  type DealStage, type QuoteStatus,
} from '../shared/ui'
import { dateShort } from '../shared/lib/format'
import { deals, quotes } from '../mocks/fixtures'

/**
 * 공통 컴포넌트 갤러리 — `/_ui`.
 *
 * 화면을 만들기 전에 여기서 고른다. 같은 뜻을 다른 모양으로 만들지 않는 것이 목적이고,
 * 여기 없는 것이 필요하면 화면에서 새로 만들지 말고 `shared/ui`에 추가한다.
 * (10-screen-design.md §6 · 12-frontend-plan.md §6.5)
 */
export function UiGallery() {
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [motionKey, setMotionKey] = useState(0)

  return (
    <Box>
      <PageHeader
        title="공통 컴포넌트"
        badge={<Badge color="gray">개발 참고</Badge>}
        description="화면은 여기 있는 것으로 조립한다. 색·간격은 Radix 토큰만 쓰고 임의 hex는 리뷰에서 막는다."
        actions={<Button variant="soft">문서 보기</Button>}
      />

      <Callout.Root color="blue" mb="5">
        <Callout.Icon><InfoCircledIcon /></Callout.Icon>
        <Callout.Text>
          정본은 <Code>docs/10-screen-design.md</Code>다. 이 페이지는 그것을 코드로 옮긴 것이고,
          둘이 어긋나면 문서가 맞다.
        </Callout.Text>
      </Callout.Root>

      <Section title="1. 색의 뜻 — 하나의 색은 하나의 뜻만" note="§2.3">
        <Grid columns={{ initial: '2', sm: '5' }} gap="3">
          <Swatch color="blue" meaning="기본 · 주요 동작" where="주요 버튼, 활성 탭" />
          <Swatch color="amber" meaning="주목 · 봐야 할 것" where="열람됨, 되돌릴 수 없는 안내" />
          <Swatch color="green" meaning="성공 · 성사" where="성사 단계, 이달 성사 금액" />
          <Swatch color="red" meaning="위험 · 이미 늦은 것" where="비활성화, 마감 초과, 미읽음" />
          <Swatch color="gray" meaning="중립" where="나머지 전부" />
        </Grid>
        <Text as="p" size="2" color="gray" mt="3">
          amber와 red를 섞지 않는 것이 규칙이다 — amber는 "확인이 필요하다", red는 "되돌릴 수 없거나 이미 늦었다".
        </Text>
      </Section>

      <Section title="2. 상태 배지" note="§6.1 · 색 + 아이콘 + 텍스트를 항상 함께 낸다">
        <Row label="딜 단계">
          {DEAL_STAGES.map((s) => <DealStageBadge key={s} stage={s as DealStage} />)}
          <Separator orientation="vertical" />
          <DealStageBadge stage="QUOTE" current />
          <Text size="1" color="gray">← 딜 보드의 현재 단계만 solid</Text>
        </Row>
        <Row label="견적 상태">
          {QUOTE_STATUSES.map((s) => <QuoteStatusBadge key={s} status={s as QuoteStatus} />)}
        </Row>
        <Row label="열람 여부">
          <ViewedBadge firstViewedAt="2026-08-25T05:20:00Z" sentAt="2026-08-24T01:00:00Z" />
          <ViewedBadge firstViewedAt={null} sentAt="2026-08-23T00:00:00Z" />
          <Text size="1" color="gray">← 안 봤다와 봤는데 답이 없다는 다음 행동이 다르다 (AP-06)</Text>
        </Row>
        <Row label="기타">
          <RemainingBadge until="2026-09-09" />
          <RemainingBadge until="2026-08-20" />
          <AutoBadge />
        </Row>
      </Section>

      <Section title="3. 금액" note="원 단위 정수 · tabular-nums">
        <Flex gap="5" align="baseline" wrap="wrap">
          <Flex direction="column">
            <Text size="1" color="gray">기본</Text>
            <Money value={3355000} size="4" />
          </Flex>
          <Flex direction="column">
            <Text size="1" color="gray">단위 표시</Text>
            <Money value={3355000} unit size="4" weight="bold" />
          </Flex>
          <Flex direction="column">
            <Text size="1" color="gray">축약 (요약 자리에만)</Text>
            <Money value={48400000} short size="4" />
          </Flex>
          <Flex direction="column">
            <Text size="1" color="gray">억 단위</Text>
            <Money value={230000000} short size="4" />
          </Flex>
        </Flex>
      </Section>

      <Section title="4. 표 — 목록 화면의 기본형" note="실제 목 픽스처 = 백엔드 시드와 같은 데이터">
        <Card>
          <Table.Root variant="ghost" size="2">
            <Table.Header>
              <Table.Row>
                <Table.ColumnHeaderCell>견적번호</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>딜</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>상태</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>열람</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell align="right">금액</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>유효기간</Table.ColumnHeaderCell>
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {quotes.slice(0, 6).map((q) => (
                <Table.Row key={q.id}>
                  <Table.RowHeaderCell>
                    <Code variant="ghost">{q.quoteNo}</Code>
                  </Table.RowHeaderCell>
                  <Table.Cell>
                    <Text size="2">{deals.find((d) => d.id === q.dealId)?.title ?? '—'}</Text>
                  </Table.Cell>
                  <Table.Cell><QuoteStatusBadge status={q.status as QuoteStatus} /></Table.Cell>
                  <Table.Cell><ViewedBadge firstViewedAt={q.firstViewedAt} sentAt={q.sentAt} /></Table.Cell>
                  <Table.Cell align="right"><Money value={q.totalAmount} unit /></Table.Cell>
                  <Table.Cell><Text size="2" color="gray">{dateShort(`${q.validUntil}T00:00:00Z`)}</Text></Table.Cell>
                </Table.Row>
              ))}
            </Table.Body>
          </Table.Root>
        </Card>
      </Section>

      <Section title="5. 빈 화면" note="§6.2 — 없습니다가 아니라 다음 행동을 제안한다">
        <EmptyState
          icon={<PlusIcon width="28" height="28" />}
          title="아직 등록된 고객사가 없습니다"
          description="고객사를 등록하면 딜을 만들고 견적을 보낼 수 있습니다."
          action={{ label: '고객사 등록', onClick: () => {} }}
        />
      </Section>

      <Section title="6. 에러" note="§6.3 — 문구는 API 명세서 부록 상수만 쓴다">
        <ErrorCallout code="RESOURCE_NOT_FOUND" />
        <ErrorCallout code="STALE_VERSION" onRetry={() => {}} />
        <ErrorCallout code="LOGIN_LOCKED" />
        <Text as="p" size="2" color="gray">
          <Code>VALIDATION_FAILED</Code>의 <Code>fieldErrors</Code>는 Callout이 아니라 입력 아래에 붙인다:
        </Text>
        <Box maxWidth="280px">
          <Text as="label" size="2" weight="medium">이메일</Text>
          <TextField.Root defaultValue="not-an-email" color="red" mt="1" />
          <Text size="1" color="red" mt="1">올바른 이메일 형식이 아닙니다.</Text>
        </Box>
      </Section>

      <Section title="7. 확인 모달" note="§2.5 — 되돌릴 수 있는가로 Dialog와 AlertDialog를 가른다">
        <Flex gap="3" align="center">
          <Button color="green" onClick={() => setConfirmOpen(true)}>
            승인 모달 열기
          </Button>
          <Text size="2" color="gray">
            AlertDialog — ESC·바깥 클릭으로 닫히지 않고, 확인 버튼에 자동 포커스가 없다
          </Text>
        </Flex>
        <ConfirmDialog
          open={confirmOpen}
          onOpenChange={setConfirmOpen}
          title="견적을 승인하시겠습니까?"
          description="승인하면 이 링크로는 다시 응답할 수 없습니다."
          confirmLabel="승인합니다"
          confirmColor="green"
          onConfirm={() => setConfirmOpen(false)}
        >
          <Card variant="surface" mt="3">
            <Text as="div" size="2" color="gray">한빛오피스 · Q-2608-014</Text>
            <Money value={3355000} unit size="5" weight="bold" />
            <Text as="div" size="1" color="gray">부가세 별도</Text>
          </Card>
        </ConfirmDialog>
      </Section>

      <Section title="8. 모션" note="theme.css — 8px 이하 · 한 화면에 움직이는 덩어리 하나 · 되돌릴 수 없는 행동(승인·발송)엔 없음">
        <Row label="등장">
          <Button variant="soft" size="1" onClick={() => setMotionKey((k) => k + 1)}>
            다시 재생
          </Button>
          <Text size="1" color="gray">
            <Code>.enter</Code>(8px 위로)·<Code>.enter-fade</Code>는 화면·카드의 첫 등장에 — 컨테이너 하나에만 건다.
            목록은 <Code>.stagger</Code>(40ms 간격, 6개까지):
          </Text>
        </Row>
        <Grid key={motionKey} className="stagger" columns={{ initial: '1', sm: '3' }} gap="3">
          {['도담문구 사무용품 정기공급', '성원테크 복합기 임대', '대한상사 탕비실 물품'].map((title) => (
            <Card key={title} size="2">
              <Text as="div" size="2" weight="medium">{title}</Text>
              <Text as="div" size="1" color="gray">stagger 목록 예시</Text>
            </Card>
          ))}
        </Grid>
        <Row label="호버">
          <Card size="2" className="lift" style={{ cursor: 'pointer' }}>
            <Text as="div" size="2" weight="medium">누를 수 있는 카드</Text>
            <Text as="div" size="1" color="gray"><Code>.lift</Code> — 1px 상승 + 그림자. 클릭되는 카드에만</Text>
          </Card>
          <Text size="2" className="underline-grow" style={{ cursor: 'pointer' }}>
            underline-grow 링크
          </Text>
          <Text size="1" color="gray">← 내비게이션 링크용. 표 행은 <Code>.row-hover</Code>(배경색만)</Text>
        </Row>
        <Text as="p" size="2" color="gray">
          <Code>prefers-reduced-motion</Code> 사용자에겐 전부 꺼진다 — 개별 화면에서 따로 처리할 것 없음.
        </Text>
      </Section>

      <Section title="9. 하지 않는 것">
        <Flex direction="column" gap="2">
          <Dont>임의 hex 색 — <Code>#4F46E5</Code> 대신 <Code>color="blue"</Code></Dont>
          <Dont>에러 문구 새로 쓰기 — <Code>shared/api/errors.ts</Code>에서 가져온다</Dont>
          <Dont>화면에서 <Code>fetch</Code> 직접 호출 — <Code>domains/도메인/api.ts</Code> 안에서만</Dont>
          <Dont>화면에서 타입 새로 정의 — <Code>shared/api/types.ts</Code>가 DTO 미러</Dont>
          <Dont>권한 없는 버튼을 비활성화로 보여주기 — <b>숨긴다</b> (§3.2)</Dont>
          <Dont>4의 배수가 아닌 간격 — Radix space 1~9만 쓴다</Dont>
        </Flex>
      </Section>
    </Box>
  )
}

function Section({ title, note, children }: { title: string; note?: string; children: ReactNode }) {
  return (
    <Box mb="7">
      <Flex align="baseline" gap="2" mb="3">
        <Heading size="4">{title}</Heading>
        {note && <Text size="1" color="gray">{note}</Text>}
      </Flex>
      <Flex direction="column" gap="3">{children}</Flex>
      <Separator size="4" mt="6" />
    </Box>
  )
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Flex align="center" gap="2" wrap="wrap">
      <Text size="2" color="gray" style={{ width: 72, flexShrink: 0 }}>{label}</Text>
      {children}
    </Flex>
  )
}

function Swatch({ color, meaning, where }: { color: 'blue' | 'amber' | 'green' | 'red' | 'gray'; meaning: string; where: string }) {
  return (
    <Card size="1">
      <Box height="28px" mb="2" style={{ background: `var(--${color}-9)`, borderRadius: 'var(--radius-2)' }} />
      <Text as="div" size="2" weight="medium">{meaning}</Text>
      <Text as="div" size="1" color="gray">{where}</Text>
      <Code variant="ghost" size="1">{color}</Code>
    </Card>
  )
}

function Dont({ children }: { children: ReactNode }) {
  return (
    <Flex gap="2" align="baseline">
      <Text color="red" size="2">✕</Text>
      <Text size="2">{children}</Text>
    </Flex>
  )
}

import { Button, Card, Flex, Heading, Text } from '@radix-ui/themes'
import { useNavigate } from 'react-router'

/**
 * 시작하기 체크리스트 (10 §6.2 · GAP-01 완화).
 * 가입 직후 관리자가 처음 보는 대시보드는 비어 있다 — "없습니다" 대신 다음 행동을 제안한다.
 * 팀원 초대는 기업 관리자만 (MB-01) — 영업 담당자에게는 그 줄을 숨긴다.
 */
export function StartChecklist({ canInvite }: { canInvite: boolean }) {
  const navigate = useNavigate()
  const steps = [
    { label: '판매할 상품을 등록하세요', action: '등록하기', to: '/products' },
    ...(canInvite ? [{ label: '팀원을 초대하세요', action: '초대하기', to: '/members' }] : []),
    { label: '첫 고객사를 등록하세요', action: '등록하기', to: '/customers' },
    { label: '첫 견적을 보내보세요', action: '딜 만들기', to: '/deals' },
  ]
  return (
    <Card size="3" className="enter">
      <Heading size="4" mb="1">
        시작하기
      </Heading>
      <Text as="p" size="2" color="gray" mb="4">
        아직 진행 중인 딜이 없습니다. 아래 순서대로 준비하면 첫 견적까지 이어집니다.
      </Text>
      <Flex direction="column" gap="2">
        {steps.map((step, index) => (
          <Flex key={step.to} align="center" justify="between" gap="3" py="2" style={{ borderTop: index === 0 ? undefined : '1px solid var(--gray-a4)' }}>
            <Text size="2">
              {index + 1}. {step.label}
            </Text>
            <Button size="1" variant="soft" onClick={() => navigate(step.to)}>
              {step.action}
            </Button>
          </Flex>
        ))}
      </Flex>
    </Card>
  )
}

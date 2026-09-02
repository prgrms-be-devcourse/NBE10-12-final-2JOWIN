import { Card, Flex, Heading, Text } from '@radix-ui/themes'

/**
 * 아직 붙지 않은 화면 자리 — 담당자가 실제 화면으로 교체한다.
 * 레이아웃 안에 들어가므로 자체 높이를 강제하지 않는다.
 */
export function Placeholder({ title, note }: { title: string; note?: string }) {
  return (
    <Card size="3" className="enter-fade">
      <Flex direction="column" align="center" gap="2" py="8">
        <Heading size="5" color="gray">
          {title}
        </Heading>
        {note && (
          <Text size="2" color="gray">
            {note}
          </Text>
        )}
      </Flex>
    </Card>
  )
}

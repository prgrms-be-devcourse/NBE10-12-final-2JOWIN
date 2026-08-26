import { Card, Flex, Heading, Text } from '@radix-ui/themes'

/** 스캐폴드 확인용 임시 화면 — 실제 화면이 붙으면 삭제한다. */
export function Placeholder({ title, note }: { title: string; note?: string }) {
  return (
    <Flex align="center" justify="center" style={{ minHeight: '100vh' }}>
      <Card size="3">
        <Heading size="5">2JO · {title}</Heading>
        {note && (
          <Text as="p" size="2" color="gray" mt="2">
            {note}
          </Text>
        )}
      </Card>
    </Flex>
  )
}

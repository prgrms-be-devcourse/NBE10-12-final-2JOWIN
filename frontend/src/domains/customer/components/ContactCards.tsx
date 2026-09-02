import { Avatar, Badge, Box, Button, Card, Flex, Grid, Text } from '@radix-ui/themes'
import { EnvelopeClosedIcon, MobileIcon, Pencil1Icon, PlusIcon, StarFilledIcon, TrashIcon } from '@radix-ui/react-icons'
import type { ContactResponse } from '../../../shared/api/types'

interface Props {
  contacts: ContactResponse[]
  onAdd: () => void
  onEdit: (contact: ContactResponse) => void
  onDelete: (contact: ContactResponse) => void
  onSetPrimary: (contact: ContactResponse) => void
  /** 대표 지정 진행 중 — 버튼을 잠근다 */
  settingPrimary?: boolean
}

/** 담당자 카드 (CU-09~11, 10 §5.9). 대표가 먼저, 동작 버튼은 카드에 노출 */
export function ContactCards({ contacts, onAdd, onEdit, onDelete, onSetPrimary, settingPrimary = false }: Props) {
  const sorted = [...contacts].sort((a, b) => Number(b.primary) - Number(a.primary))

  return (
    <Grid columns={{ initial: '1', sm: '2', lg: '3' }} gap="3">
      {sorted.map((contact) => (
        <Card
          key={contact.id}
          size="2"
          variant="surface"
          // 대표 카드만 강조 면 (10 §2.2)
          style={contact.primary ? { background: 'var(--accent-a2)', boxShadow: 'inset 0 0 0 1px var(--accent-a5)' } : undefined}
        >
          <Flex direction="column" gap="3" height="100%">
            <Flex align="center" gap="3">
              <Avatar size="3" radius="full" fallback={contact.name.slice(0, 1)} color={contact.primary ? undefined : 'gray'} variant="soft" />
              <Box minWidth="0">
                <Flex align="center" gap="2" wrap="wrap">
                  <Text size="3" weight="medium">
                    {contact.name}
                  </Text>
                  {contact.primary && (
                    <Badge variant="solid" size="1" radius="full">
                      <StarFilledIcon width="10" height="10" /> 대표
                    </Badge>
                  )}
                </Flex>
                {contact.title && (
                  <Text as="div" size="2" color="gray">
                    {contact.title}
                  </Text>
                )}
              </Box>
            </Flex>

            <Flex direction="column" gap="1">
              <ContactLine icon={<EnvelopeClosedIcon />} href={`mailto:${contact.email}`} text={contact.email} />
              {contact.phone && <ContactLine icon={<MobileIcon />} href={`tel:${contact.phone}`} text={contact.phone} />}
            </Flex>

            <Flex gap="2" mt="auto" pt="1" wrap="wrap">
              {!contact.primary && (
                <Button size="1" variant="soft" onClick={() => onSetPrimary(contact)} loading={settingPrimary}>
                  <StarFilledIcon /> 대표로 지정
                </Button>
              )}
              <Button size="1" variant="soft" color="gray" onClick={() => onEdit(contact)}>
                <Pencil1Icon /> 수정
              </Button>
              <Button size="1" variant="soft" color="red" onClick={() => onDelete(contact)}>
                <TrashIcon /> 삭제
              </Button>
            </Flex>
          </Flex>
        </Card>
      ))}

      {/* 추가 카드 — 0명일 때는 빈 상태 안내를 겸한다 */}
      <Box asChild>
        <button
          type="button"
          onClick={onAdd}
          className="lift"
          style={{
            minHeight: 120,
            border: '1px dashed var(--gray-a6)',
            borderRadius: 'var(--radius-3)',
            background: 'transparent',
            cursor: 'pointer',
            color: 'var(--gray-11)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 6,
            padding: 12,
          }}
        >
          <PlusIcon width="18" height="18" />
          <Text size="2" weight="medium">
            담당자 추가
          </Text>
          {contacts.length === 0 && (
            <Text size="1" color="gray" align="center">
              견적을 보내려면 담당자 한 명이 필요합니다
            </Text>
          )}
        </button>
      </Box>
    </Grid>
  )
}

function ContactLine({ icon, href, text }: { icon: React.ReactNode; href: string; text: string }) {
  return (
    <Flex align="center" gap="1" minWidth="0">
      <Text color="gray" style={{ display: 'inline-flex', flexShrink: 0 }}>
        {icon}
      </Text>
      <Text asChild size="2" color="gray" truncate>
        <a href={href} style={{ color: 'inherit', textDecoration: 'none' }}>
          {text}
        </a>
      </Text>
    </Flex>
  )
}

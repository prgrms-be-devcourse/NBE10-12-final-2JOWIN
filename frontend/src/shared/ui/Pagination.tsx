import { Flex, IconButton, Text } from '@radix-ui/themes'
import { ChevronLeftIcon, ChevronRightIcon } from '@radix-ui/react-icons'
import type { PageResponse } from '../api/types'

interface Props {
  data: Pick<PageResponse<unknown>, 'page' | 'size' | 'totalElements' | 'totalPages'>
  /** 단위 — "곳"·"건" */
  unit?: string
  onPageChange: (page: number) => void
}

/** 목록 하단 페이지 이동 — 공통 PageResponse(Q-39)를 그대로 받는다 */
export function Pagination({ data, unit = '건', onPageChange }: Props) {
  const { page, size, totalElements, totalPages } = data
  const from = totalElements === 0 ? 0 : page * size + 1
  const to = Math.min((page + 1) * size, totalElements)
  return (
    <Flex align="center" justify="between" px="3" pt="3" mt="2" style={{ borderTop: '1px solid var(--gray-a4)' }}>
      <Text size="1" color="gray">
        전체 {totalElements}
        {unit} 중 {from}–{to}
      </Text>
      {totalPages > 1 && (
        <Flex align="center" gap="2">
          <IconButton variant="soft" color="gray" size="1" aria-label="이전 페이지" disabled={page === 0} onClick={() => onPageChange(page - 1)}>
            <ChevronLeftIcon />
          </IconButton>
          <Text size="1" color="gray">
            {page + 1} / {totalPages}
          </Text>
          <IconButton variant="soft" color="gray" size="1" aria-label="다음 페이지" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
            <ChevronRightIcon />
          </IconButton>
        </Flex>
      )}
    </Flex>
  )
}

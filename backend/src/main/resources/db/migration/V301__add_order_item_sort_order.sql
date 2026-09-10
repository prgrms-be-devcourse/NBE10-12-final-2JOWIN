-- order_item.sort_order — 주문 항목 순서 (QT-07의 순서를 OD-04 값 복사로 물려받는다)
--
-- 왜 필요한가 (PR #197 리뷰):
--   quote_item에는 sort_order가 있는데 order_item에는 없었다. 그래서 전환 직후 201 응답은
--   삽입 순서라 맞지만, 이후 GET /orders/{id}는 DB가 돌려주는 순서라 같은 주문인데
--   조회 시점에 따라 항목 순서가 달라질 수 있었다.
--
-- 왜 3단계인가:
--   NOT NULL DEFAULT 0으로 한 번에 넣으면 기존 행이 전부 0이 되어 정렬이 다시 무의미해진다.
--   백필을 거쳐야 주문 안에서 값이 갈린다.

ALTER TABLE order_item ADD COLUMN sort_order INT;

-- 백필 — created_at 1순위, id 2순위.
--   · 앱이 만든 항목은 @CreatedDate가 항목마다 Instant.now()를 불러 삽입 순서대로 갈린다
--   · 시드처럼 한 문장으로 들어가 created_at이 같은 행은 id로 떨어지는데, 시드는 id가
--     파일 순서와 같아(81…0101 → 0102 → 0103) 그 경우에도 원래 순서가 나온다
--   · 둘 다 어긋나면 임의 순서가 되지만, 목적인 "조회할 때마다 달라지지 않게 고정"은 달성된다
--     — 원래 순서를 복원할 컬럼이 애초에 없었기 때문이다
UPDATE order_item oi
SET sort_order = numbered.rn
FROM (SELECT id,
             row_number() OVER (PARTITION BY order_id ORDER BY created_at, id) - 1 AS rn
      FROM order_item) AS numbered
WHERE oi.id = numbered.id;

ALTER TABLE order_item ALTER COLUMN sort_order SET NOT NULL;

-- quote_item의 sort_order와 같은 제약 (V1__baseline.sql:256)
ALTER TABLE order_item ADD CONSTRAINT ck_order_item_sort_order CHECK (sort_order >= 0);

# db/migration-phase2 — 배치 완주 후에 적용하는 마이그레이션

**이 폴더는 부팅 시 자동으로 적용되지 않는다.** `application.yaml`의
`spring.flyway.locations`가 `classpath:db/migration` 하나뿐이라, 여기 있는 파일은
운영자가 명시적으로 적용해야 한다.

## 왜 나눴나

SPEC-0019의 참조 축 이전은 **컬럼 추가 → 애플리케이션 배치 → 제약 조이기** 순서를 요구한다
(설계 문서 §4.1, V19의 선례). 그런데 Flyway는 부팅 때 대기 중인 마이그레이션을 **한 번에
연속 적용한다.** 조이는 단계를 `db/migration`에 두면 이런 일이 벌어진다:

1. V52가 `spot.catalog_id`를 추가한다 — 전부 null이다. 배정 배치는 아직 돌지 않았다.
2. V53이 `spot`을 경유해 `spot_reaction.catalog_id`를 백필한다 — **전부 null이 들어간다.**
3. 조이기가 같은 부팅에서 돌면 `SET NOT NULL`이 실패하고 **앱이 기동하지 못한다.**
4. 그 시점에 V52·V53은 이미 적용됨으로 기록돼 있어, 되돌리려면
   `flyway_schema_history`를 손대야 한다.

적용된 마이그레이션은 수정할 수 없으므로(체크섬), 사후에 V53을 고치는 선택지도 없다.
그래서 **조이는 단계만 이 폴더로 분리했다.**

## 실행 순서

```
1. 배포              → db/migration의 V52~V55가 부팅 시 적용된다
                       (컬럼 추가 + 백필까지. 전부 nullable이라 기존 동작에 영향 없음)

2. 카탈로그 배정 배치 → spotSyncJob
                       spot.catalog_id가 채워진다. **결과를 눈으로 확인한다** —
                       실측 기준 56,250 카탈로그, 미해결 509건(0.81%)에서 크게 벗어나면
                       규칙이나 상류 데이터가 바뀐 것이다.

3. 좋아요 백필 재실행 → V53의 UPDATE를 손으로 한 번 더 돌린다.
                       (1단계에서는 spot.catalog_id가 null이라 아무것도 채우지 못했다)

                       UPDATE spot_reaction r SET catalog_id = s.catalog_id
                       FROM spot s WHERE s.id = r.spot_id AND r.catalog_id IS NULL;

                       UPDATE community_post_spot ps SET catalog_id = s.catalog_id
                       FROM spot s WHERE s.id = ps.spot_id AND ps.catalog_id IS NULL;

4. dedupe 배치        → reactionDedupeJob
                       (member_id, catalog_id) 중복을 없앤다. **건너뛰면 5단계가 실패한다.**

5. 이 폴더 적용       → flyway -locations=classpath:db/migration-phase2 migrate
                       또는 애플리케이션을 phase2 로케이션으로 한 번 기동한다.
```

**3번을 빼먹으면 5번이 `NOT NULL` 위반으로 실패한다.** 실패하는 편이 옳다 — 조용히
넘어가면 어느 좋아요가 유실되는지 아무도 모른 채 배포된다.

## 되돌리기

1~4단계는 전부 되돌릴 수 있다(추가된 컬럼은 nullable이고 기존 컬럼은 그대로 남아 있다).
`spot_reaction.spot_id`와 `community_post_spot.spot_id`를 남겨 둔 이유가 이것이다 —
좋아요는 사용자가 만든 데이터라 복구할 원본이 없다.

5단계 이후 되돌리려면 제약을 다시 떼어내야 한다. 그 시점 전에 배정 결과를 확인하는 것이
2단계가 "눈으로 확인한다"고 적힌 이유다.

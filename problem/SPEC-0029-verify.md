# SPEC-0029 배치 검증 — 방법과 쿼리

> 2026-08-21 초기화·재적재·매칭 배치 완료 후 실측. 재검증용으로 쿼리를 그대로 남긴다.

**검증의 요점은 건수가 아니라 "의도한 규칙이 실제로 작동했는가"다.** 카운트는 배치가 돌았다는 것만 말해 주지, 매칭이 옳은지·필터가 걸렸는지는 말해 주지 않는다. 아래는 각 요구사항을 **반증 가능한 형태**로 물어보는 쿼리다 — 대부분 "위반 건수"를 세고 0이어야 통과다.

접속: `kubectl exec -it -n ieuri-db $(kubectl get pod -n ieuri-db -l cnpg.io/instanceRole=primary -o name | head -1) -c postgres -- psql -U postgres -d ieuri`

---

## 실측 결과 요약 (2026-08-21)

| 항목 | 값 |
|---|---|
| spot 원본 | KO 41,597 / JA 13,301 / EN 13,253 / ZH 12,793 |
| 카탈로그 | 42,738 |
| 4개 언어 보유 카탈로그 | 11,699 (27.4%) |
| 미배정 spot | 81 |
| 언어별 노출 | KO 96.7% / JA 30.6% / EN 30.5% / ZH 29.4% |

**검색 노드**: 카탈로그 42,738 · translation 79,987 — 메인과 일치.

---

## 1. 적재 규모와 언어 분포

```sql
SELECT 'spot 언어별' AS 구분, language AS 키, count(*)::text AS 값 FROM tour.spot GROUP BY language
UNION ALL SELECT '카탈로그 총계', '-', count(*)::text FROM tour.spot_catalog
UNION ALL SELECT 'translation 언어별', language, count(*)::text FROM tour.spot_catalog_translation GROUP BY language
UNION ALL SELECT '미배정 spot', '-', count(*)::text FROM tour.spot WHERE catalog_id IS NULL
ORDER BY 구분, 키;
```

**읽는 법**: `spot`(원본)보다 `translation`이 조금 적은 것이 정상이다 — 배정되지 못한 행(제목이 한국어뿐인 외국어 행 등)이 빠진다. 차이가 크면 `SpotTitleNormalizer.displayTitle`이 과하게 버리고 있다는 신호다.

실측 차이: KO 282 / EN 227 / JA 222 / ZH 226 — 언어당 0.5~1.7%로 정상 범위.

## 2. 매칭 성과 — 카탈로그가 몇 개 언어를 갖는가 (R-7)

```sql
SELECT lang_count AS 보유언어수, count(*) AS 카탈로그수,
       round(100.0*count(*)/sum(count(*)) OVER (), 1) AS 비율
FROM (SELECT catalog_id, count(DISTINCT language) AS lang_count
      FROM tour.spot_catalog_translation GROUP BY catalog_id) t
GROUP BY lang_count ORDER BY lang_count;
```

**읽는 법**: 4언어 카탈로그가 11,699개(27.4%)면 매칭이 동작한다는 뜻이다. 이 값이 0에 가까우면 매칭이 전량 실패한 것이고, 그때는 §3의 표본 조회로 제목 정규화를 의심한다.

1언어가 69.2%인 것은 정상이다 — KO에만 있는 국내용 spot이 대다수다.

## 3. 매칭 **정확성** — 이게 핵심이다

건수가 많아도 엉뚱하게 묶였으면 소용없다. 두 가지로 반증한다.

```sql
-- 같은 카탈로그인데 언어별 좌표/카테고리가 어긋나면 오매칭 의심
SELECT '좌표 불일치' AS 검사, count(*)::text AS 건수 FROM (
  SELECT catalog_id FROM tour.spot_catalog_translation
  GROUP BY catalog_id
  HAVING count(DISTINCT language) > 1 AND max(map_x) - min(map_x) > 0.001
) x
UNION ALL
SELECT '카테고리 불일치', count(*)::text FROM (
  SELECT catalog_id FROM tour.spot_catalog_translation
  GROUP BY catalog_id HAVING count(DISTINCT category) > 1
) y;
```

**둘 다 0이어야 한다.** 실측 0/0.

그리고 **눈으로 확인하는 것이 중요하다** — 위 쿼리는 좌표·카테고리가 같으면서 다른 장소인 경우를 못 잡는다.

```sql
SELECT c.id,
       max(t.title) FILTER (WHERE t.language='KO') AS ko,
       max(t.title) FILTER (WHERE t.language='EN') AS en,
       max(t.title) FILTER (WHERE t.language='JA') AS ja,
       max(t.title) FILTER (WHERE t.language='ZH') AS zh,
       c.category
FROM tour.spot_catalog c JOIN tour.spot_catalog_translation t ON t.catalog_id=c.id
GROUP BY c.id, c.category HAVING count(DISTINCT t.language)=4
ORDER BY random() LIMIT 20;
```

`ORDER BY random()`으로 여러 번 돌려 보는 것을 권한다. 확인할 것 둘:

1. **네 제목이 같은 장소인가** — 실측 표본(`목포 스카이워크`/`Mokpo Skywalk`/`木浦スカイウォーク`/`木浦天空步道`)은 전부 일치했다.
2. **ZH가 번체인가** — `蘇萊浦口節`·`麗水`·`獨立紀念館`처럼 나와야 한다. 간체(`苏莱`·`丽水`·`独立纪念馆`)가 섞이면 CLAUDE.md 위반이다.

## 4. R-1 의료 시설 제외

```sql
SELECT count(*) AS 잔존, string_agg(DISTINCT title, ' | ' ORDER BY title) AS 표본
FROM tour.spot
WHERE language='KO' AND (title LIKE '%병원%' OR title LIKE '%의원%' OR title LIKE '%약국%'
   OR title LIKE '%외과%' OR title LIKE '%내과%' OR title LIKE '%피부과%' OR title LIKE '%치과%'
   OR title LIKE '%보건소%' OR title LIKE '%요양원%' OR title LIKE '%동물병원%');
```

**0이 아니어도 된다.** 남은 것이 **보호 규칙에 걸린 것인지** 확인하는 게 핵심이다.

실측 6건 — 전부 의도된 잔존:

| 제목 | 왜 남았나 |
|---|---|
| 서울대학교병원 의학박물관 | 카테고리 보호(CULTURAL_FACILITY) — AC-1.3 |
| 부산 구 백제병원 | 카테고리 보호 — 근대건축물 관광지 |
| 올리브영 인천길병원점 / 제주대학병원점 / 제주한라병원점 | 지점명 보호 — AC-1.4 |
| 탠저비 한라병원점 | 지점명 보호 |

**진짜 의료기관이 남아 있으면 실패다.** `○○정형외과의원` 같은 것이 나오면 키워드가 안 걸린 것이다.

반대로 **오탐 감시**도 필요하다(스펙 §2.1이 사전 인정한 위험):

```sql
SELECT title FROM tour.spot
WHERE language='KO' AND (title LIKE '%안과%' OR title LIKE '%내과%')
  AND catalog_id IS NOT NULL;
```

`안과학회관`·`내과대학` 류가 **적재돼 있으면** 오탐이 아니라 카테고리 보호가 살린 것이고, **없으면** 걸러진 것이다. 후자가 많으면 해당 키워드를 좁혀야 한다.

## 5. `visible` 정합성 (R-8, §8.1 컬럼 통합)

`visible`은 이제 **"그 언어에 원본이 있다"** 는 뜻이다. 정의와 실제가 어긋나는지 양방향으로 본다.

```sql
SELECT 'visible=true인데 원본 없음' AS 검사, count(*)::text AS 건수
FROM tour.spot_catalog_translation t
WHERE t.visible AND NOT EXISTS (
  SELECT 1 FROM tour.spot s WHERE s.catalog_id=t.catalog_id AND s.language=t.language)
UNION ALL
SELECT 'visible=false인데 원본 있음', count(*)::text
FROM tour.spot_catalog_translation t
WHERE NOT t.visible AND EXISTS (
  SELECT 1 FROM tour.spot s WHERE s.catalog_id=t.catalog_id AND s.language=t.language);
```

**둘 다 0이어야 한다.** 실측 0/0.

## 6. 커뮤니티 언어 사본 동기화 (R-15/R-23)

이번 스펙에서 **가장 조용히 틀릴 수 있는 곳**이다. 사본이 안 채워지면 글 목록이 오류 없이 비어 버린다.

```sql
SELECT '원본에 있는데 사본에 없음' AS 검사, count(*)::text AS 건수 FROM (
  SELECT catalog_id, language FROM tour.spot_catalog_translation WHERE visible
  EXCEPT SELECT catalog_id, language::text FROM community.community_spot_catalog_language
) d
UNION ALL
SELECT '사본에 있는데 원본에 없음', count(*)::text FROM (
  SELECT catalog_id, language::text FROM community.community_spot_catalog_language
  EXCEPT SELECT catalog_id, language FROM tour.spot_catalog_translation WHERE visible
) e;
```

**둘 다 0이어야 한다.** 실측 0/0 — 도메인 이벤트 기반 동기화가 실제로 동작했다는 증거다.

0이 아니면 `SpotCatalogLanguagesChanged` 리스너가 실패했거나(로그 확인), 배정 배치가 아직 안 돈 것이다.

## 7. 언어별 노출 모수 (R-9)

```sql
SELECT lang AS 활성언어, count(*) AS 노출카탈로그,
       round(100.0*count(*)/(SELECT count(*) FROM tour.spot_catalog), 1) AS "전체대비%"
FROM (VALUES ('KO'),('EN'),('JA'),('ZH')) v(lang)
JOIN tour.spot_catalog_translation t ON t.language=v.lang AND t.visible
GROUP BY lang ORDER BY count(*) DESC;
```

실측: KO 96.7% / JA 30.6% / EN 30.5% / ZH 29.4%.

**외국어가 30% 안팎인 것이 이 스펙의 결과다.** 기계번역을 걷어냈으니 원문이 있는 것만 남는다. 이 수치가 예전(100%)과 다른 것이 정상이며, 클라이언트에 이 사실을 알렸다.

## 8. 검색 노드 복제

```sql
-- 검색 노드(5433)에서 실행
SELECT count(*) AS 카탈로그 FROM tour.spot_catalog;
SELECT count(*) AS translation, count(*) FILTER (WHERE visible) AS visible만
FROM tour.spot_catalog_translation;
```

메인과 같은 수여야 한다. 실측 42,738 / 79,987 / 79,987 — 일치.

`has_origin` 컬럼이 양쪽 다 없는지도 확인한다(V71·검색노드 V10):

```sql
SELECT count(*) FROM information_schema.columns
WHERE table_name='spot_catalog_translation' AND column_name='has_origin';  -- 0
```

## 9. 아직 검증할 수 없는 것

- **showflag 필터** — 응답에서 걸러진 항목은 DB에 흔적이 없다. 배치 로그의 `dropped` 카운트로만 관측 가능하다.
- **좋아요 언어 축** — 사용자 행위가 없어 `spot_reaction`이 0건이다. 앱에서 언어를 바꿔가며 눌러 봐야 확인된다.
- **글 목록 언어 필터** — 글이 1건(spot 미첨부)뿐이라 필터가 발동할 대상이 없다.

세 번째는 앱에서 직접 확인이 필요하다: 서로 다른 언어로 spot을 첨부해 글을 쓴 뒤, 언어를 바꿔 목록에서 빠지는지 본다.

---

## 10. 발견된 결함 — 같은 건물 다른 매장의 오매칭 (2026-08-21)

첫 검증에서 **카탈로그 하나에 spot 40건**이 묶인 것을 발견했다. 검증이 카운트에 그쳤다면 못 봤을 것이고, `HAVING count(*) > 4`로 물어봤기에 드러났다.

### 무엇이었나

`현대아울렛 동대문점` 안의 K2·MLB·NBA·JDX·SJSJ 등 **열 개 매장이 한 장소**가 돼 있었다. 각각 다른 `content_id`와 썸네일을 가진 별개 spot이고, 4개 언어씩 있어 40건이었다.

원인은 좌표가 아니라 **제목 정규화**다. `matchKey`가 한글만 남긴다:

```
"K2 현대아울렛 동대문점"  → "현대아울렛동대문점"
"NBA 현대아울렛 동대문점" → "현대아울렛동대문점"   ← 같은 키
```

브랜드명이 영문이라 통째로 지워진다. 좌표 타이브레이크는 **같은 건물이라 좌표가 실제로 같아서** 무력했다.

### 찾는 쿼리

```sql
-- 한 카탈로그에 spot이 5건 이상 = 오매칭 (언어가 4개뿐이므로)
SELECT catalog_id, count(*) AS spot수,
       string_agg(DISTINCT title, ' / ' ORDER BY title) FILTER (WHERE language='KO') AS KO제목들
FROM tour.spot WHERE catalog_id IS NOT NULL
GROUP BY catalog_id HAVING count(*) > 4
ORDER BY count(*) DESC LIMIT 20;

-- 같은 언어가 한 카탈로그에 2건 이상 = 확정 오매칭
SELECT catalog_id, language, count(*) FROM tour.spot
WHERE catalog_id IS NOT NULL
GROUP BY catalog_id, language HAVING count(*) > 1
ORDER BY count(*) DESC;
```

**둘 다 0이어야 정상이다.** 언어가 4개뿐이므로 한 카탈로그의 spot은 최대 4건이다.

### 고친 방법

`pickFor`의 KO 판정에 **썸네일 비교를 추가**했다. 매장마다 이미지가 다르므로 유일한 구분자다.

안전성을 실측으로 확인한 쿼리 — 이 변경이 정상 매칭을 깨뜨리는지 본다:

```sql
SELECT
  CASE WHEN spot_cnt > 4 THEN '오매칭(5건+)' ELSE '정상(4건 이하)' END AS 구분,
  count(*) FILTER (WHERE ko_thumbs > 1) AS "KO썸네일_2종이상",
  count(*) AS 카탈로그수
FROM (
  SELECT catalog_id, count(*) AS spot_cnt,
         count(DISTINCT thumbnail_image_url) FILTER (WHERE language='KO') AS ko_thumbs
  FROM tour.spot WHERE catalog_id IS NOT NULL GROUP BY catalog_id
) t GROUP BY 1;
```

결과: 오매칭 103개 중 **91개(88%)가 갈라지고**, 정상 42,635개 중 영향받는 것은 **4개**뿐이었다. 그 4개도 대부분 원래 갈라져야 할 것들이었다(`[제주올레 15코스] A/B`, `[하영올레] 1코스/2코스`).

### 기존 데이터 정정 — 배치 재실행만으로는 안 된다

`findUnassigned`가 `catalog_id IS NULL`인 행만 보므로(증분 처리), **이미 배정된 80,863건은 다시 판정되지 않는다.** 코드를 고쳐도 기존 오매칭은 남는다.

정정하려면 배정을 되돌려야 한다. 좋아요·글 연결이 아직 없는 지금이 적기다:

```sql
-- 1) 오매칭 카탈로그에 붙은 spot의 배정만 푼다
UPDATE tour.spot SET catalog_id = NULL
WHERE catalog_id IN (
  SELECT catalog_id FROM tour.spot WHERE catalog_id IS NOT NULL
  GROUP BY catalog_id HAVING count(*) FILTER (WHERE language='KO') > 1
);

-- 2) 빈 카탈로그를 지운다 (translation·festival은 CASCADE)
DELETE FROM tour.spot_catalog c
WHERE NOT EXISTS (SELECT 1 FROM tour.spot s WHERE s.catalog_id = c.id);

-- 3) 배정 배치 재실행 → 고친 규칙으로 다시 묶인다
```

**주의**: 2번이 카탈로그를 지우므로 그 id에 달린 좋아요가 CASCADE로 사라진다. 운영 데이터가 생긴 뒤에는 이 방법을 쓸 수 없다 — 그때는 카탈로그를 살리고 spot만 옮기는 별도 절차가 필요하다.

전량 초기화(V73 재실행)로 다시 쌓는 것이 가장 확실하지만, 재적재에 시간이 오래 걸린다.

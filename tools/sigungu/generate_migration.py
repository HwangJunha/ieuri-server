import json, sys
sys.path.insert(0,'.')
import names

geo = json.load(open('final.json'))
rows = []
for line in open('kto_sigungu_codes.tsv'):
    p = line.rstrip('\n').split('\t')
    if len(p) < 4: continue
    rows.append(tuple(p))

def q(s):
    return "'" + s.replace("'", "''") + "'"

out = []
for area_cd, area_nm, code, sgg_nm in rows:
    g = geo[code]
    en, ja, zh = names.SIGUNGU[sgg_nm]
    pen, pja, pzh = names.SIDO[area_nm]
    out.append(
        "(" + ", ".join([
            q(code), q(area_cd),
            q(sgg_nm), q(en), q(ja), q(zh),
            q(area_nm), q(pen), q(pja), q(pzh),
            f"ST_GeogFromText('SRID=4326;{g['wkt']}')",
            f"ST_GeogFromText('SRID=4326;POINT({g['lng']:.7f} {g['lat']:.7f})')",
        ]) + ")"
    )

sql = """-- 시군구 252건 적재(SPEC-0015 R1 · R2 · R3).
--
-- **경계 출처**: vuski/admdongkor `ver20240401` 행정동 경계(공공누리, 통계청 원자료 가공).
-- 행정동 3,553건을 시군구 코드(`sgg`)로 합쳐(dissolve) 252건을 만들었다. 이 버전을 고른
-- 이유는 **KTO 코드 파일 252건과 코드 집합이 정확히 일치**하기 때문이다 — 최신본
-- (ver20260701)은 전남·광주 통합, 인천 제물포/영종/서해/검단구, 화성시 일반구 신설이
-- 반영되어 KTO가 아직 쓰지 않는 코드가 섞인다.
--
-- **좌표는 (경도 위도) 순서다.** WKT · POINT 모두 그렇다.
--
-- **경계는 0.0001도(약 11m) 단순화했다.** 원본 11MB가 4.9MB가 된다. 시군구 규모의
-- 지도 표시·공간 조인에는 영향이 없는 수준이며, 단순화 후에도 모든 대표점이 도형
-- 내부임을 검증했다.
--
-- **대표 좌표는 ST_PointOnSurface가 아니라 적재 시점에 계산해 넣는다**(설계 §1.2).
-- 산출 규칙: MultiPolygon 조각 중 **면적이 가장 큰 것을 고른 뒤** 그 위에서 대표점을
-- 구한다. 전체 MultiPolygon에 그냥 대표점을 구하면 섬이 많은 지역에서 본토가 아닌
-- 작은 섬에 찍힐 수 있다(신안군 191조각 · 옹진군 77조각 · 완도군 103조각).
--   주의: 옹진군의 대표점은 백령도에 찍힌다 — 실제로 옹진군에서 면적이 가장 큰 섬이
--   백령도이기 때문이며 오류가 아니다.
--
-- ja·zh 명칭은 한자 독음 기반으로 생성했고 **검수받지 않았다**. 폴백(활성 언어 → EN → KO)이
-- 있으므로 틀린 표기가 있으면 UPDATE로 고치면 된다.

INSERT INTO sigungu (code, province_code,
                     name_ko, name_en, name_ja, name_zh,
                     province_name_ko, province_name_en, province_name_ja, province_name_zh,
                     boundary, center)
VALUES
""" + ",\n".join(out) + """
ON CONFLICT (code) DO UPDATE SET
    province_code    = EXCLUDED.province_code,
    name_ko          = EXCLUDED.name_ko,
    name_en          = EXCLUDED.name_en,
    name_ja          = EXCLUDED.name_ja,
    name_zh          = EXCLUDED.name_zh,
    province_name_ko = EXCLUDED.province_name_ko,
    province_name_en = EXCLUDED.province_name_en,
    province_name_ja = EXCLUDED.province_name_ja,
    province_name_zh = EXCLUDED.province_name_zh,
    boundary         = EXCLUDED.boundary,
    center           = EXCLUDED.center;
"""
open('V45__load_sigungu.sql','w').write(sql)
print('rows:', len(out))
print('size:', len(sql)/1024/1024, 'MB')

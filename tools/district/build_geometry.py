import json
from collections import defaultdict
from shapely.geometry import shape, MultiPolygon, Polygon
from shapely.ops import unary_union

TOL = 0.0001  # 약 11m

d = json.load(open('HangJeongDong_ver20240401.geojson'))
kto = {}; order = []
for line in open('kto_district_codes.tsv'):
    a, an, s, sn = line.rstrip('\n').split('\t')
    kto[s] = (a, an, sn); order.append(s)

g = defaultdict(list)
for f in d['features']: g[f['properties']['sgg']].append(shape(f['geometry']))

out = {}
for code in order:
    m = unary_union(g[code])
    if isinstance(m, Polygon): m = MultiPolygon([m])
    s = m.simplify(TOL, preserve_topology=True)
    if isinstance(s, Polygon): s = MultiPolygon([s])
    if not s.is_valid:
        s = s.buffer(0)
        if isinstance(s, Polygon): s = MultiPolygon([s])
    # 대표점은 **단순화된** 도형에서 뽑는다 — 저장된 boundary와 center가 반드시 일치해야 한다.
    largest = max(s.geoms, key=lambda p: p.area)
    pt = largest.representative_point()
    assert s.is_valid and s.geom_type == 'MultiPolygon'
    assert largest.contains(pt) and s.contains(pt), f'{code} 대표점 이탈'
    assert -90 <= pt.y <= 90 and -180 <= pt.x <= 180
    out[code] = {'wkt': s.wkt, 'lat': pt.y, 'lng': pt.x, 'parts': len(s.geoms)}

json.dump(out, open('final.json','w'))
print('시군구:', len(out))
print('총 WKT:', sum(len(v['wkt']) for v in out.values())/1024/1024, 'MB')
print('검증 통과 — 전부 valid MultiPolygon, 대표점 내부 보장')

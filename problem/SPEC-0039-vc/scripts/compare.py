"""Call every Query root on the local app and api-dev with identical requests and diff the responses."""
import json
import sys

from client import DEV, LOCAL, call

MAX_DEPTH = 4
SEOUL = {'latitude': 37.5665, 'longitude': 126.9780}
BBOX = {'minLat': 37.55, 'minLng': 126.96, 'maxLat': 37.58, 'maxLng': 127.00}

INTROSPECTION = """
{ __schema { queryType { name } types { name kind
  fields { name args { name defaultValue type { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name } } } } } } } } }
           type { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name } } } } } } } } }
  possibleTypes { name } } } }
"""

LOGIN = """mutation($e: String!, $p: String!) { loginEmail(input: { email: $e, password: $p }) {
  success accessToken userErrors { code message field } } }"""


def named(t):
    while t.get('ofType'):
        t = t['ofType']
    return t['name']


def required(arg):
    return arg['type']['kind'] == 'NON_NULL' and arg['defaultValue'] is None


class Schema:
    def __init__(self, data):
        self.types = {t['name']: t for t in data['__schema']['types']}
        self.query = self.types[data['__schema']['queryType']['name']]

    def selection(self, type_name, depth):
        t = self.types[type_name]
        if t['kind'] in ('SCALAR', 'ENUM'):
            return ''
        if depth >= MAX_DEPTH:
            return None
        if t['kind'] in ('INTERFACE', 'UNION'):
            parts = ['__typename']
            for p in t['possibleTypes'] or []:
                inner = self.selection(p['name'], depth + 1)
                if inner:
                    parts.append(f'... on {p["name"]} {inner}')
            return '{ ' + ' '.join(parts) + ' }'
        parts = []
        for f in t['fields'] or []:
            if any(required(a) for a in f['args']):
                continue
            sub = self.selection(named(f['type']), depth + 1)
            if sub is None:
                continue
            parts.append(f['name'] + (' ' + sub if sub else ''))
        return '{ ' + ' '.join(parts) + ' }' if parts else None

    def root_query(self, field_name, args):
        f = next(x for x in self.query['fields'] if x['name'] == field_name)
        sel = self.selection(named(f['type']), 1) or ''
        arg_text = ', '.join(f'{k}: {literal(v)}' for k, v in args.items())
        return f'{{ {field_name}{"(" + arg_text + ")" if arg_text else ""} {sel} }}'


def literal(v):
    if isinstance(v, bool):
        return 'true' if v else 'false'
    if isinstance(v, (int, float)):
        return json.dumps(v)
    if isinstance(v, str):
        return json.dumps(v, ensure_ascii=False)
    if isinstance(v, list):
        return '[' + ', '.join(literal(x) for x in v) + ']'
    if isinstance(v, dict):
        return '{' + ', '.join(f'{k}: {literal(x)}' for k, x in v.items()) + '}'
    if v is None:
        return 'null'
    raise TypeError(v)


def diff(a, b, path='$'):
    if type(a) is not type(b):
        return [f'{path}: {short(a)} != {short(b)}']
    if isinstance(a, dict):
        out = []
        for k in sorted(set(a) | set(b)):
            if k not in a or k not in b:
                out.append(f'{path}.{k}: only in {"dev" if k in b else "local"}')
            else:
                out += diff(a[k], b[k], f'{path}.{k}')
        return out
    if isinstance(a, list):
        if len(a) != len(b):
            return [f'{path}: length {len(a)} != {len(b)}']
        out = []
        for i, (x, y) in enumerate(zip(a, b)):
            out += diff(x, y, f'{path}[{i}]')
        return out
    return [] if a == b else [f'{path}: {short(a)} != {short(b)}']


def short(v):
    s = json.dumps(v, ensure_ascii=False)
    return s if len(s) < 120 else s[:117] + '...'


def dig(obj, *keys):
    for k in keys:
        if obj is None:
            return None
        if isinstance(k, int):
            obj = obj[k] if isinstance(obj, list) and len(obj) > k else None
        else:
            obj = obj.get(k) if isinstance(obj, dict) else None
    return obj


def main():
    email, password = sys.argv[1], sys.argv[2]
    schema = Schema(call(LOCAL, INTROSPECTION)[1]['data'])
    tokens = {}
    for name, url in (('local', LOCAL), ('dev', DEV)):
        status, body = call(url, LOGIN, {'e': email, 'p': password})
        tokens[url] = dig(body, 'data', 'loginEmail', 'accessToken')
        print(f'login {name}: status={status} success={dig(body, "data", "loginEmail", "success")}')

    def both(label, query, variables=None, auth=True):
        res = {}
        for url in (LOCAL, DEV):
            res[url] = call(url, query, variables, tokens[url] if auth else None)
        (ls, lb), (ds, db) = res[LOCAL], res[DEV]
        diffs = ([] if ls == ds else [f'HTTP {ls} != {ds}']) + diff(lb, db)
        results.append((label, diffs, db))
        return db

    results = []

    def q(label, field, args=None, auth=True):
        return both(label, schema.root_query(field, args or {}), auth=auth)

    # 식별자 없이 부를 수 있는 것
    q('authHealth', 'authHealth', auth=False)
    me = q('me', 'me')
    q('assignableRoles', 'assignableRoles')
    q('agreements', 'agreements')
    q('members', 'members', {'first': 5})
    q('communityChips', 'communityChips')
    q('blockedUsers', 'blockedUsers', {'first': 5})
    q('myPosts', 'myPosts', {'first': 5})
    posts = q('posts(좌표)', 'posts', {**SEOUL, 'first': 5})
    conversations = q('conversations', 'conversations', {'first': 5})
    q('mapViewport', 'mapViewport', {'bbox': BBOX, 'zoom': 14})
    q('mapRecommendedSpots', 'mapRecommendedSpots', {'bbox': BBOX, 'first': 5})
    q('seoulCongestion', 'seoulCongestion', {'bbox': BBOX})
    q('seoulCongestionAreas', 'seoulCongestionAreas')
    cells = q('mapCells', 'mapCells')
    q('mapRelations', 'mapRelations', {'first': 5})
    q('recommendedRegions', 'recommendedRegions', {'origin': {'lat': 37.5665, 'lng': 126.9780}})
    questions = q('spotQuestions', 'spotQuestions', {'first': 5})
    q('spotQuestions(mine)', 'spotQuestions', {'mine': True, 'first': 5})
    q('recommendedSpots', 'recommendedSpots', {**SEOUL, 'first': 10})
    spots = q('spots', 'spots', {**SEOUL, 'size': 10})
    q('availableSpotCategories', 'availableSpotCategories')
    q('likedSpots', 'likedSpots', {'first': 5})
    q('searchLikedSpots', 'searchLikedSpots', {'query': '서울', 'first': 5})
    q('searchSpots', 'searchSpots', {'query': '궁', **SEOUL, 'size': 5})
    q('spotSearchSuggestions', 'spotSearchSuggestions', {'query': '경복', **SEOUL, 'limit': 5})
    q('ongoingFestivals', 'ongoingFestivals', {**SEOUL, 'first': 5})
    q('popularSpots', 'popularSpots', {**SEOUL, 'first': 5})

    # dev 응답에서 얻은 id로 같은 요청
    member_id = dig(me, 'data', 'me', 'id')
    if member_id is not None:
        q('member(me.id)', 'member', {'id': member_id})
    post_id = dig(posts, 'data', 'posts', 'edges', 0, 'node', 'id')
    if post_id is not None:
        q('post', 'post', {'id': post_id})
        q('comments', 'comments', {'postId': post_id, 'first': 5})
    conv_id = dig(conversations, 'data', 'conversations', 'edges', 0, 'node', 'id')
    if conv_id is not None:
        q('conversationMessages', 'conversationMessages', {'conversationId': conv_id, 'first': 5})
    cell_id = dig(cells, 'data', 'mapCells', 0, 'id')
    if cell_id is not None:
        q('mapCell', 'mapCell', {'id': cell_id})
    spot_id = dig(spots, 'data', 'spots', 'items', 0, 'id') or dig(spots, 'data', 'spots', 'spots', 0, 'id')
    if spot_id is not None:
        q('spotDetail', 'spotDetail', {'id': spot_id})
        q('spotQuestions(catalogId)', 'spotQuestions', {'catalogId': spot_id, 'first': 5})
    question_id = dig(questions, 'data', 'spotQuestions', 'edges', 0, 'node', 'id')
    if question_id is not None:
        q('spotAnswers', 'spotAnswers', {'questionId': question_id, 'first': 5})

    # 오류 경로(상태를 바꾸지 않는 것만)
    q('ERR spotDetail(없는 id)', 'spotDetail', {'id': 999999999})
    q('ERR post(없는 id)', 'post', {'id': 999999999})
    q('ERR member(없는 id)', 'member', {'id': 999999999})
    q('ERR mapCell(없는 id)', 'mapCell', {'id': 999999999})
    q('ERR sharedMap(잘못된 토큰)', 'sharedMap', {'token': 'invalid-token'})
    q('ERR comments(없는 글)', 'comments', {'postId': 999999999})
    q('ERR conversationMessages(없는 대화)', 'conversationMessages', {'conversationId': 999999999})
    q('ERR spotAnswers(없는 질문)', 'spotAnswers', {'questionId': 999999999})
    q('ERR searchSpots(빈 검색어)', 'searchSpots', {'query': '', **SEOUL})
    q('ERR searchSpots(101자)', 'searchSpots', {'query': 'a' * 101, **SEOUL})
    q('ERR spots(반경 초과)', 'spots', {**SEOUL, 'radiusMeters': 10_000_000})
    q('ERR recommendedSpots(first 초과)', 'recommendedSpots', {**SEOUL, 'first': 1000})
    q('ERR mapViewport(줌 범위 밖)', 'mapViewport', {'bbox': BBOX, 'zoom': 99})
    q('ERR mapViewport(bbox 과대)', 'mapViewport', {'bbox': {'minLat': 33, 'minLng': 124, 'maxLat': 39, 'maxLng': 132}, 'zoom': 10})
    q('ERR posts(first 초과)', 'posts', {**SEOUL, 'first': 1000})
    q('ERR members(비로그인)', 'members', {'first': 5}, auth=False)
    q('ERR me(비로그인)', 'me', auth=False)
    q('ERR likedSpots(비로그인)', 'likedSpots', {'first': 5}, auth=False)

    ok = 0
    for label, diffs, dev_body in results:
        err = ''
        if isinstance(dev_body, dict) and dev_body.get('errors'):
            e = dev_body['errors'][0]
            code = (e.get('extensions') or {}).get('code') or (e.get('extensions') or {}).get('errorType')
            err = f' [errors x{len(dev_body["errors"])}: {code} {e.get("path")} {e.get("message", "")[:90]}]'
        if diffs:
            print(f'DIFF {label}{err}')
            for d in diffs[:8]:
                print('     ' + d)
            if len(diffs) > 8:
                print(f'     … {len(diffs) - 8} more')
        else:
            ok += 1
            print(f'SAME {label}{err}')
    print(f'\n{ok}/{len(results)} identical')
    json.dump([{'label': l, 'diffs': d} for l, d, _ in results], open('result.json', 'w'), ensure_ascii=False, indent=1)


main()

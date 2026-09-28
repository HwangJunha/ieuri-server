"""Compare local vs dev results for one phase, normalizing values that differ per call."""
import json
import sys

PHASE = sys.argv[1]
VOLATILE = {'id', 'postId', 'commentId', 'questionId', 'answerId', 'replyId', 'conversationId', 'messageId',
            'createdAt', 'updatedAt', 'lastMessageAt', 'accessToken', 'refreshToken', 'uploadUrl', 'objectKey',
            'profileImageKey', 'profileImageUrl', 'shareUrl', 'expiresAt', 'counterpartMemberId', 'confirmed'}


def norm(v, key=None):
    if key in VOLATILE and v is not None:
        return f'<{key}>'
    if isinstance(v, dict):
        return {k: norm(x, k) for k, x in v.items()}
    if isinstance(v, list):
        return [norm(x, key) for x in v]
    return v


def diff(a, b, path='$'):
    if type(a) is not type(b):
        return [f'{path}: {json.dumps(a, ensure_ascii=False)[:150]} != {json.dumps(b, ensure_ascii=False)[:150]}']
    if isinstance(a, dict):
        out = []
        for k in sorted(set(a) | set(b)):
            out += diff(a.get(k), b.get(k), f'{path}.{k}') if k in a and k in b else [f'{path}.{k}: only in one side']
        return out
    if isinstance(a, list):
        if len(a) != len(b):
            return [f'{path}: length {len(a)} != {len(b)}']
        return [d for i, (x, y) in enumerate(zip(a, b)) for d in diff(x, y, f'{path}[{i}]')]
    return [] if a == b else [f'{path}: {json.dumps(a, ensure_ascii=False)[:150]} != {json.dumps(b, ensure_ascii=False)[:150]}']


local = {s['label']: s for s in json.load(open(f'results/local-{PHASE}.json'))}
dev = {s['label']: s for s in json.load(open(f'results/dev-{PHASE}.json'))}
same = 0
for label in local:
    a, b = local[label], dev.get(label)
    if b is None:
        print(f'MISSING on dev: {label}')
        continue
    d = ([] if a['status'] == b['status'] else [f'HTTP {a["status"]} != {b["status"]}']) + diff(norm(a['response']), norm(b['response']))
    checks_a = [(c['name'], c['ok']) for c in a['checks']]
    checks_b = [(c['name'], c['ok']) for c in b['checks']]
    if checks_a != checks_b:
        d.append(f'checks {checks_a} != {checks_b}')
    if d:
        print(f'DIFF {label}')
        for x in d[:6]:
            print('     ' + x)
    else:
        same += 1
print(f'{PHASE}: {same}/{len(local)} same')

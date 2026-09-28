"""GraphQL client for the local app and api-dev. Secrets come from the environment (direnv)."""
import json
import os
import urllib.request

LOCAL = 'http://localhost:18888/graphql'
DEV = 'https://api-dev.ieuri.co.kr/graphql'


def call(url, query, variables=None, token=None, headers=None):
    body = json.dumps({'query': query, 'variables': variables or {}}).encode()
    h = {'Content-Type': 'application/json', 'Accept-Language': 'ko-KR', 'User-Agent': 'curl/8.7.1'}
    if url == DEV:
        h['CF-Access-Client-Id'] = os.environ['CF_ACCESS_CLIENT_ID']
        h['CF-Access-Client-Secret'] = os.environ['CF_ACCESS_CLIENT_SECRET']
    if token:
        h['Authorization'] = f'Bearer {token}'
    h.update(headers or {})
    req = urllib.request.Request(url, data=body, headers=h, method='POST')
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {'raw': raw[:300].decode(errors='replace')}


if __name__ == '__main__':
    for url in (LOCAL, DEV):
        print(url, call(url, '{ authHealth }'))

"""Run the SPEC-0039 mutation verification plan against one server.

usage: mutation_run.py <local|dev> <errors|main-a|main-b|dup>
Writes results/<side>-<phase>.json. Secrets come from the environment; nothing secret is written.
"""
import json
import os
import sys
import time
import urllib.request

from client import DEV, LOCAL, call

SIDE, PHASE = sys.argv[1], sys.argv[2]
URL = LOCAL if SIDE == 'local' else DEV
EMAIL, PASSWORD = os.environ['VC_EMAIL'], os.environ['VC_PASSWORD']
STATE_FILE = f'results/{SIDE}-state.json'
SEOUL = {'latitude': 37.5665, 'longitude': 126.9780}
PNG = bytes.fromhex(
    '89504e470d0a1a0a0000000d4948445200000001000000010806000000'
    '1f15c4890000000d49444154789c6360000002000154a24f5d0000000049454e44ae426082')
UE = 'userErrors { code message field }'
# 교차 대상: 신고 A=댓글 129(로컬 먼저), B=댓글 130(dev 먼저). 대화·지도 관계 상대: 로컬 101, dev 146.
COUNTERPART = {'local': 101, 'dev': 146}
OTHER_COUNTERPART = {'local': 146, 'dev': 101}
REPORT_FIRST = {'local': 129, 'dev': 130}
REPORT_SECOND = {'local': 130, 'dev': 129}
# 상대 계정의 유효한 공유 링크 토큰. 예: VC_SHARE_TOKENS='{"101": "...", "146": "..."}' (tour.map_share_link)
SHARE_TOKEN = {int(k): v for k, v in json.loads(os.environ['VC_SHARE_TOKENS']).items()}

steps = []
state = json.load(open(STATE_FILE)) if os.path.exists(STATE_FILE) else {}


def login():
    q = f'mutation($i: LoginEmailInput!) {{ loginEmail(input: $i) {{ success accessToken refreshToken tokenType accessTokenExpiresIn refreshTokenExpiresIn {UE} }} }}'
    status, body = call(URL, q, {'i': {'email': EMAIL, 'password': PASSWORD}})
    return body['data']['loginEmail']


TOKEN = None


def m(label, query, variables=None, auth=True):
    status, body = call(URL, query, variables, TOKEN if auth else None)
    step = {'label': label, 'query': query, 'variables': variables, 'status': status, 'response': body, 'checks': []}
    steps.append(step)
    return body


def data(body, *keys):
    cur = body
    for k in keys:
        if cur is None:
            return None
        if isinstance(k, int):
            cur = cur[k] if isinstance(cur, list) and len(cur) > k else None
        else:
            cur = cur.get(k) if isinstance(cur, dict) else None
    return cur


def check(name, ok, detail=''):
    steps[-1]['checks'].append({'name': name, 'ok': bool(ok), 'detail': detail})


def put(url, content_type):
    req = urllib.request.Request(url, data=PNG, method='PUT', headers={'Content-Type': content_type, 'User-Agent': 'curl/8.7.1'})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.status


def q(query, variables=None):
    return call(URL, query, variables, TOKEN)[1]


def run_errors():
    E = lambda label, mutation, inp, sel='', auth=True: m(
        label, f'mutation($i: {mutation[1]}) {{ {mutation[0]}(input: $i) {{ success {UE} {sel} }} }}', {'i': inp}, auth)
    E('verifyEmail 없는 id', ('verifyEmail', 'VerifyEmailInput!'), {'verificationId': 'nope', 'code': '000000'}, 'verified failureReason')
    E('resendVerification 없는 id', ('resendVerification', 'ResendVerificationInput!'), {'verificationId': 'nope'})
    E('loginSns 잘못된 토큰', ('loginSns', 'LoginSnsInput!'), {'provider': 'GOOGLE', 'idToken': 'not-a-jwt'})
    E('loginEmail 없는 이메일', ('loginEmail', 'LoginEmailInput!'), {'email': 'vc-nobody@example.com', 'password': 'whatever123!'})
    E('requestPasswordReset 없는 이메일', ('requestPasswordReset', 'RequestPasswordResetInput!'), {'email': 'vc-nobody@example.com'})
    E('resetPassword 없는 이메일', ('resetPassword', 'ResetPasswordInput!'), {'email': 'vc-nobody@example.com', 'code': '000000', 'newPassword': 'Newpass123!'}, 'failureReason')
    E('refreshToken 잘못된 토큰', ('refreshToken', 'RefreshTokenInput!'), {'refreshToken': 'not-a-token'})
    E('logout 잘못된 토큰', ('logout', 'LogoutInput!'), {'refreshToken': 'not-a-token'})
    E('suspendMember 권한 없음', ('suspendMember', 'SuspendMemberInput!'), {'memberId': 52, 'reason': 'vc'})
    E('reactivateMember 권한 없음', ('reactivateMember', 'ReactivateMemberInput!'), {'memberId': 52})
    E('assignRole 권한 없음', ('assignRole', 'AssignRoleInput!'), {'memberId': 52, 'roleCode': 'ADMIN'}, 'changed')
    E('revokeRole 권한 없음', ('revokeRole', 'RevokeRoleInput!'), {'memberId': 52, 'roleCode': 'USER'}, 'changed')
    E('changeEmail 형식 오류', ('changeEmail', 'ChangeEmailInput!'), {'newEmail': 'not-an-email'}, 'verificationId')
    E('changeEmail 같은 주소', ('changeEmail', 'ChangeEmailInput!'), {'newEmail': EMAIL}, 'verificationId')
    E('confirmEmailChange 없는 id', ('confirmEmailChange', 'ConfirmEmailChangeInput!'), {'verificationId': 'nope', 'code': '000000'}, 'email failureReason')
    E('resendEmailChange 없는 id', ('resendEmailChange', 'ResendEmailChangeInput!'), {'verificationId': 'nope'})
    E('retargetEmailVerification 인증된 회원', ('retargetEmailVerification', 'RetargetEmailVerificationInput!'), {'newEmail': 'vc-retarget@example.com'})
    E('reportMessages 없는 메시지', ('reportMessages', 'ReportMessagesInput!'), {'messageIds': [999999999], 'reason': 'SPAM'})
    E('createMapRelation 잘못된 URL', ('createMapRelation', 'CreateMapRelationInput!'), {'shareUrl': 'https://example.com/nope'}, 'counterpartMemberId')
    E('createPost 빈 제목', ('createPost', 'CreatePostInput!'), {'category': 'TOURIST_SPOT', 'tags': [], 'title': '', 'body': '본문', 'photoKeys': []}, 'postId')
    E('createPost 51자 제목', ('createPost', 'CreatePostInput!'), {'category': 'TOURIST_SPOT', 'tags': [], 'title': '가' * 51, 'body': '본문', 'photoKeys': []}, 'postId')
    E('createPost 태그 6개', ('createPost', 'CreatePostInput!'), {'category': 'TOURIST_SPOT', 'tags': [f't{i}' for i in range(6)], 'title': '제목', 'body': '본문', 'photoKeys': []}, 'postId')
    E('editPost 남의 글', ('editPost', 'EditPostInput!'), {'postId': 163, 'title': '제목', 'body': '본문'})
    E('editPost 태그 6개', ('editPost', 'EditPostInput!'), {'postId': 164, 'title': '제목', 'body': '본문', 'tags': [f't{i}' for i in range(6)]})
    E('deletePost 없는 글', ('deletePost', 'DeletePostInput!'), {'postId': 999999999})
    E('likePost 없는 글', ('likePost', 'LikePostInput!'), {'postId': 999999999}, 'likeCount')
    E('createComment 없는 글', ('createComment', 'CreateCommentInput!'), {'postId': 999999999, 'body': '댓글'}, 'commentId')
    E('blockUser 자기 자신', ('blockUser', 'BlockUserInput!'), {'targetMemberId': 55})
    E('startConversation 자기 자신', ('startConversation', 'StartConversationInput!'), {'counterpartMemberId': 55}, 'conversationId')
    E('sendMessage 없는 대화', ('sendMessage', 'SendMessageInput!'), {'conversationId': 999999999, 'body': '안녕'}, 'messageId')
    E('reactToSpot 없는 장소', ('reactToSpot', 'ReactToSpotInput!'), {'catalogId': 999999999, 'reaction': 'LIKE'})
    E('recordVisits 없는 코드', ('recordVisits', 'RecordVisitsInput!'), {'districtCodes': ['99999']}, 'updatedCells { id }')
    E('createSpotQuestion 빈 본문', ('createSpotQuestion', 'CreateSpotQuestionInput!'), {'catalogId': 343259, 'body': '', 'photoKeys': []}, 'questionId')
    E('createSpotAnswer 없는 질문', ('createSpotAnswer', 'CreateSpotAnswerInput!'), {'questionId': 999999999, 'body': '답변', 'photoKeys': []}, 'answerId')
    E('reportContent 자기 댓글', ('reportContent', 'ReportContentInput!'), {'targetType': 'COMMENT', 'targetId': 131, 'reason': 'SPAM'})
    E('reportContent 없는 대상', ('reportContent', 'ReportContentInput!'), {'targetType': 'POST', 'targetId': 999999999, 'reason': 'SPAM'})
    E('createProfileImageUploadUrl gif', ('createProfileImageUploadUrl', 'CreateProfileImageUploadUrlInput!'), {'contentType': 'image/gif', 'contentLength': 67}, 'objectKey')
    E('changeNickname 빈 값', ('changeNickname', 'ChangeNicknameInput!'), {'nickname': ''}, 'nickname')
    E('createPost 비로그인', ('createPost', 'CreatePostInput!'), {'category': 'TOURIST_SPOT', 'tags': [], 'title': '제목', 'body': '본문', 'photoKeys': []}, 'postId', auth=False)


def run_main_a():
    me = q('{ me { id nickname profileImageUrl } }')['data']['me']
    state['nickname'] = me['nickname']
    spots = q('query($la: Float!, $lo: Float!) { spots(latitude: $la, longitude: $lo, size: 5) { items { id } } }',
              {'la': SEOUL['latitude'], 'lo': SEOUL['longitude']})
    spot_ids = [s['id'] for s in data(spots, 'data', 'spots', 'items') or []]
    catalog_x = spot_ids[0]
    reaction_spot = spot_ids[0] if SIDE == 'local' else spot_ids[1]

    # 1·2·3 토큰
    body = m('1 loginEmail', 'mutation { loginEmail(input: {email: "%s", password: "%s"}) { success tokenType accessTokenExpiresIn refreshTokenExpiresIn accessToken refreshToken %s } }' % (EMAIL, PASSWORD, UE), auth=False)
    first = data(body, 'data', 'loginEmail')
    ok = data(call(URL, '{ me { id } }', None, first['accessToken'])[1], 'data', 'me', 'id') == '55'
    check('Q: 받은 토큰으로 me.id=55', ok)
    body = m('2 refreshToken', f'mutation($i: RefreshTokenInput!) {{ refreshToken(input: $i) {{ success tokenType accessToken refreshToken {UE} }} }}', {'i': {'refreshToken': first['refreshToken']}}, auth=False)
    second = data(body, 'data', 'refreshToken')
    ok = second and data(call(URL, '{ me { id } }', None, second['accessToken'])[1], 'data', 'me', 'id') == '55'
    check('Q: 새 accessToken으로 me.id=55', ok)
    m('3 logout', f'mutation($i: LogoutInput!) {{ logout(input: $i) {{ success {UE} }} }}', {'i': {'refreshToken': second['refreshToken']}}, auth=False)

    # 4 닉네임
    temp = 'VC검증닉네임'
    m('4a changeNickname', f'mutation($i: ChangeNicknameInput!) {{ changeNickname(input: $i) {{ success nickname {UE} }} }}', {'i': {'nickname': temp}})
    check('Q: me.nickname=임시값', q('{ me { nickname } }')['data']['me']['nickname'] == temp)
    m('4b changeNickname 복구', f'mutation($i: ChangeNicknameInput!) {{ changeNickname(input: $i) {{ success nickname {UE} }} }}', {'i': {'nickname': state['nickname']}})
    check('Q: me.nickname=원래값', q('{ me { nickname } }')['data']['me']['nickname'] == state['nickname'])

    # 5·6 약관
    agr = lambda: {a['termsCode']: a['agreed'] for a in q('{ agreements { termsCode agreed } }')['data']['agreements']}
    m('5a updateAgreement MARKETING true', f'mutation($i: UpdateAgreementInput!) {{ updateAgreement(input: $i) {{ success agreement {{ termsCode agreed required }} {UE} }} }}', {'i': {'termsCode': 'MARKETING', 'agreed': True}})
    check('Q: MARKETING=true', agr().get('MARKETING') is True)
    m('5b updateAgreement MARKETING false 복구', f'mutation($i: UpdateAgreementInput!) {{ updateAgreement(input: $i) {{ success agreement {{ termsCode agreed required }} {UE} }} }}', {'i': {'termsCode': 'MARKETING', 'agreed': False}})
    check('Q: MARKETING=false', agr().get('MARKETING') is False)
    m('6 agreeToRequiredTerms', f'mutation($i: AgreeToRequiredTermsInput!) {{ agreeToRequiredTerms(input: $i) {{ success agreements {{ termsCode agreed required }} {UE} }} }}', {'i': {'termsCodes': ['SERVICE', 'PRIVACY', 'LOCATION']}})
    a = agr()
    check('Q: 필수 3개 true', all(a.get(c) for c in ('SERVICE', 'PRIVACY', 'LOCATION')))

    # 7·8·9 프로필 이미지
    body = m('7 createProfileImageUploadUrl', f'mutation($i: CreateProfileImageUploadUrlInput!) {{ createProfileImageUploadUrl(input: $i) {{ success uploadUrl objectKey expiresAt {UE} }} }}', {'i': {'contentType': 'image/png', 'contentLength': len(PNG)}})
    up = data(body, 'data', 'createProfileImageUploadUrl')
    check('업로드 PUT 200', up and up['uploadUrl'] and put(up['uploadUrl'], 'image/png') == 200)
    m('8 confirmProfileImageUpload', f'mutation($i: ConfirmProfileImageUploadInput!) {{ confirmProfileImageUpload(input: $i) {{ success profileImageKey {UE} }} }}', {'i': {'objectKey': up['objectKey']}})
    check('Q: me.profileImageUrl 있음', q('{ me { profileImageUrl } }')['data']['me']['profileImageUrl'] is not None)
    m('9 removeProfileImage', f'mutation {{ removeProfileImage {{ success {UE} }} }}')
    check('Q: me.profileImageUrl=null', q('{ me { profileImageUrl } }')['data']['me']['profileImageUrl'] is None)

    # 10 기기 토큰 (삭제는 main-b), 12 알림 설정 (복구는 main-b)
    state['deviceId'] = f'vc-{SIDE}-{int(time.time())}'
    m('10 registerDeviceToken', f'mutation($i: RegisterDeviceTokenInput!) {{ registerDeviceToken(input: $i) {{ success {UE} }} }}', {'i': {'deviceId': state['deviceId'], 'fcmToken': 'vc-fcm-token', 'language': 'KO', 'platform': 'IOS'}})
    m('12a updateNotificationPreference MESSAGE false', f'mutation($i: UpdateNotificationPreferenceInput!) {{ updateNotificationPreference(input: $i) {{ success notificationPreferences {{ category enabled }} {UE} }} }}', {'i': {'category': 'MESSAGE', 'enabled': False}})

    # 13~21 글
    post_q = 'query($id: ID!) { post(id: $id) { id title body tags category likeCount likedByViewer commentCount hidden edited ownedByViewer } }'
    body = m('13 createPost', f'mutation($i: CreatePostInput!) {{ createPost(input: $i) {{ success postId {UE} }} }}', {'i': {'category': 'TOURIST_SPOT', 'tags': ['검증', 'vc'], 'title': 'VC 제목', 'body': 'VC 본문', 'photoKeys': []}})
    pid = data(body, 'data', 'createPost', 'postId')
    p = data(q(post_q, {'id': pid}), 'data', 'post')
    check('Q: post(id) 제목·태그', p and p['title'] == 'VC 제목' and p['tags'] == ['검증', 'vc'] and p['ownedByViewer'])
    check('Q: myPosts 첫 항목', data(q('{ myPosts(first: 1) { edges { node { id } } } }'), 'data', 'myPosts', 'edges', 0, 'node', 'id') == pid)
    m('14 editPost', f'mutation($i: EditPostInput!) {{ editPost(input: $i) {{ success {UE} }} }}', {'i': {'postId': pid, 'title': 'VC 수정', 'body': 'VC 수정 본문'}})
    p = data(q(post_q, {'id': pid}), 'data', 'post')
    check('Q: 제목·본문 변경, edited', p and p['title'] == 'VC 수정' and p['body'] == 'VC 수정 본문' and p['edited'])
    m('15 likePost', f'mutation($i: LikePostInput!) {{ likePost(input: $i) {{ success likeCount {UE} }} }}', {'i': {'postId': pid}})
    p = data(q(post_q, {'id': pid}), 'data', 'post')
    check('Q: likeCount=1, likedByViewer', p and p['likeCount'] == 1 and p['likedByViewer'])
    m('16 unlikePost', f'mutation($i: UnlikePostInput!) {{ unlikePost(input: $i) {{ success likeCount {UE} }} }}', {'i': {'postId': pid}})
    p = data(q(post_q, {'id': pid}), 'data', 'post')
    check('Q: likeCount=0', p and p['likeCount'] == 0 and not p['likedByViewer'])
    body = m('17 createComment', f'mutation($i: CreateCommentInput!) {{ createComment(input: $i) {{ success commentId {UE} }} }}', {'i': {'postId': pid, 'body': 'VC 댓글'}})
    cid = data(body, 'data', 'createComment', 'commentId')
    comments = lambda: data(q('query($p: ID!) { comments(postId: $p, first: 5) { edges { node { id body } } } }', {'p': pid}), 'data', 'comments', 'edges') or []
    check('Q: comments 1건', [e['node']['id'] for e in comments()] == [cid])
    m('18 deleteComment', f'mutation($i: DeleteCommentInput!) {{ deleteComment(input: $i) {{ success {UE} }} }}', {'i': {'commentId': cid}})
    check('Q: comments 0건', comments() == [])
    hidden = lambda: [e['node']['id'] for e in data(q('{ posts(hiddenByViewer: true, first: 20) { edges { node { id } } } }'), 'data', 'posts', 'edges') or []]
    m('19 hidePost 163', f'mutation($i: HidePostInput!) {{ hidePost(input: $i) {{ success {UE} }} }}', {'i': {'postId': 163}})
    check('Q: 숨긴 글 목록에 163', '163' in hidden())
    m('20 unhidePost 163', f'mutation($i: HidePostInput!) {{ unhidePost(input: $i) {{ success {UE} }} }}', {'i': {'postId': 163}})
    check('Q: 숨긴 글 목록에서 빠짐', '163' not in hidden())
    m('21 deletePost', f'mutation($i: DeletePostInput!) {{ deletePost(input: $i) {{ success {UE} }} }}', {'i': {'postId': pid}})
    check('Q: post(id)=null', data(q(post_q, {'id': pid}), 'data', 'post') is None)

    # 22·23·24 글 사진
    body = m('22 createPostImageUploadUrls', f'mutation($i: CreatePostImageUploadUrlsInput!) {{ createPostImageUploadUrls(input: $i) {{ success uploadUrls {{ uploadUrl objectKey expiresAt }} {UE} }} }}', {'i': {'images': [{'contentType': 'image/png', 'contentLength': len(PNG)}]}})
    u = data(body, 'data', 'createPostImageUploadUrls', 'uploadUrls', 0)
    check('업로드 PUT 200', u and put(u['uploadUrl'], 'image/png') == 200)
    state['postImageKey'] = u and u['objectKey']
    body = m('23 confirmPostImageUploads', f'mutation($i: ConfirmPostImageUploadsInput!) {{ confirmPostImageUploads(input: $i) {{ success confirmed failed {{ objectKey code }} {UE} }} }}', {'i': {'objectKeys': [u['objectKey']]}})
    confirmed = data(body, 'data', 'confirmPostImageUploads', 'confirmed') or []
    uuid = u['objectKey'].rsplit('/', 1)[1]
    check('응답: confirmed에 같은 이미지의 확정 키(images/…)', len(confirmed) == 1 and confirmed[0].startswith('images/') and confirmed[0].endswith(uuid))
    state['postImageKey'] = confirmed[0] if confirmed else None
    m('24 deletePostImage', f'mutation($i: DeletePostImageInput!) {{ deletePostImage(input: $i) {{ success {UE} }} }}', {'i': {'objectKey': state['postImageKey']}})

    # 25·26 차단
    blocked = lambda: data(q('{ blockedUsers(first: 5) { edges { node { id } } } }'), 'data', 'blockedUsers', 'edges') or []
    m('25 blockUser', f'mutation($i: BlockUserInput!) {{ blockUser(input: $i) {{ success {UE} }} }}', {'i': {'targetMemberId': COUNTERPART[SIDE]}})
    check('Q: blockedUsers 1건', len(blocked()) == 1)
    m('26 unblockUser', f'mutation($i: UnblockUserInput!) {{ unblockUser(input: $i) {{ success {UE} }} }}', {'i': {'targetMemberId': COUNTERPART[SIDE]}})
    check('Q: blockedUsers 0건', blocked() == [])

    # 27 반응
    state['reactionSpot'] = reaction_spot
    m('27 reactToSpot LIKE', f'mutation($i: ReactToSpotInput!) {{ reactToSpot(input: $i) {{ success {UE} }} }}', {'i': {'catalogId': reaction_spot, 'reaction': 'LIKE'}})
    liked = [e['node']['id'] for e in data(q('{ likedSpots(first: 50) { edges { node { id } } } }'), 'data', 'likedSpots', 'edges') or []]
    check('Q: likedSpots에 포함', str(reaction_spot) in liked)

    # 28·29 방문
    visited = lambda: data(q('query($id: ID!) { mapCell(id: $id) { viewerVisited } }', {'id': 2}), 'data', 'mapCell', 'viewerVisited')
    m('28 recordVisits 11110', f'mutation($i: RecordVisitsInput!) {{ recordVisits(input: $i) {{ success updatedCells {{ id viewerVisited }} {UE} }} }}', {'i': {'districtCodes': ['11110']}})
    check('Q: 칸 2 viewerVisited=true', visited() is True)
    m('29 removeVisit 칸 2', f'mutation($i: RemoveVisitInput!) {{ removeVisit(input: $i) {{ success updatedCell {{ id viewerVisited }} {UE} }} }}', {'i': {'cellId': 2}})
    check('Q: 칸 2 viewerVisited=false', visited() is False)

    # 30·31 공유 링크
    body = m('30 createMapShareLink', f'mutation {{ createMapShareLink {{ success shareLink {{ shareUrl createdAt }} {UE} }} }}')
    url = data(body, 'data', 'createMapShareLink', 'shareLink', 'shareUrl')
    token = url.rsplit('/', 1)[1] if url else None
    shared = lambda t: q('query($t: String!) { sharedMap(token: $t) { ownerNickname visitedCount } }', {'t': t})
    check('Q: sharedMap(token) 조회', data(shared(token), 'data', 'sharedMap', 'ownerNickname') == state['nickname'])
    m('31 revokeMapShareLink', f'mutation($i: RevokeMapShareLinkInput!) {{ revokeMapShareLink(input: $i) {{ success {UE} }} }}', {'i': {'shareUrl': url}})
    check('Q: sharedMap 실패', bool(shared(token).get('errors')))
    base = url.rsplit('/', 1)[0]

    # 32·33 지도 관계
    other = COUNTERPART[SIDE]
    relations = lambda: data(q('{ mapRelations(first: 5) { edges { node { memberId } } } }'), 'data', 'mapRelations', 'edges') or []
    m('32 createMapRelation', f'mutation($i: CreateMapRelationInput!) {{ createMapRelation(input: $i) {{ success counterpartMemberId {UE} }} }}', {'i': {'shareUrl': f'{base}/{SHARE_TOKEN[other]}'}})
    check('Q: mapRelations 1건', [e['node']['memberId'] for e in relations()] == [str(other)])
    m('33 removeMapRelation', f'mutation($i: RemoveMapRelationInput!) {{ removeMapRelation(input: $i) {{ success {UE} }} }}', {'i': {'memberId': other}})
    check('Q: mapRelations 0건', relations() == [])

    # 34~38 장소 Q&A
    mine = lambda: [e['node'] for e in data(q('{ spotQuestions(mine: true, first: 20) { edges { node { id resolved } } } }'), 'data', 'spotQuestions', 'edges') or []]
    body = m('34 createSpotQuestion', f'mutation($i: CreateSpotQuestionInput!) {{ createSpotQuestion(input: $i) {{ success questionId {UE} }} }}', {'i': {'catalogId': catalog_x, 'body': 'VC 질문', 'photoKeys': []}})
    qid = data(body, 'data', 'createSpotQuestion', 'questionId')
    check('Q: mine 목록에 질문', any(n['id'] == qid for n in mine()))
    m('35 resolveSpotQuestion', f'mutation($i: ResolveSpotQuestionInput!) {{ resolveSpotQuestion(input: $i) {{ success {UE} }} }}', {'i': {'questionId': qid, 'resolved': True}})
    check('Q: resolved=true', any(n['id'] == qid and n['resolved'] for n in mine()))
    answers = lambda: [e['node'] for e in data(q('{ spotAnswers(questionId: 54, first: 20) { edges { node { id replies { id } } } } }'), 'data', 'spotAnswers', 'edges') or []]
    body = m('36 createSpotAnswer 질문 54', f'mutation($i: CreateSpotAnswerInput!) {{ createSpotAnswer(input: $i) {{ success answerId {UE} }} }}', {'i': {'questionId': 54, 'body': 'VC 답변', 'photoKeys': []}})
    aid = data(body, 'data', 'createSpotAnswer', 'answerId')
    check('Q: spotAnswers(54)에 답변', any(n['id'] == aid for n in answers()))
    body = m('37a createSpotAnswerReply', f'mutation($i: CreateSpotAnswerReplyInput!) {{ createSpotAnswerReply(input: $i) {{ success replyId {UE} }} }}', {'i': {'parentAnswerId': aid, 'body': 'VC 답글', 'photoKeys': []}})
    rid = data(body, 'data', 'createSpotAnswerReply', 'replyId')
    check('Q: 답변에 답글', any(n['id'] == aid and any(r['id'] == rid for r in n['replies']) for n in answers()))
    m('37b deleteSpotAnswerReply', f'mutation($i: DeleteSpotAnswerReplyInput!) {{ deleteSpotAnswerReply(input: $i) {{ success {UE} }} }}', {'i': {'replyId': rid}})
    check('Q: 답글 사라짐', not any(any(r['id'] == rid for r in n['replies']) for n in answers()))
    m('37c deleteSpotAnswer', f'mutation($i: DeleteSpotAnswerInput!) {{ deleteSpotAnswer(input: $i) {{ success {UE} }} }}', {'i': {'answerId': aid}})
    check('Q: 답변 사라짐', not any(n['id'] == aid for n in answers()))
    m('38 deleteSpotQuestion', f'mutation($i: DeleteSpotQuestionInput!) {{ deleteSpotQuestion(input: $i) {{ success {UE} }} }}', {'i': {'questionId': qid}})
    check('Q: mine 목록에서 빠짐', not any(n['id'] == qid for n in mine()))

    # 39 대화
    body = m('39a startConversation', f'mutation($i: StartConversationInput!) {{ startConversation(input: $i) {{ success conversationId {UE} }} }}', {'i': {'counterpartMemberId': COUNTERPART[SIDE]}})
    conv = data(body, 'data', 'startConversation', 'conversationId')
    body = m('39b sendMessage', f'mutation($i: SendMessageInput!) {{ sendMessage(input: $i) {{ success messageId {UE} }} }}', {'i': {'conversationId': conv, 'body': 'VC 메시지(SPEC-0039 검증)'}})
    mid = data(body, 'data', 'sendMessage', 'messageId')
    msgs = data(q('query($c: ID!) { conversationMessages(conversationId: $c, first: 5) { edges { node { id body ownedByViewer } } } }', {'c': conv}), 'data', 'conversationMessages', 'edges') or []
    check('Q: 메시지 1건', [e['node']['id'] for e in msgs] == [mid])
    m('39c notifyTyping', f'mutation($i: NotifyTypingInput!) {{ notifyTyping(input: $i) {{ success {UE} }} }}', {'i': {'conversationId': conv}})
    m('39d markConversationRead', f'mutation($i: MarkConversationReadInput!) {{ markConversationRead(input: $i) {{ success unreadCount {UE} }} }}', {'i': {'conversationId': conv}})
    convs = data(q('{ conversations(first: 5) { edges { node { id unreadCount } } } }'), 'data', 'conversations', 'edges') or []
    check('Q: 대화 목록에 있고 unreadCount=0', any(e['node']['id'] == conv and e['node']['unreadCount'] == 0 for e in convs))
    state['conversationId'] = conv

    # 40 신고(선행)
    m('40 reportContent 선행', f'mutation($i: ReportContentInput!) {{ reportContent(input: $i) {{ success {UE} }} }}', {'i': {'targetType': 'COMMENT', 'targetId': REPORT_FIRST[SIDE], 'reason': 'OTHER', 'detail': 'SPEC-0039 검증'}})


def run_main_b():
    m('11 deleteDeviceToken', f'mutation($i: DeleteDeviceTokenInput!) {{ deleteDeviceToken(input: $i) {{ success {UE} }} }}', {'i': {'deviceId': state['deviceId']}})
    m('12b updateNotificationPreference MESSAGE true 복구', f'mutation($i: UpdateNotificationPreferenceInput!) {{ updateNotificationPreference(input: $i) {{ success notificationPreferences {{ category enabled }} {UE} }} }}', {'i': {'category': 'MESSAGE', 'enabled': True}})


def run_dup():
    m('40d reportContent 후행(중복)', f'mutation($i: ReportContentInput!) {{ reportContent(input: $i) {{ success {UE} }} }}', {'i': {'targetType': 'COMMENT', 'targetId': REPORT_SECOND[SIDE], 'reason': 'OTHER', 'detail': 'SPEC-0039 검증'}})
    m('39e startConversation 기존 상대', f'mutation($i: StartConversationInput!) {{ startConversation(input: $i) {{ success conversationId {UE} }} }}', {'i': {'counterpartMemberId': OTHER_COUNTERPART[SIDE]}})


TOKEN = login()['accessToken']
{'errors': run_errors, 'main-a': run_main_a, 'main-b': run_main_b, 'dup': run_dup}[PHASE]()
os.makedirs('results', exist_ok=True)
json.dump(steps, open(f'results/{SIDE}-{PHASE}.json', 'w'), ensure_ascii=False, indent=1)
json.dump(state, open(STATE_FILE, 'w'), ensure_ascii=False, indent=1)
bad = [(s['label'], c['name']) for s in steps for c in s['checks'] if not c['ok']]
errs = [s['label'] for s in steps if s['response'].get('errors')]
fails = [s['label'] for s in steps if any(v.get('success') is False for v in (s['response'].get('data') or {}).values() if isinstance(v, dict))]
print(f'{SIDE} {PHASE}: steps={len(steps)} checks={sum(len(s["checks"]) for s in steps)} failedChecks={len(bad)} graphqlErrors={len(errs)} success=false={len(fails)}')
for b in bad:
    print('  CHECK FAIL', b)
for e in errs:
    print('  ERRORS', e)
for f in fails:
    print('  success=false', f)

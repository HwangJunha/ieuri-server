-- 알림 문구에 본문을 더한다(SPEC-0028 R13 보정).
--
-- **iOS가 본문 없는 알림을 표시하지 않는다.** APNs는 `alert.body`가 비면 알림을 띄우지
-- 않는다 — FCM은 정상 접수하고(로그상 성공) 기기에서 조용히 버려지므로, 오류 없이
-- iOS에만 알림이 안 오는 형태로 나타난다. Android는 제목만으로도 표시돼 발견이 늦었다.
--
-- **사용자가 쓴 내용을 되살리는 것이 아니다.** 잠금화면에 대화가 읽히지 않아야 한다는
-- 결정은 그대로다(R13). 여기 들어가는 것은 서버가 정한 고정 안내 문구이고, 메시지·답변
-- 본문은 여전히 알림에 실리지 않는다.
--
-- V68을 고치지 않고 새 파일을 만드는 이유: V68은 이미 적용됐다. 편집하면 checksum이 깨져
-- 이 저장소를 쓰는 다른 체크아웃과 환경의 기동을 막는다.

ALTER TABLE alarm.notification_message
    ADD COLUMN body VARCHAR(200);

-- 기존 12행을 채운다. 종류마다 다른 문구를 두는 이유는 제목만으로 행동이 정해지지 않기
-- 때문이다 — DM은 대화로, Q&A는 답변 확인으로 유도한다.
UPDATE alarm.notification_message SET body = '앱에서 메시지를 확인해 보세요' WHERE notification_type = 'DM' AND locale = 'KO';
UPDATE alarm.notification_message SET body = 'Open the app to read it' WHERE notification_type = 'DM' AND locale = 'EN';
UPDATE alarm.notification_message SET body = 'アプリで確認してください' WHERE notification_type = 'DM' AND locale = 'JA';
UPDATE alarm.notification_message SET body = '開啟應用程式查看訊息' WHERE notification_type = 'DM' AND locale = 'ZH';

UPDATE alarm.notification_message SET body = '앱에서 답변을 확인해 보세요' WHERE notification_type = 'SPOT_QA_ANSWER' AND locale = 'KO';
UPDATE alarm.notification_message SET body = 'Open the app to read the answer' WHERE notification_type = 'SPOT_QA_ANSWER' AND locale = 'EN';
UPDATE alarm.notification_message SET body = 'アプリで回答を確認してください' WHERE notification_type = 'SPOT_QA_ANSWER' AND locale = 'JA';
UPDATE alarm.notification_message SET body = '開啟應用程式查看回覆' WHERE notification_type = 'SPOT_QA_ANSWER' AND locale = 'ZH';

UPDATE alarm.notification_message SET body = '앱에서 답글을 확인해 보세요' WHERE notification_type = 'SPOT_QA_REPLY' AND locale = 'KO';
UPDATE alarm.notification_message SET body = 'Open the app to read the reply' WHERE notification_type = 'SPOT_QA_REPLY' AND locale = 'EN';
UPDATE alarm.notification_message SET body = 'アプリで返信を確認してください' WHERE notification_type = 'SPOT_QA_REPLY' AND locale = 'JA';
UPDATE alarm.notification_message SET body = '開啟應用程式查看留言' WHERE notification_type = 'SPOT_QA_REPLY' AND locale = 'ZH';

-- **NOT NULL로 조인다.** 본문이 빈 행이 하나라도 있으면 그 언어의 iOS 알림이 조용히
-- 표시되지 않는다 — 빈 값을 허용하면 이번과 같은 장애가 다시 난다.
ALTER TABLE alarm.notification_message
    ALTER COLUMN body SET NOT NULL;

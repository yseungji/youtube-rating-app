# 팀 공용 Firebase 설정

이 문서는 향후 연결 절차입니다. 저장소 생성만으로 Firebase 프로젝트나 서버가 구성되는 것은 아닙니다.

## 클라이언트 설정

1. 팀 공용 개발용 Firebase 프로젝트를 정합니다.
2. Android 앱의 applicationId와 같은 패키지명으로 앱을 등록합니다.
3. 콘솔에서 `google-services.json`을 다운로드해 `app/google-services.json`에 둡니다.
4. 실제 프로젝트에 맞는 Google services 플러그인과 Firebase SDK 의존성을 추가합니다.
5. Google 로그인에 필요한 서명 인증서 설정도 각 개발 환경에 맞게 등록합니다.

이 저장소는 기본적으로 `google-services.json`을 Git에서 제외합니다. 팀원은 공용 Firebase 프로젝트의 같은 앱 설정을 받아 로컬에 둡니다.

`google-services.json`은 프로젝트·앱 식별 설정이며 서비스 계정의 비밀 개인키와는 다릅니다. 이 파일을 제외하는 것은 팀 설정을 배치하는 정책입니다. 클라이언트 설정을 숨기는 것만으로 Firestore 데이터가 보호되지는 않습니다.

## 서버 자격증명과 규칙

- 서비스 계정의 private key가 들어간 JSON, 서명키와 비밀번호는 저장소나 Android 앱에 넣지 않습니다.
- Firestore·Storage 접근은 Authentication과 Security Rules로 검증합니다.
- Firebase 초기화 후 `firebase.json`, `firestore.rules`, `firestore.indexes.json`, 필요한 `storage.rules`를 팀이 함께 버전 관리합니다.
- 프로젝트 ID를 담은 `.firebaserc`는 개인 비밀키가 아닙니다. 팀 공용 프로젝트를 정한 뒤 공유할 수 있습니다.
- CI를 추가할 때에도 클라이언트 설정 파일과 서버 비밀 자격증명을 구분하고 필요한 값만 전달합니다.

공식 참고:

- https://firebase.google.com/docs/android/setup
- https://firebase.google.com/support/guides/security-checklist

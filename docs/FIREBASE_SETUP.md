# 팀 공용 Firebase 설정

기존에 구축한 Firestore를 Android 앱에서 사용하도록 Gradle 연결 설정과 `CloudFirestoreActivity.addData()` 저장 테스트 코드를 추가했습니다. 아래 절차는 팀원이 같은 환경을 준비하는 방법입니다.

서비스 설계는 [Firestore 데이터 구조](FIRESTORE_SCHEMA.md)를 따릅니다. 데이터 구조 설계와 실제 보안 규칙·집계 구현은 구분합니다.

## 클라이언트 설정

1. 기존에 구축한 팀 공용 개발용 Firebase 프로젝트에 접근합니다.
2. Android 앱의 applicationId와 같은 패키지명으로 앱을 등록합니다.
3. 콘솔에서 `google-services.json`을 다운로드해 `app/google-services.json`에 둡니다.
4. 실제 프로젝트에 맞는 Google services 플러그인과 Firebase SDK 의존성을 추가합니다.
5. Google 로그인에 필요한 서명 인증서 설정도 각 개발 환경에 맞게 등록합니다.

이 저장소는 기본적으로 `google-services.json`을 Git에서 제외합니다. 팀원은 공용 Firebase 프로젝트의 같은 앱 설정을 받아 로컬에 둡니다.

`google-services.json`은 프로젝트·앱 식별 설정이며 서비스 계정의 비밀 개인키와는 다릅니다. 이 파일을 제외하는 것은 팀 설정을 배치하는 정책입니다. 클라이언트 설정을 숨기는 것만으로 Firestore 데이터가 보호되지는 않습니다.

## 현재 설정과 저장 테스트

- Google services 플러그인 4.5.0, Firebase BoM 35.1.0, Authentication·Firestore·Storage·Analytics SDK를 사용합니다.
- Storage는 프로필 사진(`profileImages/{uid}.jpg`)에만 씁니다. 콘솔의 Storage 메뉴에서 버킷이 만들어져 있어야 하고, Storage 보안 규칙에서 본인만 자기 사진을 올리도록 제한합니다. (3.2, 2026-11-12 팀 합의)
- Android application 플러그인은 기존 alias를 사용합니다. 같은 플러그인을 id 방식으로 중복 선언하지 않습니다.
- Firebase는 공식 문서 방식대로 `app/build.gradle.kts`에 BoM과 버전 없는 문자열로 선언합니다. 예: `implementation("com.google.firebase:firebase-auth")`. 버전은 BoM(`firebase-bom:35.1.0`) 한 곳에서만 정하고, 버전 카탈로그에는 Firebase 항목을 두지 않습니다. (2.1에서 카탈로그·문자열로 중복돼 있던 Firestore 선언을 정리, BoM 35.1.0 기준 Firestore 27.1.0)
- `CloudFirestoreActivity` 실행 시 `ratings/testRating`에 예시 데이터를 저장합니다.
- Logcat 필터 `package:mine tag:CJW`로 결과를 확인하고 Firebase 콘솔에서도 해당 문서를 확인합니다.
- 현재 예시는 4.5점이며 재실행하면 같은 문서를 덮어쓰는 연결 테스트입니다. 서비스의 1~5점 정수 평가와 구분합니다.
- `Default FirebaseApp is not initialized` 오류는 설정 파일 위치·패키지명과 Google services 플러그인 적용 여부를 확인합니다.

이번 문서 수정에서는 저장 테스트를 다시 실행하지 않았습니다.

## 서버 자격증명과 규칙

- 서비스 계정의 private key가 들어간 JSON, 서명키와 비밀번호는 저장소나 Android 앱에 넣지 않습니다.
- Firestore·Storage 접근은 Authentication과 Security Rules로 검증합니다.
- Firebase 초기화 후 `firebase.json`, `firestore.rules`, `firestore.indexes.json`, 필요한 `storage.rules`를 팀이 함께 버전 관리합니다.
- 프로젝트 ID를 담은 `.firebaserc`는 개인 비밀키가 아닙니다. 팀 공용 프로젝트를 정한 뒤 공유할 수 있습니다.
- CI를 추가할 때에도 클라이언트 설정 파일과 서버 비밀 자격증명을 구분하고 필요한 값만 전달합니다.

공식 참고:

- https://firebase.google.com/docs/android/setup
- https://firebase.google.com/support/guides/security-checklist

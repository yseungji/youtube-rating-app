# Android 프로젝트 등록 안내

현재 상태와 실제 개발 환경은 [README](../README.md), Firestore 테스트와 서비스 구조의 차이는 [Firestore 데이터 구조](FIRESTORE_SCHEMA.md)를 참고합니다. 아래는 프로젝트 최초 등록 시 사용한 참고 절차입니다.

Android 프로젝트는 등록되어 있습니다. 현재는 Java·XML Views, applicationId `com.example.viewit`, minSdk 24, compileSdk·targetSdk 37을 사용합니다. 아래는 최초 등록 시 사용한 절차입니다.

1. Android Studio에서 `Empty Views Activity`와 Java를 선택합니다.
2. 팀이 정한 앱 이름과 applicationId를 사용합니다. Firebase에 등록할 패키지명도 실제 applicationId와 일치시킵니다.
3. 생성 위치는 복제한 저장소 루트로 맞춥니다. 기존 README와 `.git` 디렉터리를 삭제하지 않습니다.
4. `app/`, `gradle/`, `gradlew`, `gradlew.bat`, 루트와 모듈의 Gradle 설정, `settings.gradle.kts` 등 프로젝트 실행에 필요한 파일을 함께 올립니다.
5. Gradle Sync와 기본 앱 실행을 확인한 뒤 PR을 만듭니다.
6. README에 실제 JDK·Gradle·AGP·SDK 버전과 실행 방법을 적습니다.

다른 폴더에서 먼저 프로젝트를 만들었다면 `.git`, 개인 설정, 빌드 결과를 제외한 프로젝트 파일을 가져오고 PR에서 구조를 확인합니다. 프로젝트 폴더를 여러 번 중첩해 넣지 않습니다.

## 자동 빌드

현재 Gradle Wrapper는 9.5.0, AGP는 9.3.3이며 Java 소스·타깃 호환성은 11입니다. Gradle 실행용 JDK는 이와 별도로 호환 환경을 선택합니다. 팀 실행 환경을 확인한 뒤 GitHub Actions를 추가합니다. 이번 문서 수정에서는 빌드를 다시 실행하지 않았습니다.

## 실행과 연결 테스트

팀 공용 `app/google-services.json`을 준비하고 SDK·Gradle JDK 확인 후 Sync합니다. API 24 이상 기기에서 실행합니다.

```powershell
.\gradlew.bat :app:assembleDebug
```

Firestore 테스트는 실행 Activity를 `CloudFirestoreActivity`로 지정합니다. 화면 생성 시 `addData()`가 `ratings/testRating`을 덮어쓰며, Logcat 필터 `package:mine tag:CJW`로 성공·실패를 확인합니다. 현재 예시는 4.5점으로 서비스 설계의 1~5점 정수와 다릅니다.

현재 Manifest에는 두 Activity 모두 MAIN/LAUNCHER가 등록되어 있습니다. 2.2에서 앱 시작 화면을 하나로 정리합니다.

공식 참고: https://developer.android.com/build

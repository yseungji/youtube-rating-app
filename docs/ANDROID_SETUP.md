# 첫 Android 프로젝트 등록

현재 저장소에는 Android 앱 프로젝트가 없습니다. 첫 프로젝트를 등록할 때 다음을 맞춥니다.

1. Android Studio에서 `Empty Views Activity`와 Java를 선택합니다.
2. 팀이 정한 앱 이름과 applicationId를 사용합니다. Firebase에 등록할 패키지명도 실제 applicationId와 일치시킵니다.
3. 생성 위치는 복제한 저장소 루트로 맞춥니다. 기존 README와 `.git` 디렉터리를 삭제하지 않습니다.
4. `app/`, `gradle/`, `gradlew`, `gradlew.bat`, 루트와 모듈의 Gradle 설정, `settings.gradle.kts` 등 프로젝트 실행에 필요한 파일을 함께 올립니다.
5. Gradle Sync와 기본 앱 실행을 확인한 뒤 PR을 만듭니다.
6. README에 실제 JDK·Gradle·AGP·SDK 버전과 실행 방법을 적습니다.

다른 폴더에서 먼저 프로젝트를 만들었다면 `.git`, 개인 설정, 빌드 결과를 제외한 프로젝트 파일을 가져오고 PR에서 구조를 확인합니다. 프로젝트 폴더를 여러 번 중첩해 넣지 않습니다.

## 자동 빌드

실제 프로젝트의 Gradle Wrapper와 호환 JDK를 확인한 뒤 GitHub Actions를 추가합니다. 첫 단계는 debug 빌드·lint·로컬 단위 테스트 정도로 잡습니다. 아직 실행할 Gradle 프로젝트가 없으므로 현재는 빌드가 성공한 것처럼 표시하는 CI를 넣지 않습니다.

공식 참고: https://developer.android.com/build

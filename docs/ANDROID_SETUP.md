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

앱 시작 화면은 하단 5탭이 있는 `MainActivity` 하나입니다. (2.2에서 정리)

Firebase 테스트 화면(로그인·Firestore 저장)은 `DevMenuActivity` 메뉴에서 엽니다. 디버그 빌드를 설치하면 `src/debug/AndroidManifest.xml`에 의해 **"Viewit 테스트"** 앱 아이콘이 따로 생기며, 출시용(release) 빌드에는 이 아이콘이 없습니다. Android Studio에서 바로 열려면 실행 구성의 Launch를 `Specified Activity` → `DevMenuActivity`로 지정해도 됩니다.

Firestore 테스트는 테스트 메뉴의 `Cloud Firestore` 버튼으로 `CloudFirestoreActivity`를 엽니다. 화면 생성 시 `addData()`가 `ratings/testRating`을 덮어쓰며, Logcat 필터 `package:mine tag:CJW`로 성공·실패를 확인합니다. 현재 예시는 4.5점으로 서비스 설계의 1~5점 정수와 다릅니다.

## 개인 설정 값 (local.properties)

API 키처럼 저장소에 올리면 안 되는 값은 프로젝트 루트의 `local.properties`에 둡니다. 이 파일은 `.gitignore`로 제외되어 있고, Android Studio가 `sdk.dir`을 자동으로 적는 파일입니다. 아래 줄을 각자 추가합니다.

```properties
YOUTUBE_API_KEY=발급받은_키
```

- 빌드 시 `app/build.gradle.kts`가 이 값을 읽어 `BuildConfig.YOUTUBE_API_KEY`로 만듭니다. 코드에서는 이 상수만 사용하고 키를 소스에 직접 적지 않습니다.
- 값이 없어도 빌드는 되며 빈 문자열이 들어갑니다. YouTube API를 호출하는 기능을 실행할 때만 필요합니다.
- 값을 바꾼 뒤에는 Gradle Sync 또는 다시 빌드해야 반영됩니다.
- 팀 공용 키는 메신저 등 저장소 밖에서 전달하고, Google Cloud 콘솔에서 **API 제한**을 YouTube Data API v3 하나로 겁니다. 앱이 YouTube 주소로 직접 요청하는 방식이라 **Android 앱(패키지명·SHA-1) 제한은 걸지 않습니다**(걸면 요청이 거부됨).
- 앱에 들어간 키는 APK에서 꺼낼 수 있으므로 개발·시연용으로만 씁니다. 공개 전에는 Cloud Functions를 거쳐 호출하는 방식(4.1)으로 옮깁니다.

## YouTube 영상 정보 확인 (4.1)

`data/youtube/YouTubeApi`로 YouTube Data API `videos.list`를 호출해 제목·채널·썸네일·재생 시간 등을 `Video`로 받습니다. 요청 1번에 영상 50개까지, 사용량은 1유닛입니다(하루 기본 10,000유닛).

```java
YouTubeApi.getInstance().fetchVideo("jNQXAC9IVRw", new YouTubeApi.Callback<Video>() {
    @Override public void onSuccess(Video video) { /* 화면에 채우기 */ }
    @Override public void onError(YouTubeException e) { /* YouTubeErrorMessage.of(context, e) 안내 */ }
});
```

- 여러 개는 `fetchVideos(List<String>, ...)`. 없는 영상·비공개 영상은 결과에서 빠집니다.
- 결과는 메인 스레드에서 받으므로 바로 화면을 고쳐도 됩니다.
- 실패 이유(`YouTubeException.Reason`): 키 없음, 키 거부(틀린 키·API 꺼짐), 사용량 초과, 영상 없음, 인터넷 연결, 기타
- 디버그 빌드의 `debug/VideoInfoTestActivity`(아이콘 없음)에서 영상 ID를 넣고 바로 확인할 수 있습니다. 실행 구성의 Launch를 `Specified Activity` → `com.example.viewit.debug.VideoInfoTestActivity`로 지정합니다.

### 화면에서는 VideoRepository를 씁니다 (Firestore 저장·30일 갱신)

화면에서 영상 정보가 필요하면 `YouTubeApi`를 직접 부르지 말고 `data/VideoRepository`를 씁니다. Firestore `videos/{영상ID}`에 저장해 둔 정보를 먼저 보고, 없을 때만 YouTube를 부르므로 사용량이 줄어듭니다.

```java
new VideoRepository().getVideo("jNQXAC9IVRw", new VideoRepository.Callback<Video>() {
    @Override public void onSuccess(Video video) { /* 제목·썸네일 + 평균 별점(집계값)까지 들어 있음 */ }
    @Override public void onError(@NonNull Exception e) { /* YouTubeErrorMessage.of(context, e) 안내 */ }
});
```

- 저장본의 수집 시각(`fetchedAt`)이 **30일 안이면 그대로** 쓰고(YouTube 사용량 0), 없거나 30일이 지났으면 YouTube에서 새로 받아 `videos`에 저장합니다. YouTube API 정책상 받아 온 정보는 30일 안에 새로 받아야 하기 때문입니다.
- 저장할 때는 영상 정보 필드(`title`·`channelId`·`channelTitle`·`thumbnailUrl`·`categoryId`·`durationSeconds`·`publishedAt`·`fetchedAt`)만 **merge**로 씁니다. 평가 집계 필드(`publicRatingCount`, `ratingAverage` 등)는 건드리지 않습니다.
- YouTube에서 사라진(삭제·비공개) 영상은 저장해 둔 영상 정보 필드를 지우고 결과에서 뺍니다. 평가 집계값은 남깁니다.
- 30일이 지났는데 새로 받지 못하면(인터넷·사용량 문제) 오래된 정보를 보여 주지 않고 오류로 알립니다.
- 여러 개는 `getVideos(List<String>, ...)`. 결과는 요청한 순서대로이며 찾을 수 없는 영상은 빠집니다.
- 확인 화면(`VideoInfoTestActivity`)의 **Firestore 거쳐 불러오기** 버튼으로 "저장본 n개, YouTube에서 새로 m개"를 볼 수 있고, **저장본 수집 시각을 31일 전으로** 버튼으로 30일 규칙을 확인할 수 있습니다.
- Security Rules(6.4)에서는 로그인한 사용자가 영상 정보 필드만 쓸 수 있고 집계 필드는 쓸 수 없게 막아야 합니다.

공식 참고: https://developer.android.com/build

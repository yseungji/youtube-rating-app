# YouTube Rating App

유튜브 시청자를 위한 영상 평가·취향 공유 Android 앱입니다.

본 영상을 별점과 한줄평으로 기록하고, 팔로우한 사람의 평가로 볼 영상을 찾는 서비스를 목표로 합니다.

## 현재 상태

팀 공동 작업을 위한 저장소와 작업 규칙을 준비한 단계입니다. Android 앱 소스와 Firebase 프로젝트 설정은 아직 등록하지 않았습니다.

## 구현 목표

| 화면 | 목표 |
|---|---|
| 피드 | 팔로우한 사람이 남긴 최신 평가와 영상, 리뷰 반응 |
| 탐색 | 등록된 영상·사용자 검색, 필요한 경우 YouTube 추가 검색 |
| 카테고리 | YouTube 카테고리를 기준으로 영상 분류·둘러보기 |
| 평가하기 | 별점 1~5점과 선택 한줄평 작성·수정 |
| 프로필 | 평가 기록, 팔로워·팔로잉, 보관함과 공개 범위 |
| 영상 상세 | YouTube 임베드 재생, 전체 평가와 팔로우 평가 |

최근 본 영상과 보관함은 앱 내 기록을 기준으로 구현합니다. 사용자 기반 협업 필터링은 기본 기능 완성 후 검토하는 부가 기능입니다.

## 개발 계획

- Android: Java와 XML Views 중심으로 시작합니다.
- Firebase: Authentication과 Cloud Firestore를 우선 활용할 계획입니다.
- 필요한 서버 처리: API 호출 관리, 평점 집계, 탈퇴 데이터 정리 등을 Cloud Functions로 구성할 수 있습니다.
- 프로필 사진 직접 업로드를 추가할 때 Cloud Storage를 검토합니다.

실제 SDK·JDK·Gradle 버전과 실행 명령은 Android 프로젝트가 추가되면 그 설정을 기준으로 기록합니다.

## 처음 참여하기

1. 저장소 초대를 수락합니다.
2. Android Studio의 `Get from VCS` 또는 `Clone Repository`에서 아래 주소로 복제합니다.

```text
https://github.com/yseungji/youtube-rating-app.git
```

3. Android 프로젝트가 등록되면 저장소 루트를 열고 Gradle Sync를 실행합니다.
4. Firebase를 연결한 뒤에는 [Firebase 설정 안내](docs/FIREBASE_SETUP.md)에 따라 팀 공용 설정 파일을 준비합니다.
5. [공동 작업 규칙](CONTRIBUTING.md)을 읽고 작업 브랜치에서 개발합니다.

아직 앱 프로젝트가 없으므로 현재 저장소만으로 앱을 실행하거나 빌드할 수는 없습니다. Android 프로젝트를 만들 때 Gradle Wrapper와 모듈 설정도 함께 올립니다.

## 공동 작업

`main`은 합쳐진 코드, `feature/작업명`은 각자 개발하는 브랜치로 사용합니다. 작업한 코드는 Pull Request로 공유하고 다른 팀원 1명에게 확인을 받은 뒤 합치는 것을 팀 규칙으로 합니다.

처음부터 별도 develop 브랜치는 만들지 않습니다. 실제 빌드가 가능한 프로젝트가 들어온 뒤 자동 빌드를 추가합니다.

| 팀원 | GitHub |
|---|---|
| 승지 | [@yseungji](https://github.com/yseungji) |
| 도엽 | [@Yeopdo](https://github.com/Yeopdo) |
| 추가 팀원 | 아이디 확인 후 초대 |

## 저장소 안내

- [CONTRIBUTING.md](CONTRIBUTING.md): 작업·리뷰·병합 순서
- [Android 프로젝트 등록](docs/ANDROID_SETUP.md): 첫 앱 프로젝트 추가 시 확인할 것
- [Firebase 설정](docs/FIREBASE_SETUP.md): 클라이언트 설정과 서버 비밀키 관리
- [남은 설정](docs/SETUP_STATUS.md): 팀원 추가와 앱 등록 이후 작업

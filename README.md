# Viewit · YouTube Rating App

유튜브 영상을 별점과 한줄평으로 기록하고, 팔로우한 사람의 평가로 볼 영상을 찾는 Android 앱입니다.

## 현재 상태

Android 앱 프로젝트가 추가되어 있으며, 기존에 구축한 Firestore에 연결하기 위한 Gradle 설정과 평가 저장 테스트 코드를 작성했습니다. Figma와 팀에서 정한 기준에 따라 컬렉션·문서·필드 구조를 정리했습니다.

- Java + XML Views, 단일 `app` 모듈
- Firebase 연결 설정 및 `CloudFirestoreActivity.addData()` 저장 테스트 코드
- [Firestore 데이터 구조](docs/FIRESTORE_SCHEMA.md) 설계
- 하단 5탭 뼈대: `MainActivity` + 탭별 빈 화면(`ui/feed`, `ui/search`, `ui/category`, `ui/rate`, `ui/profile`)
- 공통 데이터 클래스: `data/model` (`User`, `Video`, `Rating`, `Category`, `Follow`, 컬렉션 이름 `FirestorePaths`)
- 다음 작업: 2.3 공통 UI 컴포넌트

데이터 구조 문서는 설계 기준입니다. 로그인, 서비스용 평가 저장·조회, 보안 규칙, 집계, 5탭 UI가 모두 구현된 상태는 아닙니다.

## 구현 목표

| 화면 | 목표 |
|---|---|
| 피드 | 팔로우한 사람이 남긴 최신 공개 평가와 영상, 좋아요·댓글·공유 |
| 탐색 | 등록된 영상·사용자 검색, 필요한 경우 YouTube 추가 검색 |
| 카테고리 | YouTube 카테고리별 둘러보기, 좋아요 많은 인기 평가 |
| 평가하기 | 앱에서 본 영상·YouTube 좋아요 영상·구독 채널 영상에서 평가 진입 |
| 프로필 | 평가 기록, 팔로워·팔로잉, 나중에 보기와 공개 범위 |
| 영상 상세 | YouTube 임베드 재생, 1~5점 정수 별점·선택 한줄평, 평균·분포 |

평가마다 공개·비공개를 선택합니다. 시청 기록은 재생 시작 시 남기며, 리스트는 나중에 보기 하나로 시작합니다. 공유받은 영상과 개인 콘텐츠 숨김은 제외합니다. 사용자 차단은 포함하며 구체적인 적용 범위는 추가 합의합니다.

## 개발 환경

| 항목 | 현재 프로젝트 설정 |
|---|---|
| 언어·UI | Java, XML Views |
| applicationId | `com.example.viewit` |
| minSdk / compileSdk / targetSdk | 24 / 37 / 37 |
| Java 소스·타깃 호환성 | 11 |
| Android Gradle Plugin | 9.3.3 |
| Gradle Wrapper | 9.5.0 |
| Google services 플러그인 | 4.5.0 |
| Firebase BoM | 35.1.0 |

Java 소스 호환성과 Gradle을 실행하는 JDK 버전은 구분합니다. Android Studio의 Gradle JDK는 프로젝트의 AGP·Gradle과 호환되는 환경을 사용합니다.

## 처음 참여하기

1. 저장소 초대를 수락하고 복제합니다.
2. Android Studio에서 저장소 루트를 엽니다.
3. [Firebase 설정 안내](docs/FIREBASE_SETUP.md)에 따라 `app/google-services.json`을 준비합니다.
4. Gradle Sync를 실행하고 Android SDK와 Gradle JDK 설정을 확인합니다.
5. YouTube API를 쓰는 기능을 실행하려면 `local.properties`에 `YOUTUBE_API_KEY`를 추가합니다. ([개인 설정 값](docs/ANDROID_SETUP.md#개인-설정-값-localproperties))
6. `app` 실행 구성을 사용하면 하단 5탭 화면이 열립니다. Firebase 테스트 화면은 디버그 빌드에 함께 설치되는 "Viewit 테스트" 아이콘으로 엽니다. ([Android 설정](docs/ANDROID_SETUP.md#실행과-연결-테스트))
7. [공동 작업 규칙](CONTRIBUTING.md)에 따라 변경 사항을 공유합니다. 커밋 메시지 앞에는 `[학번 이름]`을 붙입니다.

```text
https://github.com/yseungji/youtube-rating-app.git
```

실행·빌드 명령과 현재 테스트 화면의 동작은 [Android 설정 안내](docs/ANDROID_SETUP.md)에 정리했습니다.

## 공동 작업

초기 설정 단계에서는 필요한 파일만 선택해 커밋하고 `main`에 직접 push합니다. 담당 기능을 나누어 개발할 때는 작업 브랜치와 PR을 사용합니다.

| 팀원 | GitHub |
|---|---|
| 승지 | [@yseungji](https://github.com/yseungji) |
| 도엽 | [@Yeopdo](https://github.com/Yeopdo) |
| 주원 | [@juwonhs0913](https://github.com/juwonhs0913) |

## 문서

- [Android 설정](docs/ANDROID_SETUP.md): 환경과 실행 방법
- [Firebase 설정](docs/FIREBASE_SETUP.md): 클라이언트 연결과 저장 테스트
- [Firestore 데이터 구조](docs/FIRESTORE_SCHEMA.md): 컬렉션·문서·필드와 동작 기준
- [설정 상태와 다음 작업](docs/SETUP_STATUS.md): 완료한 작업과 남은 작업
- [공동 작업 규칙](CONTRIBUTING.md): 커밋·push·PR 흐름

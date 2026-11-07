# 초기 설정과 남은 작업

## 앱·Firestore 진행 상태

- 기존 Firestore에 연결하도록 Android Studio·Gradle 프로젝트 설정
- `CloudFirestoreActivity.addData()` 평가 저장 테스트 코드와 성공·실패 로그 작성
- Figma와 합의한 기준으로 컬렉션·문서·필드 구조 구체화
- 공개 범위·시청 기록·나중에 보기·인기 평가·차단 포함·새 평가 확인 기준 정리

상세 설계는 [Firestore 데이터 구조](FIRESTORE_SCHEMA.md)를 참고합니다. 서비스 기능·보안 규칙·집계가 모두 구현된 상태는 아닙니다.

## 저장소 초기 설정 기록

아래 원격 설정과 초대 수락 여부는 이번 문서 수정에서 다시 조회하지 않았습니다.

- 비공개 저장소, 기본 브랜치 main
- Squash merge 사용, merge commit·rebase merge 비활성화
- 병합 후 작업 브랜치 자동 삭제
- Android 제외 파일, 줄바꿈·들여쓰기 설정
- README, 작업 규칙, 이슈 양식, PR 양식
- Yeopdo 및 juwonhs0913에 쓰기 권한 초대 발송

## main 보호의 적용 상태

저장소 초기 설정 당시 보호 브랜치 적용 요청에 Pro 업그레이드 또는 공개 저장소 전환이 필요하다는 응답이 있었고, 당시 유료·공개 전환은 하지 않았습니다.

현재 초기 설정 단계에서는 필요한 파일을 선택해 main에 직접 push합니다. 기능 개발 단계부터 작업 브랜치와 PR·리뷰 흐름을 사용합니다. 서버 보호 규칙이 적용된 것으로 가정하지 않으며 강제 push는 하지 않습니다.

## 다음 작업

- [ ] Yeopdo의 공동 작업 초대 수락 확인
- [x] juwonhs0913의 공동 작업 초대 수락 확인 (2026-11-04 저장소 복제)
- [x] Android 프로젝트 등록과 저장 테스트 코드 작성
- [x] 기존 Firebase 연결을 위한 Gradle 설정
- [x] 1.3 Firestore 데이터 구조 문서화
- [ ] 2.1 공통 개발 환경
  - [x] Firebase SDK 선언을 BoM + 문자열 방식 하나로 통일 (중복 Firestore 카탈로그 선언 제거)
  - [x] API 키 관리: `local.properties`의 `YOUTUBE_API_KEY` → `BuildConfig.YOUTUBE_API_KEY` ([안내](ANDROID_SETUP.md#개인-설정-값-localproperties))
  - [x] 커밋 메시지 규칙 `[학번 이름]` 추가 ([CONTRIBUTING](../CONTRIBUTING.md))
  - [x] 주원: 설정 파일 배치·빌드·에뮬레이터 저장 테스트 성공 (2026-11-04)
  - [ ] 승지·도엽: 각자 PC에서 Sync·빌드·저장 테스트 확인
  - [ ] 작업분해표 항목을 Issue로 등록하고 담당자 지정
- [x] 2.2 하단 5탭 뼈대·공통 데이터 클래스·앱 시작 화면 통일
  - [x] 탭 이름·아이콘, 하단 탭 메뉴·선택 색상, 탭별 빈 화면 레이아웃 (#2, #3)
  - [x] `MainActivity` 하단 5탭 + 탭별 Fragment 5개 (탭 화면을 다시 만들지 않고 숨기기·보이기로 전환) (#4)
  - [x] 앱 시작 화면을 `MainActivity` 하나로 정리, 기존 테스트 메뉴는 `DevMenuActivity`로 이름 변경(디버그 빌드에서만 "Viewit 테스트" 아이콘) (#4)
  - [x] 공통 데이터 클래스 `data/model`: `User`, `Video`, `Rating`, `Category`, `Follow` + 컬렉션 이름 상수 `FirestorePaths`
    - Firestore 필드 이름이 스키마와 같은지 단위 테스트 `DataModelMappingTest`로 확인
- [x] 2.3 영상 카드·별점·카테고리 칩 등 공통 컴포넌트 ([사용법](UI_COMPONENTS.md))
  - [x] A. 디자인 토큰(색 13개·글자 단계·간격·모서리)과 테마(흰 배경 모노톤, 다크 모드도 같은 밝은 테마) (#6)
  - [x] A. 기본 위젯: 검정·테두리 버튼, 선택 칩, 검색창 + 디버그 전용 컴포넌트 확인 화면 (#6)
  - [x] B. 영상: Thumbnail, VideoRow, CategoryCard, RatingTile, 별점·재생 시간 표시 함수
  - [x] C. 사람·리뷰: Author, ReviewActions, ReviewQuote, ReviewDetail, UserResult, FollowRow, LibraryRow, SearchHistoryRow, TopicChip
  - [x] D. 평가 입력: StarRatingInputView, RatingPanelView, VisibilityToggle, RatingStatisticsView
  - 썸네일 이미지 불러오기는 4.1에서 추가
- [ ] 보안 규칙·필요한 인덱스·공개 평가 집계 구현 및 검증
- [ ] 차단 적용 범위 합의와 기능 구현
- [ ] 실제 프로젝트 설정에 맞는 자동 빌드 추가

2.1 작업(2026-11-04)에서 `:app:assembleDebug` 빌드와 에뮬레이터(Medium Phone)에서 `CloudFirestoreActivity` 실행 시 Logcat `평가 저장 성공`을 확인했습니다. 다른 팀원 환경은 아직 확인하지 않았습니다.

공식 참고:

- https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches
- https://docs.github.com/en/get-started/git-basics/configuring-git-to-handle-line-endings

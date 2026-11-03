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
- [ ] juwonhs0913의 공동 작업 초대 수락 확인
- [x] Android 프로젝트 등록과 저장 테스트 코드 작성
- [x] 기존 Firebase 연결을 위한 Gradle 설정
- [x] 1.3 Firestore 데이터 구조 문서화
- [ ] 2.1 팀원별 설정 배치·Sync·저장 테스트 확인, SDK 선언 방식·키 관리 정리
- [ ] 2.2 하단 5탭 뼈대·공통 데이터 클래스·앱 시작 화면 통일
- [ ] 2.3 영상 카드·별점·카테고리 칩 등 공통 컴포넌트
- [ ] 보안 규칙·필요한 인덱스·공개 평가 집계 구현 및 검증
- [ ] 차단 적용 범위 합의와 기능 구현
- [ ] 실제 프로젝트 설정에 맞는 자동 빌드 추가

이번 작업은 문서 정리이며 빌드·기기 실행·Firestore 저장을 재검증한 결과는 포함하지 않습니다.

공식 참고:

- https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches
- https://docs.github.com/en/get-started/git-basics/configuring-git-to-handle-line-endings

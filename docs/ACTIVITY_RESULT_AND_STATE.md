# Activity 결과 전달·회전 상태 비교 (#29)

유승지 담당 피드·평가하기·상세 평가 영역의 리팩토링 기록. 테스트·빌드는 사용자가 직접 수행한다.

## Launcher 흐름

1. Fragment 생성 시 registerForActivityResult()로 Launcher와 결과 콜백을 등록한다.
2. 행 클릭 → Intent의 videoId·categoryId → launcher.launch(intent).
3. 상세의 getIntent()로 입력을 받는다.
4. 서버 저장·수정·삭제 성공 → setResult(RESULT_OK, Intent). ratingChanged=true와 videoId를 담는다.
5. 상세 종료 → 원래 Fragment의 onActivityResult() → 결과·Intent null·변경 여부 확인 → 해당 목록 갱신.

변경 없이 돌아오거나 실패하면 성공 결과를 만들지 않는다. 이미 한 번 성공한 후 다른 요청이 실패해도 앞선 변경 결과는 유지한다.
setResult()는 상세를 바로 닫지 않는다. 상단 뒤로가기와 시스템 뒤로가기로 종료할 때 결과가 전달된다.
Launcher로 연 상세의 상단 뒤로가기는 원래 화면으로 돌아간다. 일반 startActivity()로 직접 연 상세의 기존 메인 피드 이동은 유지한다.
Launcher는 Activity 사이 결과 전달 API다. Firestore·YouTube API 요청은 기존 저장소가 담당한다.

## 기본 버전: 재생성 후 저장·복원

현재 Manifest에는 상세 Activity의 configChanges를 지정하지 않는다.

- onSaveInstanceState(): 별점·한줄평·공개 범위·UID·카테고리·이미 성공한 평가 변경 여부를 Bundle에 저장한다.
- onCreate(): 저장된 상태를 읽고 입력 복원을 준비한다.
- onRestoreInstanceState(): 입력 복원을 시도한다. 내 평가 조회 중이면 성공 콜백에서 복원한다. 늦게 도착한 서버 값이 작성 중인 입력을 덮지 않도록 한다.
- 조회 도중 다시 회전하면 아직 적용하지 못한 입력 Bundle도 다시 저장한다.
- 피드 정렬 선택은 Fragment의 onSaveInstanceState()에 저장한다. 목록·조회 커서 전체를 Bundle에 넣지 않으며, 화면 재생성 시 목록을 다시 조회한다.

기존 XML의 고유 ID를 유지한다. 일부 기본 뷰의 자동 복원과 Java에서 직접 보관하는 입력 상태 복원을 구분한다.

## 비교 버전: configChanges

같은 화면에서 비교할 때만 AndroidManifest.xml의 .VideoDetailActivity에 다음 속성을 추가하고 다시 실행한다.

```xml
android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"
```

수업의 orientation·screenSize에 창 크기·화면 배치 변경을 포함한 설정이다. gravity는 배치 속성이다.
지정한 구성 변경에서는 Activity를 재생성하지 않고 onConfigurationChanged()가 호출된다. 구현한 콜백은 super를 호출하고 시스템 바 여백을 다시 적용하며 Logcat에 방향을 남긴다. 현재 XML은 가로·세로 공통이다.
동일 인스턴스에 입력이 남아 있으므로 해당 회전에서는 onRestoreInstanceState()로 입력을 되살리지 않는다. 이 설정이 프로세스 종료 후 복원을 대신하지는 않는다.
비교 후 위 속성을 제거하면 기본 재생성·복원 버전으로 돌아간다.

## Logcat에서 흐름 확인

태그는 FeedFragment·RateFragment·VideoDetailActivity를 사용한다. 세 화면의 오류만 보기:

```text
package:com.example.viewit (tag:FeedFragment | tag:RateFragment | tag:VideoDetailActivity) level:ERROR
```

같은 필터에서 level:ERROR를 level:DEBUG로 바꾸면 정상 흐름도 표시된다.
예상 순서: 상세 실행 → 평가 저장 요청 → 평가 저장 성공 → 결과 준비: setResult(RESULT_OK) → 상세 종료 → 상세 결과 수신 → 평가 변경 결과로 목록 갱신.
시스템 뒤로가기는 별도 상단 버튼 로그 없이 onDestroy와 결과 수신 로그를 확인한다.
변경 없이 복귀한 RESULT_CANCELED나 null Intent는 정상적인 결과일 수 있으므로 오류로 기록하지 않는다.
복원 완료 로그는 실제 입력값을 적용한 시점에 남긴다. 새 로그의 실행 검증은 사용자가 수행한다.

## 사용자 확인 항목 — 실행 검증 대기

- 피드와 평가하기에서 상세 진입 → 변경 없이 복귀 / 저장·수정·삭제 성공 후 복귀.
- 실패 시 성공 결과가 전달되지 않는지.
- 피드 인기순 선택 후 회전 시 정렬 유지.
- 상세의 별점·한줄평·공개 범위 입력 후 회전. 느린 조회·연속 회전에서도 입력 유지.
- 저장·삭제 성공 후 회전하고 돌아와도 변경 결과가 전달되는지.
- 두 회전 방식의 Logcat 비교: onSaveInstanceState·onRestoreInstanceState 대 onConfigurationChanged.

저장·삭제 요청 진행 중 회전·종료는 별도 확인이 필요하다. 진행 중인 비동기 작업 자체를 Bundle로 보존하거나 새 Activity에 재연결하는 처리는 이번 변경에 포함하지 않는다. 조회 실패 시 입력 복원은 재시도 성공 후 적용한다. 목록 전체·스크롤 위치 복원도 이번 상태 저장 범위에 포함하지 않는다.

공식 참고: [Activity 결과 API](https://developer.android.com/training/basics/intents/result), [Activity 상태](https://developer.android.com/reference/android/app/Activity), [구성 변경](https://developer.android.com/guide/topics/resources/runtime-changes).

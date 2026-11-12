# Firestore 데이터 구조

이 문서는 Viewit의 1.3 데이터 구조 설계입니다. 문서에 정의한 컬렉션, 보안 규칙, 집계 로직이 모두 구현되었다는 의미는 아닙니다.

기준: 2026-11-03 팀 요구사항 정리와 [Figma Page 4 · 5탭 UI](https://www.figma.com/design/wEfyU89iY8HaVx42LV1ENB/?node-id=63-556).

## 확정한 기준

- 카테고리는 `categories`, 평가는 `ratings`, 자기소개는 `introduction`으로 명명합니다.
- 카테고리 ID는 YouTube 카테고리 ID 문자열로 통일합니다.
- 사용자 한 명이 영상 하나에 평가 하나를 작성하고, 이후 같은 평가를 수정합니다.
- 별점은 1~5점 정수이며 한줄평은 선택 입력입니다.
- 공개 범위는 `public` / `private`입니다. 평가 작성·수정 시 각 평가의 공개 범위를 선택합니다.
- 비공개 평가는 작성자만 읽으며 공개 피드, 다른 사용자의 평가 목록, 공개 집계에서 제외합니다.
- 리스트는 나중에 보기 하나만 제공합니다. 나중에 보기는 평가가 아닌 영상에 연결합니다.
- 앱 시청 기록은 재생 시작 시 기록하고, 같은 영상을 다시 재생하면 마지막 시각을 갱신합니다.
- 카테고리의 인기 평가는 공개 평가의 좋아요 많은 순입니다.
- 사용자 차단을 포함합니다. 개인 콘텐츠 숨김과 공유받은 영상 기능은 제외합니다.
- 새 평가는 해당 작성자의 평가를 마지막으로 확인한 이후 작성된 공개 평가입니다.
- 프로필 사진은 사용자가 앨범에서 고른 사진을 Firebase Storage에 올려 사용합니다. Google 계정 사진은 쓰지 않습니다. (2026-11-12 팀 합의로 "Storage 사용 안 함" 결정을 변경)

## 1. 전체 컬렉션·문서 구조

`{…}`는 문서 ID입니다. 하위 컬렉션은 위 사용자 또는 평가 문서에 속합니다.

```text
Firestore
|
|-- users [사용자 프로필]
|   `-- {uid} [Firebase Authentication UID]
|       |-- username: String                         사용자 아이디
|       |-- displayName: String                      표시 이름
|       |-- photoUrl: String                         프로필 사진 URL(Storage), 없으면 빈 문자열
|       |-- introduction: String                     자기소개, 선택·최대 40자
|       |-- categoryIds: Array<String>               관심 카테고리, 최소 3개
|       |-- publicRatingCount: Integer               공개 평가 수
|       |-- publicRatingCountsByCategory: Map<String, Integer>
|       |                                             카테고리별 공개 평가 수
|       |-- followerCount: Integer                   팔로워 수
|       |-- followingCount: Integer                  팔로잉 수
|       |-- createdAt: Timestamp                     가입 시각
|       |-- updatedAt: Timestamp                     프로필 수정 시각
|       |
|       |-- settings [본인 설정]
|       |   `-- privacy [고정 문서]
|       |       |-- watchHistoryVisibility: String   시청 기록 공개 범위
|       |       |-- watchLaterVisibility: String     나중에 보기 공개 범위
|       |       |-- subscriptionVisibility: String   구독 채널 공개 범위
|       |       `-- updatedAt: Timestamp
|       |
|       |-- integrations [본인 연동 정보]
|       |   `-- youtube [고정 문서]
|       |       |-- connected: Boolean               YouTube 연동 여부
|       |       |-- connectedAt: Timestamp           연동 시각, 연결 시 저장
|       |       `-- updatedAt: Timestamp
|       |
|       |-- watchHistory [앱에서 본 영상]
|       |   `-- {videoId}
|       |       `-- lastWatchedAt: Timestamp         마지막 재생 시작 시각
|       |
|       |-- watchLater [나중에 볼 영상]
|       |   `-- {videoId}
|       |       `-- createdAt: Timestamp             보관 시각
|       |
|       |-- blockedUsers [본인이 차단한 사용자]
|       |   `-- {차단한 사용자 UID}
|       |       `-- createdAt: Timestamp             차단 시각
|       |
|       `-- ratingReadStates [본인의 평가 확인 상태]
|           `-- {평가 작성자 UID}
|               `-- lastCheckedAt: Timestamp         해당 작성자 평가의 마지막 확인 시각
|
|-- usernames [아이디 중복 방지]
|   `-- {username} [정규화한 사용자 아이디]
|       |-- uid: String                              아이디 소유자 UID
|       `-- createdAt: Timestamp
|
|-- categories [카테고리 목록]
|   `-- {categoryId} [YouTube 카테고리 ID]
|       |-- name: String                             카테고리 이름
|       |-- sortOrder: Integer                       표시 순서
|       `-- isActive: Boolean                        앱에서 사용하는지
|
|-- videos [영상 정보와 평가 집계]
|   `-- {videoId} [YouTube 영상 ID]
|       |-- title: String                            영상 제목
|       |-- channelId: String                        YouTube 채널 ID
|       |-- channelTitle: String                     채널 이름
|       |-- thumbnailUrl: String                     썸네일 URL
|       |-- categoryId: String                       카테고리 ID
|       |-- durationSeconds: Integer                 재생 시간, 초
|       |-- publishedAt: Timestamp                   영상 게시 시각
|       |-- fetchedAt: Timestamp                     영상 정보 수집 시각
|       |-- publicRatingCount: Integer               공개 평가 수
|       |-- ratingSum: Integer                       공개 별점 합계
|       |-- ratingAverage: Double                    공개 별점 평균
|       |-- ratingDistribution: Map<String, Integer>
|       |   |-- s1: Integer                          1점 평가 수
|       |   |-- s2: Integer                          2점 평가 수
|       |   |-- s3: Integer                          3점 평가 수
|       |   |-- s4: Integer                          4점 평가 수
|       |   `-- s5: Integer                          5점 평가 수
|       `-- statsUpdatedAt: Timestamp                집계 갱신 시각
|
|-- ratings [평가 원본]
|   `-- {uid}_{videoId} [사용자당 영상별 평가 하나]
|       |-- userId: String                           작성자 UID
|       |-- videoId: String                          영상 ID
|       |-- categoryId: String                       연결된 영상의 카테고리 ID
|       |-- score: Integer                           1, 2, 3, 4, 5 중 하나
|       |-- comment: String                          선택 한줄평, 미입력 시 빈 문자열
|       |-- visibility: String                       public 또는 private
|       |-- likeCount: Integer                       평가 좋아요 수
|       |-- commentCount: Integer                    댓글 수
|       |-- createdAt: Timestamp                     최초 작성 시각
|       |-- updatedAt: Timestamp                     마지막 수정 시각
|       |
|       |-- likes [평가 좋아요]
|       |   `-- {uid} [좋아요를 누른 사용자 UID]
|       |       `-- createdAt: Timestamp
|       |
|       `-- comments [평가에 달린 댓글]
|           `-- {자동ID}
|               |-- userId: String                   댓글 작성자 UID
|               |-- content: String                  댓글 내용
|               |-- createdAt: Timestamp
|               `-- updatedAt: Timestamp
|
`-- follows [팔로우 관계]
    `-- {followerId}_{followingId}
        |-- followerId: String                       팔로우하는 사용자 UID
        |-- followingId: String                      팔로우받는 사용자 UID
        `-- createdAt: Timestamp                     팔로우 시각
```

### Firebase Storage

```text
Storage
|
`-- profileImages [프로필 사진]
    `-- {uid}.jpg                                    512×512 JPEG, 사용자당 파일 하나
```

- 프로필 만들기(3.2)에서 고른 사진을 512×512 JPEG로 줄여 올리고, 받은 다운로드 URL을 `users/{uid}.photoUrl`에 저장합니다.
- 파일 이름을 `{uid}.jpg` 하나로 고정해, 다시 올리거나 사진을 바꾸면 같은 파일을 덮어씁니다.
- 사진을 고르지 않으면 파일을 만들지 않고 `photoUrl`은 빈 문자열입니다. 화면에서는 기본 프로필 그림(`ic_avatar_default`)을 보여 줍니다.

### 자료형과 공통 저장 규칙

| 표기 | Java에서 사용하는 형태 | 규칙 |
|---|---|---|
| String | `String` | 연결 ID는 Firestore Reference 대신 문자열로 통일 |
| Integer | `long` / `Long` | 별점·개수·초 단위 시간 |
| Double | `double` / `Double` | 평균 별점 |
| Boolean | `boolean` / `Boolean` | 참·거짓 |
| Timestamp | Firebase `Timestamp` | 앱에서 기록하는 작성·수정 시각은 서버 시각 사용 |
| Array<String> | `List<String>` | 관심 카테고리 ID |
| Map<String, Integer> | `Map<String, Long>` | 카테고리별 개수·별점 분포 |

- 문서 ID에 이미 포함된 ID는 별도 필드로 반복하지 않습니다. 다만 다른 컬렉션에서 작성자·영상별로 조회하는 `ratings`, `follows`에는 조회용 ID 필드를 둡니다.
- `ratings.categoryId`는 카테고리별 인기 평가 조회용이며 `videos.categoryId`와 동일하게 유지합니다.
- 합성 문서 ID 생성 방식은 저장·조회·보안 규칙에서 공통으로 사용하고, 입력 ID가 달라도 같은 문서 ID가 생성되지 않도록 규칙을 정합니다.
- 영상 제목·썸네일은 우선 `videos`에서 읽습니다. 시청 기록과 나중에 보기에는 중복 저장하지 않습니다.
- 사용자 아이디는 영문·숫자·밑줄 3~20자로 검증하고 소문자로 정규화합니다. 아이디 예약과 프로필 저장은 트랜잭션으로 처리합니다.
- 평가하지 않은 영상에는 평가 문서를 만들지 않습니다. 0점을 미평가 표시로 저장하지 않습니다.

## 2. 저장·갱신·공개 기준

### 평가와 공개 집계

- 각 평가의 `visibility`가 공개 범위의 기준입니다. 사용자 설정에 평가의 기본 공개 범위 필드는 두지 않습니다.
- 공개 평가만 피드, 다른 사용자 프로필, 영상 평균·분포, 평가자 추천용 개수에 포함합니다.
- 본인의 평가 목록에는 본인의 공개·비공개 평가를 모두 표시합니다.
- 본인 전체 평가 수는 본인 평가 조회로 계산하고, 다른 사용자에게는 공개 평가 수를 표시합니다.
- 새 평가의 좋아요 수와 댓글 수는 0으로 시작합니다.
- `ratings`가 원본이고 영상·사용자의 개수와 평균·분포는 집계값입니다.
- 2026-11-11 결정: 영상 상세 통계는 별도 서버 함수 없이 Firestore 집계 쿼리로 조회합니다. 팔로우 여부와 관계없이 해당 영상의 유효한 공개 평가 전체(본인 공개 평가 포함)가 대상입니다.
- 화면 진입·새로고침·평가 저장/수정/삭제 성공 시 통계와 공개 목록을 다시 조회합니다. 실시간 구독은 하지 않는다.
- 평균·개수·합계와 1~5점별 개수를 조회하며, 현재 페이지의 항목 개수로 전체 통계를 계산하지 않습니다. 공개↔비공개 변경도 재조회 결과에 반영됩니다.
- 이번 구현은 `videos`의 집계 필드를 갱신하지 않습니다. 위 스키마의 영상 집계 필드와 사용자·카테고리 집계는 저장형 집계가 필요할 때의 설계로 남습니다. 따라서 기존 `videos.ratingAverage` 정렬을 이번 조회만으로 구현한 것은 아닙니다.
- 요약 쿼리와 점수별 개수 쿼리는 동일 시점의 원자적 조회가 아닙니다. 개수·합계가 불일치하면 성공으로 표시하지 않고 재조회할 수 있도록 오류를 전달합니다.
- 집계 오류를 '평가 없음'으로 처리하지 않습니다. 집계용 복합 인덱스 정의는 `firestore.indexes.json`에 있으며 실제 프로젝트 반영과 조회 검증은 별도입니다.
- 공개 평가가 없으면 개수·합계·평균·분포를 0으로 두고 화면에는 평균을 `—`로 표시합니다.

### 기록·관계

| 동작 | 저장 기준 |
|---|---|
| 영상 재생 시작 | `watchHistory/{videoId}.lastWatchedAt` 생성 또는 갱신 |
| 나중에 보기 추가 | `watchLater/{videoId}` 생성 |
| 나중에 보기 취소 | 해당 문서 삭제 |
| 평가 좋아요 | `ratings/{ratingId}/likes/{uid}` 생성 |
| 좋아요 취소 | 해당 좋아요 문서 삭제 |
| 팔로우 | `follows` 문서 생성, 자기 자신 팔로우 금지 |
| 차단 | `blockedUsers/{상대 UID}` 생성 |
| 차단 해제 | 해당 차단 문서 삭제 |

사용자 차단은 포함하되, 차단 시 기존 팔로우·댓글을 어떻게 처리하고 상대의 접근을 어디까지 제한할지는 추가 합의가 필요합니다. 차단 문서를 생성하는 것만으로 접근 제한이 자동 적용되지는 않습니다.

### 새 평가 개수

1. 처음 팔로우할 때 `ratingReadStates/{작성자 UID}.lastCheckedAt`을 현재 시각으로 초기화합니다.
2. 해당 작성자의 공개 평가 중 `createdAt > lastCheckedAt`인 평가를 새 평가로 셉니다.
3. 해당 작성자의 평가 목록을 열어 정상적으로 불러오면 확인 시각을 갱신하고 표시를 해제합니다.
4. 팔로잉 목록만 보거나 평가 목록 조회에 실패했을 때는 확인 시각을 갱신하지 않습니다.
5. 기존 평가 수정은 새 평가에 포함하지 않습니다. `updatedAt`이 아닌 `createdAt`을 사용합니다.
6. 새 평가 개수 자체는 별도 필드에 저장하지 않습니다. 사용자별 확인 시각으로 계산합니다.

현재 기준은 작성자별 목록 확인이며, 평가 한 개마다 읽음 문서를 만들지는 않습니다. 구현 시 조회 중 추가된 평가까지 확인 처리되지 않도록 조회 기준 시각을 일관되게 사용합니다.

### 접근 범위

- `settings`, `integrations`, `blockedUsers`, `ratingReadStates`는 본인 전용입니다.
- 시청 기록·나중에 보기·구독 채널은 각각의 공개 범위를 따릅니다.
- 비공개 평가와 그 하위 좋아요·댓글은 평가 작성자에게만 읽기를 허용합니다.
- 프로필의 공개 문서에는 이메일·비밀번호·OAuth 토큰을 넣지 않습니다.
- 실제 접근 제어는 Security Rules와 필요한 서버 처리로 구현합니다. 문서 경로만으로 보호되지는 않습니다.
- 탈퇴·평가 삭제 시 하위 컬렉션과 연결 데이터도 정리해야 합니다. 부모 문서 삭제만으로 하위 문서가 자동 삭제되지는 않습니다.
- 내 활동(내가 좋아요한 리뷰·내가 쓴 댓글 목록)은 본인 전용입니다.
- Storage `profileImages/{uid}.jpg`는 로그인한 사용자만 읽고, 본인만 올리거나 바꿉니다. 이미지 형식과 크기(2MB 미만)도 Storage 규칙으로 제한합니다.
- 탈퇴 시 Storage의 프로필 사진도 지워야 합니다. Firestore 문서를 지워도 Storage 파일은 자동으로 삭제되지 않습니다.
- 부적절한 프로필 사진의 차단·검수는 과제 범위에서 제외하고 배포 전에 결정합니다.

## 3. 화면별 조회 기준

| 화면·기능 | 기준 |
|---|---|
| 팔로잉 피드 | 팔로우한 작성자의 공개 평가, 최초 작성 시각 내림차순, 20개씩 |
| 팔로우 0명인 초기 피드 | 공개 인기 평가로 대체, 우선 좋아요 많은 순으로 제안 |
| 영상 전체 평가 | `videoId` + `visibility == public` |
| 내 평가 | `userId == 내 UID` |
| 다른 사용자의 평가 | 해당 `userId` + `visibility == public` |
| 카테고리 인기 평가 | `categoryId` + `visibility == public`, `likeCount` 내림차순 |
| 카테고리 평점순 영상 | `videos.categoryId`, `ratingAverage` 내림차순 |
| 최근 본 영상 | `watchHistory.lastWatchedAt` 내림차순 |
| 나중에 보기 | `watchLater.createdAt` 내림차순 |
| 팔로워·팔로잉 | 해당 관계 ID 조건, `follows.createdAt` 내림차순 |
| 새 평가 개수 | 작성자 ID + 공개 + `createdAt > lastCheckedAt` |
| 온보딩 추천 | 선택한 카테고리의 공개 평가 수 기준 |

위 조회에 필요한 복합 인덱스는 실제 쿼리 구현 시 `firestore.indexes.json`으로 관리합니다. 팔로잉 수 증가에 따른 조회 분할, 영상·사용자 검색 방식은 기능 구현 단계에서 결정합니다.

## 4. 별도 컬렉션을 만들지 않아도 되는 항목

| 항목 | 처리 방식 |
|---|---|
| Google 로그인·인증 | Firebase Authentication |
| 최근 검색어 | 우선 기기 내부 저장 |
| 피드 | 팔로우 관계를 확인한 뒤 공개 `ratings` 조회 |
| 내가 평가한 영상 | `ratings`에서 내 `userId`로 조회 |
| 다른 사용자의 공개 평가 | 작성자와 공개 여부 조건으로 `ratings` 조회 |
| 프로필 평가 썸네일 | `ratings.videoId`로 `videos` 연결 |
| 새 평가 개수 | `ratingReadStates`와 `ratings.createdAt` 기준 계산 |
| 맞팔로우 표시 | 반대 방향 `follows` 문서 존재 여부 확인 |
| 카테고리 인기 평가 목록 | 카테고리·공개 여부·좋아요 수로 `ratings` 조회 |
| 영상 평균·별점 분포 | `videos`의 집계 필드 사용 |
| YouTube에서 좋아요한 영상 | YouTube 연동 API로 조회 |
| 구독 채널과 해당 영상 | YouTube 연동 API로 조회 |
| 공유 버튼 | 영상 또는 평가 링크 공유 |
| 이용약관·개인정보처리방침 | 정적 문서 |
| 앱 버전 | 앱 버전 정보 |

YouTube의 좋아요 영상과 평가 좋아요는 서로 다른 기능입니다. 연동 API의 결과를 캐시할 필요가 생기면 갱신·삭제 기준과 함께 별도 설계합니다.

## 제외·미확정 항목

### 이번 구조에서 제외

- `receivedVideos`: 공유받은 영상 버튼 제거에 따라 제외
- `hiddenItems`: 개인 콘텐츠 숨김 기능 제외
- `bookmarks/{ratingId}`: 평가 보관 제외, 영상의 `watchLater`로 통일
- 여러 개의 사용자 리스트
- 사용자별 기본 평가 공개 범위 설정
- 0.5점 단위 별점과 분포

### 추가 합의가 필요한 항목

- 싫어요: 일부 Figma 설명에는 있으나 현재 카드에는 좋아요만 있어 이번 구조에는 포함하지 않음
- 사용자 차단의 적용 범위와 기존 관계·콘텐츠 처리
- 신고 기능의 이번 구현 포함 여부와 처리 흐름
- 카테고리 영상 카드 전체의 인기 정렬 및 베스트 평가 노출 방식
- 프로필의 최근 평가 정렬을 최초 작성 시각 또는 수정 시각 중 무엇으로 할지
- 구독 채널 공개 기능의 연동 데이터 제공·캐시 방식

## 현재 테스트 코드와의 차이

`CloudFirestoreActivity.addData()`는 연결 확인용으로 `ratings/testRating`에 `videoId`, `score: 4.5`, `comment`만 저장합니다. 화면 생성 시 다시 실행되어 같은 문서를 덮어씁니다.

이 테스트 문서는 위 서비스 스키마에 맞는 평가가 아닙니다. 실제 기능 구현 시에는 1~5점 정수, 작성자 ID, 공개 범위, 시각과 문서 ID 규칙을 적용합니다. 기존 테스트 데이터도 서비스 데이터와 구분해 정리합니다.

## 참고

- [Figma · 평가하기](https://www.figma.com/design/wEfyU89iY8HaVx42LV1ENB/?node-id=95-1599)
- [Figma · 영상 상세](https://www.figma.com/design/wEfyU89iY8HaVx42LV1ENB/?node-id=105-1381)
- [Figma · 온보딩](https://www.figma.com/design/wEfyU89iY8HaVx42LV1ENB/?node-id=191-1383)
- [Figma · 설정](https://www.figma.com/design/wEfyU89iY8HaVx42LV1ENB/?node-id=172-1213)
- [Firestore 데이터 모델](https://firebase.google.com/docs/firestore/data-model)
- [Firestore 필드 접근 제어](https://firebase.google.com/docs/firestore/security/rules-fields)

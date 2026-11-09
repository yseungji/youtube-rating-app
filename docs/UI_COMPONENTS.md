# 공통 디자인과 컴포넌트

Figma [00 · 디자인 시스템](https://www.figma.com/design/wEfyU89iY8HaVx42LV1ENB/?node-id=63-560)과 [03 · 공통 컴포넌트](https://www.figma.com/design/wEfyU89iY8HaVx42LV1ENB/?node-id=63-563)를 Android 리소스로 옮긴 목록입니다. 화면을 만들 때 색·크기·글자를 직접 적지 말고 아래 이름을 사용합니다.

흰 배경, 검정 글자, 얇은 구분선이 기본이고 **코랄(`positive`)은 별점과 반응에만** 씁니다.

## 색 (`res/values/colors.xml`)

| 이름 | 값 | 쓰는 곳 |
|---|---|---|
| `bg_surface` | #FFFFFF | 화면·카드 배경 |
| `bg_canvas` | #FAFAFA | 화면 바깥 캔버스 |
| `text_primary` | #121212 | 주 텍스트 |
| `text_secondary` | #737373 | 보조 텍스트·아이콘 |
| `border_default` | #DBDBDB | 구분선·테두리 |
| `action_primary` | #111111 | 검정 버튼·선택 칩 |
| `action_soft` | #F5F5F5 | 기본 칩·아바타 배경 |
| `positive` | #FF4F3D | 별점·좋아요 (코랄) |
| `positive_soft` | #F4F4F4 | 긍정 배경 |
| `negative` | #FF3040 | 오류·회원 탈퇴 |
| `attention` | #F0EDE8 | 따뜻한 강조 |
| `accent_deep` | #141414 | 딥 네이비 |

## 글자 (`res/values/type.xml`)

`android:textAppearance="@style/..."`로 씁니다. 글꼴은 안드로이드 기본 한글 글꼴(Noto Sans CJK)입니다.

| 스타일 | 크기 | 쓰는 곳 |
|---|---|---|
| `TextAppearance.Viewit.Display` | 28 굵게 | 탭 화면 큰 제목 |
| `TextAppearance.Viewit.Heading` | 20 굵게 | 섹션 제목 |
| `TextAppearance.Viewit.Body` | 14 | 본문·한줄평 |
| `TextAppearance.Viewit.Body.Strong` | 14 굵게 | 영상 제목·사용자 이름 |
| `TextAppearance.Viewit.Body.Secondary` | 14 회색 | 채널명·재생 시간 |
| `TextAppearance.Viewit.Caption` | 11 회색 | 아이디·작성 시각 |
| `TextAppearance.Viewit.Button` / `.Chip` | 15 굵게 / 13 | 버튼·칩 글자 (스타일에 이미 포함) |

## 간격·크기 (`res/values/dimens.xml`)

8pt 기반입니다. `space_xs` 4 · `space_s` 8 · `space_m` 12 · `space_l` 16 · `space_xl` 24 · `space_xxl` 32, 화면 좌우 여백 `screen_padding` 16, 모서리 `radius_s` 10 · `radius_m` 12, `button_height`·`search_field_height` 48, `chip_height` 36, `app_bar_height` 64, 구분선 `divider` 1.

## 위젯

| 컴포넌트 | 사용법 |
|---|---|
| 검정 버튼 (UI4/Button / Primary) | `style="@style/Widget.Viewit.Button.Primary"` (스타일을 안 적은 버튼도 기본으로 이 모양). 비활성 시 회색 |
| 흰 테두리 버튼 (UI4/Button / Secondary) | `style="@style/Widget.Viewit.Button.Secondary"` |
| 선택 칩 (UI4/Chip) | `ChipGroup` 안의 `Chip`에 `style="@style/Widget.Viewit.Chip"`. 선택하면 검정 |
| 검색창 (UI4/SearchField) | `<include layout="@layout/view_search_field" />`, 입력값은 `R.id.search_input` |
| 작은 버튼 | 검정 `Widget.Viewit.Button.Small`("팔로우"), 회색 `Widget.Viewit.Button.Small.Tonal`("팔로잉") |
| 주제 칩 (UI4/TopicChip) | `style="@style/Widget.Viewit.Chip.Topic"`. 선택하면 연한 주황 + 주황 테두리 (온보딩 전용) |

## 영상 컴포넌트

목록 한 줄짜리는 `item_*.xml`이라 RecyclerView 어댑터에서 `ItemVideoRowBinding.inflate(...)`처럼 바로 쓸 수 있습니다. 썸네일 이미지는 아직 불러오기 라이브러리가 없어 회색 칸으로 나오며, 4.1에서 이미지 로딩을 붙입니다.

| 컴포넌트 | 파일 | 채울 곳 |
|---|---|---|
| 썸네일 (UI4/Thumbnail) | `view_thumbnail.xml` (16:9, 재생 시간 배지) | `thumbnail_image` ← `ThumbnailLoader.load(...)`, `thumbnail_duration` ← `DurationFormat.format(초)` |
| 영상 한 줄 (UI4/VideoRow) | `item_video_row.xml` | `video_row_title`, `video_row_meta`, `video_row_action`("평가하기 →", 필요 없으면 GONE) |
| 카테고리 영상 (UI4/CategoryCard) | `item_category_video.xml` | `category_video_title`, `category_video_rating` ← `getString(R.string.video_average, RatingFormat.averageText(평균, 개수))` |
| 평가 타일 (UI4/RatingTile) | `item_rating_tile.xml` (정사각형, "★4" 배지) | `rating_tile_image`, `rating_tile_score` |

## 사람·리뷰 컴포넌트

| 컴포넌트 | 파일 | 채울 곳 |
|---|---|---|
| 작성자 (UI4/Author) | `view_author.xml` | `author_avatar`(이름 첫 글자), `author_name`, `author_meta` ← `getString(R.string.author_meta, 아이디, TimeAgoFormat.format(...))` |
| 반응 버튼 (UI4/ReviewActions) | `view_review_actions.xml` | `review_action_like`(좋아요 n) · `_comment` · `_share` · `_watch_later`. 누른 상태 아이콘은 `ic_heart`, `ic_bookmark` |
| 한줄평 인용 (UI4/ReviewQuote) | `view_review_quote.xml` | `review_quote_title`("승지의 한줄평"), `review_quote_rating`, `review_quote_comment` |
| 평가 한 개 (UI4/ReviewDetail) | `item_review_detail.xml` | `review_author`(작성자), `review_rating`, `review_comment`, `review_actions` |
| 사용자 검색 결과 (UI4/UserResult) | `item_user_result.xml` | `user_result_avatar`, `_name`, `_meta`, `_follow` 버튼 |
| 팔로우 목록 (UI4/FollowRow) | `item_follow_row.xml` | `follow_row_avatar`, `_name`, `_meta`, `_new`(새 평가, 없으면 GONE), `_button`, `_more` |
| 보관함 행 (UI4/LibraryRow) | `item_library_row.xml` | `library_row_icon`, `library_row_label` |
| 최근 검색어 (UI4/SearchHistoryRow) | `item_search_history.xml` | `search_history_text`, `search_history_delete` |

별점 글자는 `RatingFormat.stars(점수) + " " + getString(R.string.rating_score, 점수)` → "★★★★☆ 4점"으로 만듭니다.

## 평가 입력 컴포넌트

동작이 있는 컴포넌트는 커스텀 뷰라 XML에 태그로 넣고 Java에서 메서드를 부릅니다.

| 컴포넌트 | 태그 | 주요 메서드 |
|---|---|---|
| 별점 입력 | `com.example.viewit.ui.common.StarRatingInputView` | `getScore()`, `setScore(int)`, `setOnScoreChangeListener(...)` |
| 평가 패널 (UI4/RatingPanel) | `com.example.viewit.ui.common.RatingPanelView` | `getScore()`, `getComment()`, `getRatingVisibility()`(public/private), `setSaved(true)`→"평가 수정", `setOnSaveClickListener(...)`, `setOnWatchLaterClickListener(...)`. 별을 고르기 전에는 저장 버튼이 비활성 |
| 공개 범위 토글 (UI4/VisibilityToggle) | `<include layout="@layout/view_visibility_toggle" />` | `visibility_group.getCheckedChipId() == R.id.visibility_public` |
| 별점 통계 (UI4/RatingStatistics) | `com.example.viewit.ui.common.RatingStatisticsView` | `setStats(video)` 또는 `setStats(평균, 개수, 분포)`. 막대는 가장 많은 점수를 100%로 |

## 공통 함수 (`ui/common`)

| 함수 | 예 |
|---|---|
| `RatingFormat.stars(4)` / `stars(3.6)` | "★★★★☆" |
| `RatingFormat.averageText(4.0, 3)` | "4.0" (평가 0개면 "—") |
| `RatingFormat.labelRes(4)` | R.string.rating_label_4 ("좋아요") |
| `DurationFormat.format(750)` | "12:30" (1시간 넘으면 "1:02:03") |
| `TimeAgoFormat.format(context, 작성시각, 지금)` | "방금 전 / 5분 전 / 3시간 전 / 2일 전 / 2026.11.06" |
| `ThumbnailLoader.load(이미지뷰, video.getThumbnailUrl())` | 썸네일 이미지 불러오기(Glide). 불러오는 동안·실패 시 회색 칸 |
| `YouTubeErrorMessage.of(context, 오류)` | YouTube 호출 실패 안내 문구 ("인터넷 연결을 확인해 주세요." 등) |

한국어 제목·한줄평은 Android 13 이상에서 단어 단위로 줄이 바뀝니다(`lineBreakWordStyle="phrase"`). 그보다 낮은 버전에서는 글자 단위로 바뀝니다.

## 확인 화면

디버그 빌드에는 위 컴포넌트를 모두 모아 보는 `debug/ComponentGalleryActivity`가 있습니다(아이콘 없음, 출시용 빌드에는 없음). Android Studio 실행 구성에서 Launch를 `Specified Activity` → `com.example.viewit.debug.ComponentGalleryActivity`로 지정해 엽니다. 예시 데이터를 넣는 코드가 각 컴포넌트를 채우는 방법의 예시이기도 합니다. 새 공통 컴포넌트를 만들면 이 화면에도 추가합니다.

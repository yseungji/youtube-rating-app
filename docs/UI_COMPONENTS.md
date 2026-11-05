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

## 확인 화면

디버그 빌드에는 위 컴포넌트를 모아 보는 `debug/ComponentGalleryActivity`가 있습니다(아이콘 없음, 출시용 빌드에는 없음). Android Studio 실행 구성에서 Launch를 `Specified Activity` → `com.example.viewit.debug.ComponentGalleryActivity`로 지정해 엽니다. 새 공통 컴포넌트를 만들면 이 화면에도 추가합니다.

## 남은 공통 컴포넌트 (2.3 후속)

- 영상: Thumbnail(재생 시간 배지), VideoRow, CategoryCard, RatingTile, 별점 표시
- 사람·리뷰: Author, ReviewActions, ReviewQuote, ReviewDetail, UserResult, FollowRow, LibraryRow, SearchHistoryRow
- 평가 입력: RatingPanel, VisibilityToggle, RatingStatistics

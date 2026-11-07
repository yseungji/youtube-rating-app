# 공동 작업 규칙

## 현재 초기 설정 단계

Android·Firebase 설정과 Firestore 구조 문서는 각각 별도 커밋으로 정리해 main에 직접 push합니다. 필요한 파일만 선택해 추가하고, push 전에는 원격 변경을 확인합니다. 이 단계에는 PR을 필수로 요구하지 않습니다.

현재 작업 브랜치에 미완료 변경이 있다면 보존한 뒤 main으로 전환합니다. 강제 push하지 않습니다.

## 기능 개발 단계의 기본 흐름

담당 기능 개발을 시작할 때는 작업 하나를 정하고 브랜치를 만든 뒤, Pull Request에서 코드와 실행 결과를 함께 확인합니다.

```bash
git switch main
git pull --ff-only
git switch -c feature/feed
```

위 브랜치명은 예시입니다. 같은 브랜치를 여러 작업에 재사용하지 않습니다.

| 작업 | 이름 예시 |
|---|---|
| 기능 추가 | feature/feed, feature/rating |
| 오류 수정 | fix/rating-save |
| 문서 수정 | docs/firebase-setup |

## 커밋과 PR

작업한 파일을 먼저 확인한 뒤 필요한 파일만 선택해 추가합니다.

```bash
git status
git diff
git add app/src/main/java/패키지/수정한파일.java
git commit -m "10_[20260000 홍길동] feat: 평가 저장 기능 추가 (5.2)"
git push -u origin feature/feed
```

파일 경로와 브랜치명은 자신의 작업에 맞게 바꿉니다. Android Studio의 Commit·Push 화면을 사용해도 됩니다.

커밋 메시지는 `주차_[학번 이름] 종류: 내용 (작업 번호)` 형식으로 씁니다. 맨 앞의 주차(예: 10주차면 `10_`)와 학번·이름 표기는 교수님 요청이고, 작업 번호는 역할분담·작업분해표의 중분류 번호입니다. 종류는 `feat`(기능), `fix`(오류), `docs`(문서), `chore`(설정) 중에서 고릅니다.

1. GitHub에서 작업 브랜치를 선택하고 `Compare & pull request`를 누릅니다.
2. base는 `main`으로 두고 PR 양식의 변경 내용·실행 확인을 적습니다.
3. 다른 팀원 1명에게 리뷰를 요청합니다.
4. 문제가 없으면 `Squash and merge`로 합칩니다.
5. 병합 후 원격 브랜치는 자동 삭제하도록 설정합니다. 각자 main을 다시 받아 다음 작업을 시작합니다.

리뷰 1명은 팀 운영 규칙입니다. 서버에서 강제되는지 여부는 저장소의 Branch protection 설정과 계정 플랜에 따라 달라집니다.

## 같이 수정할 때

- 큰 기능을 시작하기 전에 Issue에서 담당자와 완료 조건을 정합니다.
- 같은 Activity·XML·Gradle 파일을 동시에 크게 수정할 때는 먼저 작업 범위를 나눕니다.
- main이나 다른 사람의 브랜치에 강제 push하지 않습니다.
- 충돌이 나면 다른 사람의 코드를 통째로 지우거나 무조건 내 것으로 덮어쓰지 않습니다. 관련 팀원과 의도를 확인합니다.
- 새 라이브러리는 Gradle 설정 변경과 선택 이유를 PR에 같이 적습니다.

## 실행 확인

- 실제 실행한 화면과 동작을 적습니다.
- UI가 바뀌면 스크린샷이나 짧은 실행 영상을 첨부하면 좋습니다.
- 아직 앱 프로젝트가 없거나 실행하지 못한 경우 그대로 적습니다. 실행하지 않은 테스트를 통과했다고 쓰지 않습니다.

## 공유할 파일

앱 소스, XML 리소스, Manifest, Gradle 설정과 Wrapper는 공유합니다. 개인 SDK 경로·빌드 결과·서명키·서버 비밀 자격증명은 제외합니다.

Firebase 클라이언트 설정의 배치 방법은 [Firebase 설정 안내](docs/FIREBASE_SETUP.md)를 따릅니다. 보안 규칙과 인덱스 설정은 Firebase를 초기화한 뒤 버전 관리합니다.

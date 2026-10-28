# 발맞춤

> 모르는 길을 시간에 맞춰 걷는 대학생에게, **휴대폰을 보지 않아도** 제때 도착할 수 있는지 **손목 진동**으로 알려 주는 갤럭시 워치 앱

처음 미리 재 둔 **내 속도**(보통 걸음 / 빠른 걸음 / 뛰기)를 기준으로, 걷는 동안 "남은 거리 ÷ 남은 시간"을 계속 계산합니다. 상태가 바뀔 때만 진동으로 알려 줍니다.

| 상태 | 뜻 | 화면 |
|---|---|---|
| 여유 있음 | 보통 걸음으로 충분 | 🟢 초록 |
| 서두르면 맞춤 | 빠른 걸음이면 맞음 | 🟡 노랑 |
| 뛰어야 맞춤 | 뛰어야 맞음 | 🔴 빨강 |
| 뛰어도 어려움 | 뛰어도 늦음 | ⚫ 회색 |

**할 일 목록과 일정은 [`TODOLIST.md`](TODOLIST.md)에 있습니다.** 이 README는 개발 환경을 갖추고 앱을 처음 실행하는 데까지만 다룹니다.

---

## 1. 설치할 것 (처음 한 번, 약 40분)

**팀원 모두 설치합니다.** 코드를 안 맡는 사람(포스터·보고서·실측 담당)도 앱을 직접 실행해 봐야 화면 캡처, 시연, 결과 정리를 할 수 있습니다. 커밋·푸시도 Android Studio 안에서 하므로 다른 프로그램은 필요 없습니다.

| 순서 | 할 일 | 걸리는 시간 |
|---|---|---|
| 1-1 | GitHub 계정 만들고 저장소 초대 수락 | 5분 |
| 1-2 | Git 설치 | 5분 |
| 1-3 | Android Studio 설치 | 10분 |
| 1-4 | Android Studio 첫 실행 설정 (SDK 내려받기) | 15~20분 |
| 1-5 | Android Studio에 GitHub 계정 연결 | 3분 |

**준비물**: PC 여유 공간 **15GB 이상**, 인터넷. Windows·Mac 모두 됩니다.

### 1-1. GitHub 계정 만들기
1. https://github.com 에 들어가 오른쪽 위 **Sign up**을 누릅니다
2. 이메일, 비밀번호, 사용자 이름(영어)을 넣고 이메일로 온 코드로 인증합니다
3. **사용자 이름을 리드에게 알려 줍니다.** 리드가 저장소 `AD-AtoZ/balmatchum`에 초대합니다
4. 이메일로 온 초대 메일에서 **View invitation → Accept invitation**을 누릅니다
5. https://github.com/AD-AtoZ/balmatchum 이 열리면 성공입니다 (비공개 저장소라 초대를 수락해야 보입니다)

### 1-2. Git 설치
**Windows**
1. https://git-scm.com/downloads/win 에서 **Click here to download**를 눌러 설치 파일을 받습니다
2. 설치 파일을 실행하고, 나오는 화면마다 **Next**만 누릅니다 (옵션은 전부 기본값). 마지막에 **Install → Finish**
3. 확인: 시작 메뉴에서 **명령 프롬프트**를 열고 `git --version`을 입력해 `git version 2.xx`가 나오면 성공

**Mac**
1. **터미널**을 열고 `git --version`을 입력합니다
2. 설치하라는 창이 뜨면 **설치**를 누릅니다 (Xcode 명령줄 도구가 함께 설치됨). 이미 버전이 나오면 끝

### 1-3. Android Studio 설치
1. https://developer.android.com/studio 에서 **Download Android Studio**를 누르고, 약관 동의에 체크한 뒤 받습니다 (약 1GB)
   - Mac은 내 칩에 맞는 것을 받습니다: **Apple 칩**(M1~M4) 또는 **Intel 칩**. 확인: 화면 왼쪽 위 사과 메뉴 → 이 Mac에 관하여
2. **Windows**: 설치 파일 실행 → **Next** → "Android Virtual Device" 체크된 그대로 **Next** → 설치 위치 그대로 **Next** → **Install** → **Finish**
   **Mac**: 받은 `.dmg`를 열고 Android Studio 아이콘을 **Applications** 폴더로 끌어다 놓습니다
3. Java(JDK)는 Android Studio에 들어 있어서 **따로 설치하지 않습니다**

### 1-4. Android Studio 첫 실행 설정
Android Studio를 처음 켜면 설정 마법사가 나옵니다.

1. **Import Android Studio Settings** → **Do not import settings** → OK
2. 사용 통계를 Google에 보낼지 묻는 창 → **Don't send**
3. **Welcome** → Next
4. **Install Type** → **Standard** → Next
5. 테마는 아무거나 → Next
6. **Verify Settings**: 내려받을 목록이 나옵니다 → Next
7. **License Agreement**: 왼쪽 목록의 항목을 **하나씩 눌러 각각 Accept**를 고릅니다 (하나라도 빠지면 Finish가 안 눌림) → **Finish**
8. SDK를 내려받습니다 (10~20분). 끝나면 **Finish**
9. **Welcome to Android Studio** 창이 뜨면 설치 완료

> 💡 C 드라이브 공간이 부족하면 4번에서 **Custom**을 고르고, 다음 화면 맨 아래 **Android SDK Location**을 다른 드라이브(예: `D:\Android\Sdk`)로 바꾸면 됩니다. Standard에서는 위치를 바꿀 수 없습니다.

### 1-5. Android Studio에 GitHub 계정 연결
비공개 저장소라서 Android Studio가 내 GitHub 계정으로 로그인해야 코드를 받고 올릴 수 있습니다.

1. Welcome 화면 왼쪽 아래 ⚙ → **Settings** (Mac: 메뉴 **Android Studio → Settings**)
2. **Version Control → GitHub** → **+** → **Log In via GitHub…**
3. 브라우저가 열리면 GitHub에 로그인하고 **Authorize**를 누릅니다
4. 목록에 내 GitHub 아이디가 보이면 **OK**

---

## 2. 처음 실행까지 (약 30분)

### ① 저장소 받기 (Clone)
1. Welcome 화면에서 **Clone Repository**를 누릅니다 (프로젝트가 이미 열려 있으면 **File → New → Project from Version Control**)
2. **URL**에 `https://github.com/AD-AtoZ/balmatchum.git`을 붙여 넣습니다
3. **Directory**(저장 폴더)를 ⚠️ **영어로만 된 경로**로 바꿉니다
   - 좋은 예: `C:\dev\balmatchum`, `/Users/내이름/dev/balmatchum`
   - 나쁜 예: `C:\Users\홍길동\바탕 화면\발맞춤` (한글·공백이 있으면 빌드 오류가 날 수 있음)
4. **Clone** → "Trust Project?" 창이 뜨면 **Trust Project**
5. 프로젝트가 열리면 오른쪽 아래에 **Gradle sync** 진행 표시가 나옵니다. 끝날 때까지 기다립니다 (처음엔 5~10분)
6. 아래쪽 **Build** 창에 초록 체크나 `BUILD SUCCESSFUL`이 보이면 준비 완료. 빨간 오류가 나오면 맨 아래 "문제가 생겼을 때" 표를 보세요

### ② 프로젝트를 다시 열 때
- Android Studio를 켜면 Welcome 화면 목록에 **balmatchum**이 있습니다. 누르면 열립니다
- 목록에 없으면 **Open** → ①에서 정한 폴더(예: `C:\dev\balmatchum`)를 고릅니다

### ③ TMAP 키 넣기 (경로 받기·장소 검색에 필요)

팀 전체가 **리드가 만든 TMAP 앱 키 하나**를 같이 씁니다. 키는 저장소에 들어 있지 않으니, 아래 둘 중 한 방법으로 받아서 각자 PC에 넣습니다.

**키 받는 방법**

| 방법 | 하는 일 |
|---|---|
| **A. SK open API 앱 멤버로 초대받기 (권장)** | ① [openapi.sk.com](https://openapi.sk.com)에 가입하고 가입한 이메일을 리드에게 알립니다<br>② 리드가 **대시보드 → 앱 → 앱 상세 → 멤버관리 → 멤버 초대하기**로 초대합니다<br>③ 초대를 수락하고 **대시보드 → 앱 → 앱 상세 → 앱키 탭**에서 키를 복사합니다 |
| B. 리드에게 직접 받기 | 리드에게 **1:1 메신저**로 키를 받습니다 |

**키 넣기**
1. 프로젝트 맨 위 폴더의 `local.properties` 파일을 엽니다 (Android Studio 왼쪽 Project 창에서 보기를 **Project**로 바꾸면 보입니다)
2. 맨 아래에 한 줄을 추가하고 저장합니다. 앞뒤에 공백·따옴표 없이
   ```
   TMAP_APP_KEY=복사한키
   ```
3. **File → Sync Project with Gradle Files**를 누른 뒤 다시 Run 하면 앱에 키가 들어갑니다

> ⚠️ **키를 GitHub·단톡방·문서·스크린샷에 남기지 않습니다.** 저장소는 심사 때 공개되고, 키가 퍼지면 다른 사람이 팀 무료 한도(경로 하루 1,000건)를 써 버릴 수 있습니다. `local.properties`는 GitHub에 안 올라가게 이미 설정돼 있습니다.
> 가상 워치의 시연 경로 2개는 앱 안에 미리 들어 있어서 **키가 없어도 실행·가상 걷기는 됩니다.** 키는 새 목적지를 검색하거나 경로를 받을 때(휴대폰 앱) 필요합니다.

### ④ 가상 워치 만들기
1. 오른쪽 사이드바 **Device Manager** → **+** → **Create Virtual Device**
2. 왼쪽 **Wear OS** → **Wear OS Large Round** → Next
3. 시스템 이미지 **API 36 (Wear OS 6)** 옆 다운로드 아이콘 → 받은 뒤 Next → Finish
4. 만든 가상 워치 옆 ▶를 눌러 켭니다

> **가상 워치가 안 켜질 때 (Windows)**: 시작 메뉴 → "Windows 기능 켜기/끄기" → **Windows 하이퍼바이저 플랫폼** 체크 → 재부팅
> **화면이 회색으로만 보일 때** (원격 데스크톱 등): Device Manager에서 가상 워치 ✏️ → Show Advanced Settings → Graphics를 **Software**로

### ⑤ 앱 실행
1. 위쪽 툴바의 실행 대상(Run configuration)에서 **wear**를 고릅니다
2. 기기 목록에서 방금 켠 가상 워치를 고르고 **▶ Run**
3. 가상 워치에 **발맞춤** 첫 화면(내 속도, 시연 경로 2개)이 뜨면 성공입니다
4. 위치·알림 권한을 물으면 **허용**합니다

---

## 3. 걷지 않고 시험하기: 가상 걷기

디버그 빌드에서는 경로를 따라 가짜 위치를 움직여 볼 수 있습니다.

1. 첫 화면을 아래로 내려 **"가상 걷기: 끔"** 버튼을 누릅니다. 누를 때마다 끔 → 1.3m/s → 느리게 0.8m/s → 빠르게 2.0m/s로 바뀝니다
2. 경로를 고르고, 목표 시각을 **-1분**으로 몇 번 줄여 빠듯하게 만든 뒤 **출발**
3. 상태가 바뀌면 화면 색이 바뀌고, 도착하면 결과 화면이 나옵니다

안내할 때마다 기록이 CSV로 남습니다. 꺼내는 방법은 [`TODOLIST.md`](TODOLIST.md) 3절에 있습니다.

---

## 4. 판단 로직 테스트 (워치 없이 PC에서)

상태 판단은 안드로이드와 분리된 `core` 모듈에 있어서 PC에서 바로 테스트됩니다.

- **Android Studio에서**: `core/src/test` 폴더를 오른쪽 클릭 → **Run 'Tests in ...'**
- **터미널에서**
  ```
  ./gradlew :core:test          (Mac, Git Bash)
  gradlew.bat :core:test        (Windows 명령 프롬프트)
  ```

---

## 5. 실제 갤럭시 워치에 설치 (워치가 있을 때)

갤럭시 워치는 USB 데이터 연결이 안 돼서 **Wi-Fi 무선 디버깅**으로 연결합니다. PC와 워치가 **같은 Wi-Fi(공유기)**에 있어야 합니다.

1. 워치: **설정 → 워치 정보 → 소프트웨어 정보 → 소프트웨어 버전**을 7번 연달아 누르면 개발자 옵션이 켜집니다
2. 워치: **설정 → 개발자 옵션 → ADB 디버깅**, **무선 디버깅** 켜기
3. Android Studio: 기기 목록 → **Pair Devices Using Wi-Fi** → **Pair using pairing code**
4. 워치: 무선 디버깅 → **새 기기 페어링** → 나온 6자리 코드를 Android Studio에 입력
5. 기기 목록에 워치가 뜨면 고르고 **▶ Run**

진동은 가상 워치에서는 느낄 수 없어서 **실제 워치로 확인해야** 합니다.

---

## 6. 프로젝트 구조

| 모듈 | 내용 |
|---|---|
| `core/` | 판단 로직 (순수 Kotlin). 내 속도, 상태 판단, 남은 거리, TMAP 경로 읽기·호출 |
| `wear/` | 워치 앱. 화면(`presentation/screens/`), 안내 서비스, 위치, 진동, CSV 기록 |
| `mobile/` | 휴대폰 앱. 목적지를 검색해 워치로 보내기 (만드는 중) |

파일 단위 설명은 [`TODOLIST.md`](TODOLIST.md) 3절을 보세요.

**사용 기술**: Kotlin, Jetpack Compose for Wear OS, Fused Location Provider, Wearable Data Layer, TMAP Open API(보행자 경로·장소 검색)

---

## 7. 함께 작업하는 규칙

> ⚠️ **커밋(commit)과 푸시(push)는 반드시 본인이 직접 합니다.** AI 도구에게 코드 도움은 받아도 되지만, 커밋·푸시를 시키거나 자동으로 하게 두지 않습니다.

1. 할 일은 [`TODOLIST.md`](TODOLIST.md)에서 골라 **"담당" 칸에 이름**을 먼저 적습니다
2. 브랜치를 따서 작업합니다: `T5-progress-screen`처럼 `할일번호-짧은이름`
3. 커밋 전에 Commit 창에서 **바뀐 파일과 내용을 직접 확인**합니다. `local.properties`가 보이면 절대 올리지 않습니다
4. 끝나면 GitHub에서 **Pull Request**를 열고 리드에게 리뷰를 부탁합니다. `main`에 바로 올리지 않습니다
5. 막히면 30분 이상 혼자 붙잡지 말고 리드에게 물어보세요

### Android Studio에서 브랜치·커밋·푸시하기
다른 프로그램 없이 Android Studio 안에서 다 됩니다.

| 하는 일 | 방법 |
|---|---|
| 최신 코드 받기 | **Git → Pull** (작업 시작 전에 한 번) |
| 내 브랜치 만들기 | 오른쪽 아래 브랜치 이름(`main`) 클릭 → **New Branch** → `T5-progress-screen`처럼 입력 |
| 커밋 | 왼쪽 **Commit** 탭 또는 **Ctrl + K** → 파일 목록·변경 내용 확인 → 메시지 쓰고 **Commit** |
| 푸시 | **Git → Push** 또는 **Ctrl + Shift + K** → 처음엔 GitHub 로그인 창이 뜨면 브라우저로 로그인 |
| Pull Request 열기 | 푸시 후 GitHub 저장소 페이지에 뜨는 **Compare & pull request** 버튼 |

- 첫 커밋 때 이름·이메일을 물으면 GitHub 계정과 같은 것으로 넣습니다
- 커밋 메시지는 무엇을 했는지 한 줄로: 예) `T5 진행 화면 상태별 색과 남은 시간 표시`

---

## 문제가 생겼을 때

| 증상 | 해결 |
|---|---|
| Gradle sync가 실패한다 | 저장 경로에 한글·공백이 있는지 확인. **File → Sync Project with Gradle Files** 다시 실행 |
| `TMAP_APP_KEY`가 없다고 나온다 | `local.properties`에 키 줄이 있는지, 앞뒤에 공백·따옴표가 없는지 확인 |
| 가상 워치가 켜지지 않는다 | 2-④의 "Windows 하이퍼바이저 플랫폼" 확인 |
| 가상 워치 화면이 회색이다 | 2-④의 Graphics를 Software로 |
| 실행 대상에 wear가 없다 | Gradle sync가 끝났는지 확인 |
| 실제 워치가 기기 목록에 안 뜬다 | PC와 워치가 같은 Wi-Fi인지 확인. 학교 Wi-Fi는 기기끼리 연결을 막을 수 있어 휴대폰 핫스팟을 써 봅니다 |

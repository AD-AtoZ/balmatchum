# 워치 도보 도착 코치 앱 기획 (발맞춤 원본 기획서)

> **이 문서를 읽기 전에 (2026-10-28 추가)**
> - 발맞춤의 **처음 기획(1~10절, 9/17)**과 **일주일 구현 계획(11절, 10/27)**을 담은 원본 기획서입니다. 왜 이렇게 설계했는지(속도 계산, 경로 이탈, 네트워크, 플랫폼 선택의 근거)를 볼 때 읽습니다.
> - 이후에 바뀐 결정이 더 우선합니다. 지금 기준은 저장소의 [`README.md`](../README.md)(설치·실행)와 [`TODOLIST.md`](../TODOLIST.md)(할 일·일정)입니다. 주요 변경:
>   - 상태 판단은 **내가 미리 잰 속도**(보통 걸음·빠른 걸음·뛰기) 기준 → `core/.../ArrivalJudge.kt`, `PersonalSpeeds.kt`
>   - 목적지는 **휴대폰에서 검색**해 워치로 보냄. 시연은 앱에 넣어 둔 경로 2개로 함
>   - TMAP 키는 팀 공용 키가 `secrets.defaults.properties`에 들어 있어 따로 넣지 않음 (11-4의 `local.properties` 안내는 예전 방식)
>   - 위치는 Fused Location(GPS·Wi-Fi·기지국) 사용
>   - 일정·마감은 11-6이 아니라 TODOLIST 기준 (서류 제출 11/5, 포스터 발표 11/20)
> - 본문에 나오는 `HANDOFF`, `페르소나.pptx`, `고객여정지도_종합_1-4단계.docx` 같은 파일은 이 저장소에 없고 과제 자료 폴더에 있습니다.

> 차량 내비게이션을 사람에게 적용하되, 안내와 판단을 워치에서 하는 앱.
> 휴대폰으로 목적지와 도착 목표 시각을 정하면, 워치가 걷는 속도를 실시간으로 보고 제시간 도착이 어려우면 뛰라고 알려준다.

---

## 1. 핵심 개념

- 휴대폰에서 목적지와 도착 목표 시각을 설정한다.
- 워치가 경로를 받아오고, 이동 중 위치와 속도를 추적한다.
- 남은 거리와 남은 시간으로 제시간 도착 여부를 계속 판단한다.
- 늦을 것 같으면 진동과 화면으로 "뛰세요"를 알린다.
- 경로를 벗어나면 내비게이션처럼 경로를 다시 탐색하고 도착 판단을 다시 한다.

---

## 2. 기기별 역할

| 기기 | 맡는 일 |
|---|---|
| 휴대폰 | 목적지 검색, 도착 목표 시각 설정, 워치로 전송, 워치의 인터넷 회선 제공, 잠금화면 진행 상황 표시 |
| 워치 | 경로 요청, 위치·속도 추적, 도착 판단, 경로 이탈 감지, 재탐색, 진동·화면 피드백 |

- 경로 요청과 재탐색은 **워치 앱이 직접** 보낸다.
- 요청이 나가는 **인터넷 회선은 휴대폰을 빌려 쓴다.** 블루투스로 연결된 휴대폰을 경유한다.

---

## 3. 사용할 API

| 용도 | API | 비고 |
|---|---|---|
| 목적지 장소 검색 | 카카오 또는 네이버 장소 검색 | 휴대폰 앱에서 사용 |
| 도보 경로·거리·소요시간 | TMAP 보행자 경로안내 API | `POST https://apis.openapi.sk.com/tmap/routes/pedestrian`, 응답에 총거리와 초 단위 총소요시간 포함 |

- 네이버·카카오의 공개 길찾기 API는 자동차 경로 중심으로 알고 있어, 도보 경로는 TMAP을 쓴다. 개발 전 각 API 약관과 무료 호출량을 다시 확인한다.

---

## 4. 전체 흐름

1. **휴대폰에서 설정**: 장소 검색으로 목적지를 고르고 도착 목표 시각을 정해 워치로 보낸다. 워치는 목적지 좌표와 목표 시각을 저장한다.
2. **워치에서 출발**: 출발 버튼을 누르면 운동 세션이 시작돼 화면이 꺼져도 추적이 계속된다.
3. **첫 경로 요청**: 워치가 현재 GPS 위치를 출발지로 TMAP 보행자 경로를 요청한다. 경로 좌표, 총거리, 예상 소요시간을 저장한다.
4. **이동 중 추적**: 위치와 속도를 받아 남은 거리와 도착 가능 여부를 계속 계산하고 상태에 맞춰 피드백한다.
5. **경로 이탈 시**: 이탈이 확정되면 현재 위치에서 목적지까지 다시 요청하고, 같은 목표 시각으로 판단을 다시 한다.
6. **도착**: 목적지 반경 안에 들어오면 세션을 끝내고 제시간 도착 여부를 보여준다.

---

## 5. 워치의 판단 로직

### 5-1. 현재 속도

다른 앱들도 정확한 계산식은 공개하지 않는다. 공개 자료로 확인되는 공통점은 다음과 같다.

| 앱·플랫폼 | 확인된 방식 |
|---|---|
| Garmin | GPS 속도와 가속도계를 합쳐 계산, GPS가 부정확하면 가속도계 값이 우선. 순간 페이스는 최근 3초 정도 평균이라는 사용자 포럼 설명이 있으나 공식 발표는 아님 |
| Apple Watch | GPS가 잡히면 GPS로, 약하면 가속도계와 학습된 보폭으로 계산. 야외 걷기·달리기를 할수록 속도별 보폭을 학습 |
| Strava | 달리기 중 움직임과 멈춤을 가속도계로 판단. 평활화 방식은 비공개 |
| Wear OS | Health Services API가 걷기·달리기 운동 중 속도와 페이스를 센서 융합으로 계산해 제공 |

**우리 앱의 방식**
- GPS 좌표 차이로 속도를 직접 계산하지 않고, **플랫폼이 제공하는 융합 속도**를 받는다.
  - 애플워치: 운동 세션의 실시간 속도. 달리기 속도 값은 SE·Series 6 이후 기기의 야외 운동에서만 제공된다.
  - 갤럭시 워치: Health Services의 속도.
- **화면 표시용 속도**는 몇 초 단위로 짧게 평활화한다.
- **뛰라는 판단에는 멈춘 시간을 뺀 이동 속도**를 쓴다. 신호등에서 잠깐 멈췄다고 경고가 울리지 않게 하기 위함이다. 멈춰서 잃은 시간은 남은 시간이 줄어드는 것으로 이미 반영된다.

### 5-2. 남은 거리

- 현재 위치를 **경로 선 위에 투영**해서 경로를 따라 남은 거리를 구한다.
- 직선거리로 계산하면 골목이 꺾일 때 크게 틀린다.

### 5-3. 도착 상태 판단

- 예상 소요시간 = 남은 거리 ÷ 이동 속도
- 예상 소요시간과 남은 시간을 비교해 상태를 나눈다.

| 상태 | 조건 | 워치 피드백 |
|---|---|---|
| 여유 | 지금 속도로 충분히 제시간 도착 | 초록 화면, 알림 없음 |
| 주의 | 지금 속도로 겨우 맞춤 | 노란 화면, 짧은 진동 |
| 뛰세요 | 지금 속도로는 늦음 | 빨간 화면, 강한 반복 진동 |
| 불가능 | 뛰어도 닿기 어려움 | 예상 지각 시간 표시 |

- 같은 상태가 몇 초 이상 이어질 때만 알림을 바꿔 오락가락하지 않게 한다.

### 5-4. 경로 이탈 감지와 재탐색

아래 숫자는 표준값이 아니라 도보 GPS 오차를 고려한 **시작값**이며, 실제로 걸어보며 조정한다.

1. **이탈 후보**: 현재 위치에서 경로 선까지의 최단거리가 기준값을 넘으면 후보로 본다. 기준값은 `max(30m, GPS 정확도 × 2)`로 시작한다. 도보 GPS 오차는 차량보다 불규칙하다는 연구가 있어 고정값보다 정확도에 맞춰 늘린다.
2. **이탈 확정**: 이탈 상태가 약 10초 연속으로 이어지고, 경로상 진행 거리가 늘지 않을 때만 확정한다.
3. **재탐색 요청**: 현재 위치를 출발지, 원래 목적지를 도착지로 다시 요청한다.
4. **판단 재계산**: 새 경로의 남은 거리로 다시 판단한다. 도착 목표 시각은 유지한다.
5. **알림**: "경로 재탐색" 진동을 한 번 주고, 늦을 상황이면 곧바로 "뛰세요" 상태로 넘어간다.
6. **반복 방지**: 재탐색 후 약 30초는 다시 재탐색하지 않는다. 원래 경로로 돌아오면 이탈 후보를 취소한다.
7. **공백 방지**: 재탐색 응답을 기다리는 동안에도 기존 경로로 도착 판단을 계속한다.

---

## 6. 네트워크 모델: 휴대폰 회선 빌려쓰기

### 6-1. 선택 근거

워치 단독 인터넷 사용자는 소수로 추정된다. 미개통 비율을 직접 조사한 통계는 없어 공개 수치를 맞대어 본 추정이다.

| 항목 | 수치 | 출처 |
|---|---|---|
| 국내 성인 스마트워치 사용률 | 33% | 한국갤럽, 2025년 7월 |
| 사용률로 환산한 성인 사용자 수 | 약 1,450만 명 | 성인 인구 약 4,400만 명 기준 추정 |
| 국내 웨어러블 이동통신 회선 | 약 202만 개 | 과기정통부 통계, 2026년 1분기 |
| 개통 회선이 사용자 중 차지하는 비율 | 최대 약 14% | 위 두 수치로 계산한 상한 |

- 웨어러블 회선에는 키즈워치도 포함돼 실제 비율은 더 낮을 가능성이 크다.
- 세계 출하량 기준 셀룰러 모델 채택률 33%는 판매 비율이지 개통 비율이 아니다.
- LTE 모델을 사고도 개통하지 않는 경우가 흔하다. 개통하지 않아도 블루투스 연결로 기능을 다 쓸 수 있다.

### 6-2. 워치의 인터넷 경로

워치 앱이 요청을 보내면 회선은 아래 순서로 잡힌다. 애플워치와 갤럭시 워치 모두 같다.

1. 블루투스로 연결된 휴대폰의 인터넷
2. 휴대폰이 없으면 전에 접속한 Wi-Fi
3. 셀룰러 개통 워치라면 워치 자체 LTE

### 6-3. 상황별 동작

| 휴대폰 상태 | 워치의 경로 요청 | 워치 동작 |
|---|---|---|
| 화면 켜짐, 가까이 있음 | 가능 | 정상 동작 |
| **화면 꺼짐·잠금, 가까이 있음** | **가능** | **정상 동작. 이 앱의 기본 사용 상황** |
| 멀어져 블루투스 끊김 | 불가능 | 기존 경로로 판단 유지, 재탐색 불가 표시 |
| 전원 꺼짐 | 불가능 | 위와 같음 |
| 셀룰러 개통 워치 | 가능 | 정상 동작, 부가 기능으로 취급 |

- (10-27) 갤럭시 워치는 휴대폰 앱이 포그라운드일 때만 블루투스 인터넷이 된다는 보고가 있어 위 표의 "가능"은 미확인이다. §11-3 참고
- 위치와 속도는 워치 GPS로 측정하므로 휴대폰이 없어도 도착 판단은 끊기지 않는다.
- 출발 전 첫 경로를 받지 못하면 추적을 시작할 수 없으므로 휴대폰 연결을 확인하라고 안내한다.
- 로그인 화면이 뜨는 공용 Wi-Fi는 워치에서 쓸 수 없는 경우가 많다.

### 6-4. 우회로: 휴대폰 앱이 대신 요청

두 플랫폼 모두 워치의 인터넷 중계가 불안정했다는 개발자 보고가 있어 우회로를 둔다.

- 워치의 직접 요청이 실패하면, 워치-휴대폰 앱 통신 채널로 "경로 받아줘" 메시지를 보낸다.
- 휴대폰 앱이 대신 API를 호출해 결과를 워치로 돌려준다.

| 플랫폼 | 통신 채널 | 잠금 상태에서의 동작 |
|---|---|---|
| 애플워치 | WatchConnectivity | 워치 앱이 실행 중일 때 메시지를 보내면 iOS가 잠긴 아이폰의 앱을 백그라운드에서 깨워 받게 함 |
| 갤럭시 워치 | Wearable Data Layer API | 메시지 수신 서비스를 등록하면 앱이 꺼져 있어도 받음. 절전 설정이 앱을 제한하면 늦거나 끊길 수 있어 배터리 최적화 제외 안내 필요 |

---

## 7. 휴대폰 잠금화면 표시

잠금화면 위젯은 연결을 살려주는 장치가 아니라 **휴대폰에서 진행 상황을 보여주는 화면**이다. 워치 기능에 필수는 아니다.

| 플랫폼 | 형태 | 효과 |
|---|---|---|
| 아이폰 | 라이브 액티비티 | 잠금화면에 남은 시간과 상태 표시. 앱을 백그라운드에 계속 살려두지는 않음 |
| 갤럭시 | 진행 중 알림을 띄우는 포그라운드 서비스 | 잠금화면에 상태 표시. 휴대폰 앱이 절전으로 종료되지 않게 붙잡아 우회로 안정성도 올라감 |

**추천 구성**
- 워치 사용 자체는 위젯 없이 동작하게 설계한다.
- 갤럭시: 안내 중 진행 중 알림을 띄워 휴대폰 앱을 살려둔다.
- 아이폰: 라이브 액티비티를 부가 기능으로 넣는다.

---

## 8. 플랫폼별 개발 환경

| 항목 | 애플워치 | 갤럭시 워치 |
|---|---|---|
| 워치 OS | watchOS | Wear OS, One UI Watch |
| 개발 컴퓨터 | Mac 필수 | Windows·macOS·Linux 모두 가능 |
| 개발 도구 | Xcode | Android Studio, Wear OS SDK |
| 언어·UI | Swift, SwiftUI | Kotlin, Jetpack Compose for Wear OS |
| 운동 세션·속도 | HealthKit 운동 세션, 실시간 운동 빌더 | Health Services ExerciseClient |
| 워치-휴대폰 통신 | WatchConnectivity | Wearable Data Layer API |
| 워치 네트워크 | URLSession | HTTP 클라이언트 |
| 휴대폰 잠금화면 | 라이브 액티비티 | 포그라운드 서비스 알림 |
| 테스트 | Watch 시뮬레이터, 실기기는 페어링된 아이폰 필요 | Wear OS 에뮬레이터, 실기기는 Wi-Fi ADB 디버깅 |
| 배포 | Apple Developer Program 연 99달러 | Google Play Console 최초 25달러 |

- 두 플랫폼은 코드를 거의 공유하지 못해 하나를 먼저 정해야 한다.
- Galaxy Watch 3 이전 모델은 Tizen 기반이라 대상에서 제외한다.

---

## 9. 아직 정해야 할 것

- [ ] 애플워치와 갤럭시 워치 중 어느 쪽으로 먼저 만들지
- [ ] API 키를 앱에 그대로 둘지, 키를 숨기는 중계 서버를 둘지
  - 워치 앱이든 휴대폰 앱이든 앱을 뜯으면 키가 보일 수 있다. 과제·개인 프로젝트면 그대로, 배포까지 하면 중계 서버가 안전하다.
- [ ] TMAP 무료 호출량 안에서 재탐색 빈도를 얼마나 허용할지
- [ ] 판단 로직 시작값 조정: 이탈 기준 거리, 이탈 확정 시간, 재탐색 쿨다운, 상태 전환 유지 시간, 주의·뛰세요 경계값

---

## 10. 실기기 테스트 초반 확인 항목

- [ ] 새로 설치한 워치 앱이 휴대폰 경유로 네트워크를 쓰는지. 애플 포럼에 아이폰 재부팅 전까지 안 됐다는 보고가 있음
- [ ] 갤럭시 워치가 블루투스 연결 상태에서 인터넷 요청이 되는지. 휴대폰 앱이 백그라운드일 때도 되는지
- [ ] 휴대폰 잠금 상태에서 워치 직접 요청과 우회로 요청이 모두 되는지
- [ ] 화면이 꺼진 워치에서 운동 세션이 GPS 추적을 계속하는지
- [ ] 건물 사이, 지하도 등 GPS 약한 구간에서 속도와 이탈 감지가 튀지 않는지
- [ ] 신호등 대기 중 "뛰세요" 오경보가 나지 않는지

---

## 참고 자료

**속도 계산**
- [Instant pace/speed: Running vs Cycling - Garmin Forums](https://forums.garmin.com/sports-fitness/sports-fitness/f/forerunner-935/143598/instant-pace-speed-running-vs-cycling)
- [Differences in measured pace and report pace - Garmin Forums](https://forums.garmin.com/sports-fitness/running-multisport/f/forerunner-945/423000/differences-in-measured-pace-and-report-pace)
- [Calibrate your Apple Watch for improved Workout and Activity accuracy - Apple Support](https://support.apple.com/en-us/105048)
- [Moving Time, Speed, and Pace Calculations - Strava Help Center](https://support.strava.com/en-us/articles/15401804-moving-time-speed-and-pace-calculations)
- [Health Services on Wear OS - Android Developers](https://developer.android.com/health-and-fitness/health-services)
- [Using Health Services on Galaxy Watch - Samsung Developer](https://developer.samsung.com/health/blog/en/2022/01/14/using-health-services-on-galaxy-watch)
- [HKLiveWorkoutBuilder - Apple Developer Documentation](https://developer.apple.com/documentation/healthkit/hkliveworkoutbuilder)
- [runningSpeed availability - Apple Developer Forums](https://developer.apple.com/forums/thread/709412)

**경로·이탈 감지**
- [보행자 경로안내 - TMAP Open API](https://tmap-skopenapi.readme.io/reference/%EB%B3%B4%ED%96%89%EC%9E%90-%EA%B2%BD%EB%A1%9C%EC%95%88%EB%82%B4)
- [TMAP Open API를 활용해 도보 경로 안내 api 구현하기](https://injae7034.github.io/kotlin/tmap-open-api-pedestrian/)
- [An Improved Map-Matching Technique for Pedestrian Navigation Services](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5087552/)

**워치 네트워크**
- [Making Network Requests on Apple Watch - O'Reilly](https://www.oreilly.com/library/view/developing-for-apple/9781680501940/f_0055.xhtml)
- [WatchOS URLSession does not work without paired iPhone? - Apple Developer Forums](https://developer.apple.com/forums/thread/107964)
- [Watch-Only and Independent apps have no network connection when paired with an iPhone - Apple Developer Forums](https://developer.apple.com/forums/thread/749431)
- [Communicate directly over a network on standalone devices - Android Developers](https://developer.android.com/training/wearables/data/network-communication)
- [Can't establish a network connection when Watch is tethered to a Phone over Bluetooth - Samsung Developer Forum](https://forum.developer.samsung.com/t/cant-establish-a-network-connection-when-watch-is-tethered-to-a-phone-over-bluetooth/10269)
- [Watch can access internet over bluetooth only when Wear OS app on the phone is in foreground - Wear OS Community](https://support.google.com/wearos/thread/136137806/watch-can-access-internet-over-bluetooth-only-when-wear-os-app-on-the-phone-is-in-foreground?hl=en)

**스마트워치 사용 현황**
- [한국갤럽 스마트폰 관련 조사 2012-2025](https://x.com/GallupKoreaNews/status/1942146823474626880)
- [웨어러블 회선 200만 시대…셀룰러 스마트워치가 성장 견인](https://v.daum.net/v/6cxUY871rl?f=p)
- [2026년 1분기 글로벌 스마트워치 시장 - 카운터포인트리서치](https://korea.counterpointresearch.com/global-smartwatch-market-q1-2026/)
- [워치류 lte 요금제 안쓰면 먹통인가요? - Samsung Members](https://r1.community.samsung.com/t5/%EC%9B%A8%EC%96%B4%EB%9F%AC%EB%B8%94-%EC%9B%8C%EC%B9%98-%EB%B2%84%EC%A6%88-%EA%B8%B0%ED%83%80/%EC%9B%8C%EC%B9%98%EB%A5%98-lte-%EC%9A%94%EA%B8%88%EC%A0%9C-%EC%95%88%EC%93%B0%EB%A9%B4-%EB%A8%B9%ED%86%B5%EC%9D%B8%EA%B0%80%EC%9A%94/td-p/18345492)

---

## 11. 일주일 구현 계획 (2026-10-27, Windows · 갤럭시 워치)

> 1~10절은 처음 기획이다. **이 절이 지금 실행할 계획이고, 충돌하면 이 절이 우선한다.**

### 11-1. 전제 (사용자 결정)

| 항목 | 내용 |
|---|---|
| 플랫폼 | **갤럭시 워치(Wear OS)**. 팀에 애플워치·아이폰이 없음 |
| 결과물 | **다음 주까지 포스터 + 발표**, 그리고 실제로 걸으며 동작하는 시연 |
| 범위 | "기능적으로는 단순하지만 고충을 꿰뚫는 것" |
| 개발 PC | **Windows 데스크톱** |

### 11-2. 만들 것: 기능 하나에 집중

**핵심 기능 (반드시)**: 손목 진동만으로 "지금 속도로 제때 도착하는지" 알려준다.

- 해결하는 고충
  - 여정지도 3단계: "이 속도로 걸어가면 되나?" / 현재 속도로 제시간 도착 가능한지 실시간 반영 안 됨
  - 여정지도 2·4단계: 두 손이 묶여 휴대폰을 못 꺼냄
  - HMW: 어떻게 하면 화면을 보지 않고도 이동 상태를 알 수 있을까?
- 동작
  1. 워치에서 목적지(교내 건물 목록)와 도착 목표 시각을 고른다
  2. 출발을 누르면 워치가 경로를 불러오고, 걷는 동안 위치와 속도를 추적한다
  3. 상태가 바뀔 때만 진동한다. 상태 이름은 개요서 2번을 따른다
- 진동 패턴과 상태 경계값은 시작값이다(제안, 미검증). 오즈의 마법사 테스트와 실측으로 고친다

| 상태 | 조건 (시작값) | 진동 (시작값) | 화면 |
|---|---|---|---|
| 여유 있음 | 여유시간 ≥ 60초 | 없음 | 초록 |
| 서두르면 맞춤 | 0 ≤ 여유시간 < 60초 | 짧게 1번 | 노랑 |
| 뛰어야 맞춤 | 여유시간 < 0, 뛰는 속도(2.5m/s 가정)면 도착 | 짧게 3번 | 빨강 |
| 뛰어도 어려움 | 뛰어도 늦음 | 길게 1번 | 회색 + 예상 지각 분 |

- 여유시간 = 남은 시간 − (경로를 따라 남은 거리 ÷ 멈춘 시간을 뺀 이동 속도)
- 같은 상태가 5초 이상 이어질 때만 바꾼다(오락가락 방지)

- 출발 직후 이동 속도가 0이거나 없으면 TMAP 총거리 ÷ 총시간(없으면 1.3m/s, 시작값)을 초기 속도로 쓴다

**있으면 좋은 것 (시간 남으면)**: 경로 이탈 진동(길게 2번). "진동이 없으면 길이 맞다"는 뜻이 되어 여정지도 4단계 "길이 여기가 아닌거 같은데?"에 답한다. 재탐색까지는 하지 않아도 된다. 감정이 가장 낮은 곳은 4단계지만, 이탈 판단은 교내 GPS 오차(4단계 "내 위치가 왜 여기로 잡히지?") 때문에 일주일 안에 오경보 없이 맞추기 어려워 후순위로 둔다

**이번에 하지 않는 것**: 휴대폰 앱, 장소 검색, 재탐색, 갈림길 방향 진동, 개인 속도 학습, 신호 반영, 잠금화면 표시.

### 11-3. 네트워크 위험과 대비

- 갤럭시 워치는 휴대폰의 Wear OS 앱이 앞에 떠 있을 때만 블루투스로 인터넷이 된다는 개발자 보고가 있다(참고자료 "Watch can access internet over bluetooth only when...")
- 대비: **시연 경로 2~3개는 TMAP으로 미리 받아 앱 안(assets)에 JSON으로 넣는다.** 인터넷이 되면 실시간으로 받고, 안 되면 넣어 둔 경로를 쓴다. 시연이 네트워크에 좌우되지 않게 한다

### 11-4. Windows 개발 환경 준비

1. **Android Studio** 최신 안정판 설치(JDK 포함)
2. SDK Manager에서 Android SDK Platform(최신) 설치. Wear OS 3 이상 = 갤럭시 워치4 이후. minSdk는 30으로 시작(확인 필요)
3. (선택) **Wear OS 에뮬레이터**: Windows 기능에서 "Windows 하이퍼바이저 플랫폼"을 켜야 할 수 있음. 에뮬레이터 확장 컨트롤의 위치 → 경로(GPX/KML) 재생으로 걷기를 흉내 낼 수 있어 실외에 나가지 않고 판단 로직을 시험할 수 있다
4. **워치 실기기 연결 (무선 디버깅)**
   - 갤럭시 워치는 USB 데이터 연결이 안 되어 무선 디버깅만 된다
   - 워치: 설정 → 디바이스 정보 → 소프트웨어 정보 → 소프트웨어 버전 여러 번 탭(보통 7번) → 개발자 옵션 → ADB 디버깅 + 무선 디버깅 켜기 → "새 기기 페어링"에 나오는 IP:포트와 코드 확인. 메뉴 이름과 탭 횟수는 One UI Watch 버전에 따라 다를 수 있다(확인 필요)
   - PC: `adb pair <페어링 화면의 IP:포트>` 후 코드 입력 → `adb connect <무선 디버깅 화면의 IP:포트>`(두 포트는 다르다) → `adb devices`로 확인. adb는 PATH에 없을 수 있으니 `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`로 부른다
   - PC와 워치는 같은 네트워크여야 한다. 학교 Wi-Fi는 기기 간 통신이 막혀 있을 수 있으니 안 되면 휴대폰 핫스팟에 둘 다 연결(확인 필요). **데스크톱에 Wi-Fi가 없으면** 워치가 붙는 공유기에 데스크톱을 유선으로 연결해야 한다
   - Windows 방화벽 허용 창이 뜨면 허용
5. **TMAP 앱 키**: 사용자가 직접 발급(발급처 확인 필요: SK Open API openapi.sk.com 또는 TMAP 모빌리티 API 포털). 키는 프로젝트의 `local.properties`에 `TMAP_APP_KEY=...`로 두고 빌드 설정에서 BuildConfig로 읽는다(AGP 8부터 `buildFeatures { buildConfig = true }` 필요). 채팅·문서·zip에 넣지 않는다
6. **시연 경로 받기(2일차)**: `local.properties`에서 키를 읽어 TMAP을 호출하는 작은 스크립트로 받고, 키를 화면에 출력하지 않는다. PowerShell 5.1의 `curl`은 별칭이라 `curl.exe`를 쓴다. 교내 건물 좌표는 지도 앱에서 직접 찍어 정한다

### 11-5. 프로젝트 구조 (제안)

Android Studio → New Project → Wear OS → Empty Wear App(Compose). 패키지 예: `kr.ac.cnu.balmatchum`

| 위치 | 내용 |
|---|---|
| `core/` (순수 Kotlin) | 경로 투영(남은 거리), 이동 속도(멈춘 시간 제외), 상태 판단(4상태 + 5초 유지), 이탈 판단. **안드로이드 의존 없이 `src/test`에서 JUnit으로 시험** |
| `data/TmapClient` | `POST https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1`, 헤더 `appKey`, 본문 출발·도착 좌표와 이름. 응답 GeoJSON의 LineString 좌표와 총거리·총시간 사용 |
| `assets/routes/*.json` | 시연 경로 미리 받은 것 |
| `sensing/` | Health Services `ExerciseClient`(걷기 운동)로 위치·속도 수신, 포그라운드 서비스로 화면이 꺼져도 유지. 필요한 권한 목록은 Health Services 문서로 확인(위치, 활동 인식, 포그라운드 서비스, 진동, 인터넷 등) |
| `haptics/` | `Vibrator` 파형 진동으로 상태별 패턴 |
| `ui/` | 목적지 선택 → 목표 시각 → 진행 화면(상태 색, 남은 시간) → 도착 결과 |
| 기록 | 이동 중 시각·위치·속도·상태를 CSV로 저장 → 지표 측정과 포스터 근거에 사용 |

### 11-6. 일정 (1일차 = 작업 시작일. 10/27에 작성, 마감 "다음 주"의 정확한 날짜는 미확인. 10/29 여정지도 발표도 있음)

| 날 | 개발 | 포스터·발표 |
|---|---|---|
| 1일차 | Windows 환경 설치, 빈 Wear 앱을 워치에 설치해 화면 띄우기, TMAP 키 신청 | 포스터 규격·제출 방식 확인 |
| 2일차 | `core` 판단 로직 + 단위 테스트, TMAP으로 교내 시연 경로 2~3개 받아 assets에 저장(교내 보행로가 나오는지 확인) | **오즈의 마법사 테스트**: 팀원이 뒤따라가며 수동 진동으로 패턴 구분 확인. 진동 보내는 방법은 미정(예: 메신저 알림 진동, 버튼 하나짜리 임시 앱) |
| 3일차 | 운동 세션 위치·속도 + 화면 꺼짐 유지 + CSV 기록 | 포스터 초안(아래 구성) |
| 4일차 | 상태 판단 → 진동 → 진행 화면 연결. 걸으면 상태가 바뀔 때 진동 | |
| 5일차 | 교내 실제 경로 시험, 기준값 조정. 시간 남으면 이탈 진동 | 시연 사진·영상 촬영 |
| 6일차 | 버그 수정, 시연 리허설 | 포스터 완성, 결과 수치 반영 |
| 7일차 | 예비일 | 발표 준비 |

### 11-7. 포스터 구성 (제안)

문제(면담 근거 숫자) → 페르소나 김도윤 → 고객여정지도(종합본) → HMW → 해결: 손목 진동 4상태 → 시연 사진·QR(영상) → 테스트 결과(개요서 7번 지표 중 측정한 것) → 기대효과

- 면담 숫자는 8명을 모두 세서 쓴다(HANDOFF §0 1인별 집계)
- 측정하지 못한 지표는 "목표"라고 표시한다
- 페르소나는 애플워치 사용자인데 시연은 갤럭시 워치다. 포스터에 "시연은 갤럭시 워치로 구현(애플워치는 미구현)"처럼 한 줄 밝힌다(제안)

### 11-8. 역할 (예시)

| 담당 | 맡을 일 |
|---|---|
| 워치 주인 | 워치 연결, 운동 세션·진동, 실기기 시험 |
| 로직 | `core` 판단 로직과 테스트, TMAP 경로 |
| 테스트·포스터 | 오즈의 마법사 테스트, 실측 기록, 포스터·발표 자료 |

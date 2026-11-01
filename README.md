BlockLocker
===========

베리 이름 연동 업데이트
----------------------

gamparda 포크의 기존 신뢰 목록·소유권 이전·설정 GUI·한국어 문구·우회 목록·보호 수정 사항을 유지하면서
원본 `25b6224`(1.15.1-SNAPSHOT)까지 병합했습니다. 블록 태그 설정 지원도 포함됩니다.
배포 버전은 `1.15.1-berry.1`이며 Paper 26.3 build 201 / Java 25 기준입니다.

[BerryIntegrations 연동 안내](integrations/README.md)를 따라 별도 애드온을 설치하면 BerryNames 1.3.0 이상의
이름을 표지판·trust/untrust/transfer/bypass 명령·자동완성과 BlueMap 5.29의 플레이어 표시에서 사용할 수 있습니다.
BlockLocker 단독 사용도 가능합니다. 업데이트할 때 기존 설정 파일과 월드 데이터는 유지하세요.

표지판으로 Minecraft의 개별 블록(상자 등)을 잠그는 플러그인입니다.

주요 기능
--------

* `[개인]` / `[Private]`, `[추가 사용자]` / `[More Users]` 표지판으로 상자, 호퍼, 화로, 통, 문, 다락문, 울타리문 등 보호 가능한 블록을 보호합니다.
* 보호 가능한 블록에 표지판을 붙이면 `[개인]`과 플레이어 이름이 자동으로 입력됩니다.
* 보호 소유자만 보호를 파괴할 수 있습니다.
* 관리자는 보호된 블록을 열거나 사용할 수 있습니다.
* UUID 지원
  * UUID는 표지판의 숨겨진 데이터에 저장되며 일반 유저에게 보이지 않습니다.
  * Lockette/Deadbolt 표지판을 읽을 때 UUID를 자동 조회합니다.
* 설정 가능
  * 메시지는 한국어로 번역되어 있으며, `translations-kr.yml`을 수정해 커스터마이징할 수 있습니다.
  * 보호 가능한 블록 종류를 변경할 수 있습니다.
* 그룹 지원: 표지판에 `[MyGroup]`을 추가하면 `blocklocker.group.mygroup` 권한, 스코어보드 팀, MassiveCraft Factions의 같은 이름 팩션 유저가 접근할 수 있습니다.
* 복잡한 블록 처리
  * 양문 지원: 한쪽을 보호하면 반대쪽도 보호되고, 한쪽을 열면 반대쪽도 함께 열립니다.
  * 문 자동 닫힘 설정 지원
  * 큰 상자 지원: 한쪽을 보호하면 다른 한쪽도 보호됩니다.
  * 큰 상자의 설정은 두 블록 모두에 함께 저장됩니다.
  * 문/다락문/울타리문처럼 데이터를 직접 저장할 수 없는 블록은 보호 표지판에 설정을 저장합니다.
  * 트랩도어/울타리 문도 본체 또는 지지 블록에 표지판을 붙일 수 있습니다.
* 보호 소유자는 `/blocklocker list`로 접근 목록을 확인하고 `/blocklocker trust <플레이어>` / `/blocklocker untrust <플레이어>`로 접근 플레이어를 관리하며 `/blocklocker transfer <플레이어>`로 소유권을 이전할 수 있습니다.
* `/blocklocker setting`으로 보호된 블록의 호퍼/공용/골렘 접근 설정 GUI를 열 수 있습니다.
* 업데이트 알림을 지원합니다.

빌드
----

Maven을 사용합니다.

```bash
mvn clean package
```

빌드 후 `target` 폴더에 `blocklocker-XX.jar` 파일이 생성됩니다.

명령어
------

```text
/blocklocker help
/blocklocker reload
/blocklocker setting
/blocklocker setting on
/blocklocker setting off
/blocklocker setting toggle <hopper-input|hopper-output|public-access|golem-access>
/blocklocker list
/blocklocker trust <플레이어>
/blocklocker untrust <플레이어>
/blocklocker transfer <플레이어>
/blocklocker bypass <add|remove|list> [플레이어]
```

권한
----

* `blocklocker.protect` — 보호 가능한 블록 보호
* `blocklocker.bypass` — 다른 사람의 보호 우회
* `blocklocker.bypass.manage` — 우회 허용 플레이어 관리
* `blocklocker.admin` — 다른 사람의 보호 표지판 수정/제거
* `blocklocker.reload` — 플러그인 리로드
* `blocklocker.wilderness` — Towny 클레임 밖 야생 지역에 상자 설치

개발자 API
----------

다른 플러그인에서 BlockLocker와 연동하려면 `BlockLockerAPI` 또는 `BlockLockerAPIv2`를 사용할 수 있습니다.

플러그인 활성화 확인:

```java
boolean enabled = Bukkit.getPluginManager().getPlugin("BlockLocker") != null;
```

소유자 조회 예시:

```java
Optional<OfflinePlayer> owner = BlockLockerAPI.getOwner(block);
```

Redstone 접근 가능 여부 확인 예시:

```java
private boolean isRedstoneAllowed(Block block) {
    BlockLockerPlugin plugin = BlockLockerAPIv2.getPlugin();
    Optional<Protection> protection = plugin.getProtectionFinder().findProtection(block);
    if (!protection.isPresent()) {
        return true;
    }
    Profile redstoneProfile = plugin.getProfileFactory().fromRedstone();
    return protection.get().isAllowed(redstoneProfile);
}
```

## 파일 자동 초기화 (1.15.1-berry.2)

누락된 파일만 기본값으로 재생성하며, 자동 복구용 백업을 생성하거나 이전 내용에서 복원하지 않습니다. 자세한 정책은 [AUTOMATIC-FILES.md](AUTOMATIC-FILES.md)를 참고하세요.

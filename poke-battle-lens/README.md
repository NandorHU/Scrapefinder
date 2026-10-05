# Poké Battle Lens (Android)

Magyar nyelvű, helyben működő Pokémon csatasegéd. Android 8.0+.

## Funkciók

- Teljes képernyő megosztása Android MediaProjection API-val; nincs root vagy Accessibility-engedély.
- Folyamatos figyelés: 100 ms-os képernyőellenőrzés a három kijelölt területen, változáskor új felismerés. Egyszerre egy OCR-kérés, legalább 300 ms az indítások között; változatlan képnél 2 másodperces tartalék frissítés. A tényleges felismerési idő telefonfüggő, a 100 ms nem válaszidő-garancia.
- A háttér animációja új olvasást kér, de nem törli és nem érvényteleníti folyamatosan az OCR-válaszokat. Új Pokémon- vagy támadáslista felismerésekor törli az előző jelzéseket, majd két egyező név/támadás olvasás után jeleníti meg az újakat. Profilváltás, Force load, szünet és méretváltás továbbra is érvényteleníti a korábbi kérést.
- Beépített, offline ML Kit OCR (angol Pokémon- és támadásnevek).
- 1351 Pokémon-forma, 919 támadás, két típus együttes szorzója.
- A játék támadásnevei mellett apró típusszorzók jelennek meg. A ★ a bázisadatokból becsült legerősebb sebző támadásokat jelöli; a legmagasabb mutató 90%-át elérő támadások közösen kapnak csillagot. Az OCR a nevek pozícióját is felismeri; nincs szükség négy külön jelzéshely kézi beállítására. A jelzések nem fedik a felismert neveket és átengedik az érintést.
- Minden felismert támadás mellett látszik az alap-pontosság százaléka. Az 1×-től eltérő típusszorzó a százalék fölött, két rövid sorban szerepel; bizonytalan felismerésnél `?`. Az 1× és az állapottámadás szövege alapból rejtve marad. A 44 dp méretű, húzható gombra koppintva minden szorzó és az állapottámadások `áll.` jelzése 5 másodpercre előhívható.
- Útválasztás és más képernyő esetén a jelzések eltűnnek; a kis gomb `○`, a menü **Várakozás csatára** állapotot mutat. Csata és látható támadásmenü felismerésekor automatikusan folytatja. A kézi javítások nem kényszerítik csatává az útválasztót. Téves OCR vagy rossz profil szintén várakozást okozhat; ez nem teljes játékállapot-ismeret.
- Hosszan nyomva megnyílik a vezérlőmenü: mentett profilválasztó, Force load, szünet, beállítások és leállítás. A figyelés a nyitott menüben szünetel.
- **Force load** a hosszan nyomással megnyitható menüben: újratölti az aktív mentett profilt, eldobja a korábbi képkockát és OCR-eredményt, majd új olvasást indít. Szünetből is folytatja a figyelést.
- **Profil mentése**, **Mentés más néven**, **Mentett profil újratöltése** a főképernyőn. A másolat megtartja a területeket, generációt és kézi javításokat; a profilválasztóban visszatölthető.
- Játékonként menthető 3 olvasási terület; nagy képnézet, csippentéses nagyítás, kétujjas mozgatás, fogható sarokpontok és visszavonás.
- Kézi névjavítás és képernyőképes felismerési teszt.
- Generációválasztás (1–9): korábbi Pokémon-típusok, ismert támadásmódosítások, Gen 1 és Gen 2–5 típustábla, Gen 1–3 típus szerinti fizikai/speciális kategória.

## Használat

1. Telepítsd az APK-t.
2. Készíts teljes képernyőképet a játék nyitott támadásmenüjéről.
3. Válassz vagy hozz létre profilt. A **Profil beállítása / kép tesztelése** gombbal nyisd meg a képet.
4. A nagy képnézetben válaszd ki az ellenfél nevét, a saját Pokémon nevét, majd a négy támadást. Területváltáskor a kép arra közelít. Csippentéssel vagy a **+/−** gombokkal nagyíthatsz, két ujjal mozgathatsz. Egyujjas mozgatáshoz válts **Mozgatás** módra, majd vissza **Kijelölés** módra. Egy ujjal húzz téglalapot; a sarkokat külön húzhatod. **Visszavonás**: előző kijelölés helyreállítása; **Teljes kép**: teljes képernyőkép megmutatása. Csak a neveket jelöld ki: ne a szintet/HP-t.
5. A **Gen** gombbal válaszd a játék generációját, teszteld a képet, majd nyomd meg a **Profil mentése** gombot. A főképernyő **Mentés más néven** gombjával külön változatot készíthetsz. A teszteredmény külön ablakban nyílik meg, így a kép nem zsugorodik össze.
6. Engedélyezd a más appok feletti megjelenítést, majd indítsd a figyelést és engedélyezd a képernyőmegosztást.
7. Válts a játékra. Pokémonváltáskor nem kell új képernyőképet készíteni: a látható nevek és támadások automatikusan frissülnek. A kis gombot húzd a név/támadás-területeken kívülre. Koppintás: minden szorzó 5 mp-re; hosszan nyomás: profil és vezérlés.

Automatikus követéshez a kézi névjavításokat töröld az **Automatikus mód** gombbal. A támadáslista legyen látható: a rejtett támadásokat az app nem tudja kiolvasni. Szorosan a nevekre kalibrálj, hogy a csata animációi ne indítsanak felesleges újraolvasást.

A Dungeons & Pokémon álló profil a mellékelt példa elrendezését közelíti. A címsáv, kijelzőméret és görgetés miatt a saját képernyőképeden igazítsd. A GBA fekvő profil kiindulási példa; kalibrálni kell. Álló/fekvő elrendezéshez külön profil javasolt. Kézi javítás után az értékek fixen megmaradnak; töröld őket az **Automatikus mód** gombbal a következő ellenfélnél.

## Az eredmények jelentése és határai

- A szorzó (0×, ¼×, ½×, 1×, 2×, 4×) a **típus szerinti** hatékonyság.
- A százalék a kiválasztott generáció szerinti **alap-pontosság**, nem a csata közben módosított találati esély. Pontosság/kitérés-fokozatok, képességek, tárgyak és speciális támadásszabályok módosíthatják. A `—` azt jelenti, hogy az adatbázisban nincs megadott alap-pontosság; ezt nem alakítjuk 0% vagy 100% értékké.
- A játékban a sebző támadások neve mellett a típusszorzó szerepel. A semleges 1× és az állapottámadások alapból rejtve maradnak; előhíváskor az állapottámadásoknál **áll.** látható, mert nincs sebzésük. A részletes képtesztben továbbra is látszik az erő, pontosság és STAB.
- A csillaghoz használt összehasonlító mutató: `power × típusszorzó × STAB × bázis Attack/Defense (vagy Sp. Attack/Sp. Defense) × alap-pontosság`. Fajadatokra épülő becslés; szint, tényleges stat, IV/EV, nature és csata közbeni állapot nincs beolvasva. Az állapottámadásokat nem rangsorolja, a prioritást és a taktikai előnyöket nem értékeli. A csillaggal jelölt semleges 1× alapból is látszik.
- Csak ellenőrzött, egyszerű, egykörös sebző támadásokat rangsorol. Ha egy felismert sebző támadás feltételes, változó/fix sebzésű, több találatos, töltést/visszatöltést igényel, más statot használ vagy nincs hozzá biztonságos szabály, az egész támadáslistán elmarad a csillagjelzés. Hiányzó név/bázisadat esetén szintén nincs csillag. A menü kiírja az okot.
- Az erőmutató: `power × type effectiveness × STAB`. **Nem pontos sebzés és nem legjobb-támadás ajánlás.** Fizikai/speciális kategória, pontosság és alaperő külön látszik.
- Attack/Sp. Attack, Defense/Sp. Defense, ability (például Levitate), item, weather, terrain, stat changes, Terastallization, dynamax, és egyedi ROM-hack szabályok nincsenek figyelembe véve. Az állapottámadások és ismeretlen/változó alaperő nem kapnak sebzésbecslést.
- Az adatbázis egyes fix/változó sebzésű támadásokat számszerű erővel jelölhet: az erőmutató ezeknél nem alkalmazható. Nincs automatikus támadás-rangsor.
- Generáción belüli különbségek és régi formák adateltérései előfordulhatnak; az app generációszinten választ, nem konkrét kiadásonként.
- Az OCR tévedhet pixeles fontnál, kis szövegnél, becenévnél vagy nem angol nyelvnél. Sikertelen felismeréskor nem használ korábbi ellenfelet.
- A képernyőrögzítést tiltó appokon nincs működésgarancia. Pokémon GO eltérő harcrendszerét nem támogatja.

## Jelzések és folyamatos felismerés

A változásfigyelő kizárja a saját jelzések és a vezérlőgomb területét. A legutóbbi képkockát RAM-ban megtartja, így statikus képernyőn is befejezheti a megerősítő olvasást; a felismerés közben érkező legújabb kép sem vész el. Foglalt OCR mellett legfeljebb fél másodpercenként dolgoz fel új képkockát, a már eltárolt változatlan képet nem mintavételezi újra minden ciklusban. Az OCR futása alatt nem számol képkocka-ujjlenyomatot: a legfrissebb képet utána egyszer, a már átmásolt RAM-tömbből vizsgálja meg. Az OCR-lapot méretkorlátozza, hogy az animáció és a felismerés kevesebb CPU-ért versenyezzen. A 0.3.2 egyenletes hátterű kijelöléseknél levágja az üres margókat, a szövegsorokat egymás alá csomagolja, és kb. 24 pixeles betűmagasságra méretezi őket. Az eredeti kép színeit és a támadások képernyőbeli koordinátáit megőrzi. Texturált, alacsony kontrasztú vagy bizonytalan területeknél a teljes kijelölés olvasására tér vissza. Ha a képen jelzés vagy a kijelölésbe eső gomb szerepel, OCR előtt röviden elrejti őket, és legalább 100 ms plusz egy új képkocka után olvas. A már tiszta első képet azonnal használja. Rövid villanás észlelhető a tiszta kép kérésénél; a tényleges válaszidő telefonfüggő.

Ha indulás után 3,5 másodpercig nem kap képet, **Nincs képkocka** üzenetet ad. Ha a jelzések elrejtése után nincs friss kép, **Nincs friss képkocka** látszik. Az OCR legfeljebb 30 másodpercet kap, hogy az első modellbetöltést vagy egy terhelt telefont ne szakítsa meg túl korán. 7 másodperc után külön indulási/lassú felismerési állapotot mutat. 30 másodperces időtúllépés után új olvasóval próbálkozik; a későn visszatérő régi választ eldobja. A hosszan nyomással megnyitott menü megőrzi a részletes állapotot. Semleges/állapottámadások esetén a sikeres felismerés külön jelzi, hogy miért üres az alapnézet.

A jelzések ablaka nem érinthető és kellően áttetsző az Android 12+ érintésvédelméhez. Biztonsági okból egyes alkalmazások ettől függetlenül is elutasíthatják a fedett érintéseket. A menü és a kis gomb szándékosan kezelhető. Szűk helyen a jelzés kimaradhat, hogy ne takarja a nevet; ilyenkor a kis gomb `?` állapotot mutat, és a részletes menüben olvasható a felismerés.

## Adatvédelem

Képkockák csak RAM-ban, helyben kerülnek feldolgozásra; nem mentjük vagy küldjük el őket. Nincs saját backend, analitika vagy hirdetés. A profilokat az app saját tárhelyére menti. A beépített ML Kit modell internet nélkül is működik. Az Android jelzi a képernyőmegosztást; lezáráskor leáll. A megosztási engedély minden új indításhoz szükséges.

## Build

JDK 17, Android SDK 35, Gradle 8.9. `./gradlew testDebugUnitTest lintDebug assembleDebug`.

A GitHub Actions elkészíti a tesztelt, debug-kulccsal aláírt `poke-battle-lens-0.3.4.apk` fájlt. Az artifact az adott Actions-futásnál tölthető le. A debug APK telepíthető tesztverzió; nem Play Store-kiadás. Frissítésekhez tartsd meg ugyanazt az aláírókulcsot. A release-aláírókulcsot **soha ne commitold**.

## Forrásadatok

[PokeAPI CSV](https://github.com/PokeAPI/pokeapi/tree/master/data/v2/csv), BSD-3-Clause (lásd THIRD_PARTY_NOTICES.md). Adatfrissítés: `python3 tools/build_data.py` és `python3 tools/build_ranking_data.py`. A generátor hálózatot igényel; az app nem.

A Pokémon nevek és kapcsolódó védjegyek a megfelelő jogosultak tulajdonát képezik. Független rajongói segédapp.

## Android- és érintéstesztek

A CI külön `touch-fixture` tesztappot telepít az emulátorra. Más UID alatt futó valódi gombon ellenőrzi, hogy a megrajzolt szorzójelzésen átmenő érintés célba ér. A tesztapp nem része a kiadott APK-nak. A valódi MediaProjection-teszt statikus csatával indul, majd folyamatosan animált háttér mellett Pokémonváltást, útválasztást és visszatérést ellenőriz, beleértve a Ditto/Charm/Nasty Plot/Play Nice/Nuzzle jelzések előhívását. Képernyőképen ellenőrzi a ténylegesen kirajzolt szorzókat és azok eltűnését útválasztáskor. Ez tesztjelenet, nem az élő játékoldal. A 2026-10-04-i élő ellenőrzést a játékoldal ebben a böngészőben megjelenő **Deployment Paused** oldala blokkolta. A jelzésnézet képe az Actions tesztjelentésében `badges-preview.png` néven elérhető.

## Helyi OCR-teszt

Az opcionális referencia-képernyőképeket az `app/src/androidTest/assets/` mappába kell másolni `01-*.png`–`04-*.png` néven. Ezeket a gitignore kizárja. `./gradlew connectedDebugAndroidTest` futtatja a szintetikus képes tesztet és a helyi referenciatesztet. A referenciateszt a beszélgetés négy mintájára van beállítva (Hariyama/Toucannon és Swampert/Lickitung).

## Frissítések aláírása

A helyi APK és a GitHub Actions friss környezetében készült debug APK eltérő aláírókulcsot kaphat. A forrás és build reprodukálható, az aláírás azonosságához külön, tartós signing-keystore és GitHub Secret konfiguráció szükséges. Eltérő kulccsal készült verzió telepítéséhez a korábbi app eltávolítása kell, ami törli a helyi profilokat. Az aláírókulcs nem része a nyilvános repository-nak.

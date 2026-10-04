# Poké Battle Lens (Android)

Magyar nyelvű, helyben működő Pokémon csatasegéd. Android 8.0+.

## Funkciók

- Teljes képernyő megosztása Android MediaProjection API-val; nincs root vagy Accessibility-engedély.
- Folyamatos figyelés: 100 ms-os képernyőellenőrzés a három kijelölt területen, változáskor új felismerés. Egyszerre egy OCR-kérés, legalább 300 ms az indítások között; változatlan képnél 2 másodperces tartalék frissítés. A tényleges felismerési idő telefonfüggő, a 100 ms nem válaszidő-garancia.
- Pokémon- vagy támadásváltáskor az előző eredmény azonnal törlődik a változás észlelésekor. A közben elavult OCR-válaszokat eldobja.
- Beépített, offline ML Kit OCR (angol Pokémon- és támadásnevek).
- 1351 Pokémon-forma, 919 támadás, két típus együttes szorzója.
- Kompakt, 236 dp széles, tartalomhoz igazodó lebegő ablak: egy támadás egy sor, a név mellett típusszorzó. Húzható, összecsukható; szünet és azonnali leállítás.
- **Force load** a lebegő ablakon: újratölti az aktív mentett profilt, eldobja a korábbi képkockát és OCR-eredményt, majd új olvasást indít. Szünetből is folytatja a figyelést.
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
7. Válts a játékra. Pokémonváltáskor nem kell új képernyőképet készíteni: a látható nevek és támadások automatikusan frissülnek. Húzd a lebegő ablakot a név/támadás-területeken kívülre.

Automatikus követéshez a kézi névjavításokat töröld az **Automatikus mód** gombbal. A támadáslista legyen látható: a rejtett támadásokat az app nem tudja kiolvasni. Szorosan a nevekre kalibrálj, hogy a csata animációi ne indítsanak felesleges újraolvasást.

A Dungeons & Pokémon álló profil a mellékelt példa elrendezését közelíti. A címsáv, kijelzőméret és görgetés miatt a saját képernyőképeden igazítsd. A GBA fekvő profil kiindulási példa; kalibrálni kell. Álló/fekvő elrendezéshez külön profil javasolt. Kézi javítás után az értékek fixen megmaradnak; töröld őket az **Automatikus mód** gombbal a következő ellenfélnél.

## Az eredmények jelentése és határai

- A szorzó (0×, ¼×, ½×, 1×, 2×, 4×) a **típus szerinti** hatékonyság.
- A lebegő ablakban a sebző támadások neve mellett a típusszorzó szerepel; az állapottámadásoknál **állapot**, mert nincs sebzésük. A részletes képtesztben továbbra is látszik az erő, pontosság és STAB.
- Az erőmutató: `power × type effectiveness × STAB`. **Nem pontos sebzés és nem legjobb-támadás ajánlás.** Fizikai/speciális kategória, pontosság és alaperő külön látszik.
- Attack/Sp. Attack, Defense/Sp. Defense, ability (például Levitate), item, weather, terrain, stat changes, Terastallization, dynamax, és egyedi ROM-hack szabályok nincsenek figyelembe véve. Az állapottámadások és ismeretlen/változó alaperő nem kapnak sebzésbecslést.
- Az adatbázis egyes fix/változó sebzésű támadásokat számszerű erővel jelölhet: az erőmutató ezeknél nem alkalmazható. Nincs automatikus támadás-rangsor.
- Generáción belüli különbségek és régi formák adateltérései előfordulhatnak; az app generációszinten választ, nem konkrét kiadásonként.
- Az OCR tévedhet pixeles fontnál, kis szövegnél, becenévnél vagy nem angol nyelvnél. Sikertelen felismeréskor nem használ korábbi ellenfelet.
- A képernyőrögzítést tiltó appokon nincs működésgarancia. Pokémon GO eltérő harcrendszerét nem támogatja.

## Adatvédelem

Képkockák csak RAM-ban, helyben kerülnek feldolgozásra; nem mentjük vagy küldjük el őket. Nincs saját backend, analitika vagy hirdetés. A profilokat az app saját tárhelyére menti. A beépített ML Kit modell internet nélkül is működik. Az Android jelzi a képernyőmegosztást; lezáráskor leáll. A megosztási engedély minden új indításhoz szükséges.

## Build

JDK 17, Android SDK 35, Gradle 8.9. `./gradlew testDebugUnitTest lintDebug assembleDebug`.

A GitHub Actions elkészíti a tesztelt, debug-kulccsal aláírt `poke-battle-lens-0.2.2.apk` fájlt. Az artifact az adott Actions-futásnál tölthető le. A debug APK telepíthető tesztverzió; nem Play Store-kiadás. Frissítésekhez tartsd meg ugyanazt az aláírókulcsot. A release-aláírókulcsot **soha ne commitold**.

## Forrásadatok

[PokeAPI CSV](https://github.com/PokeAPI/pokeapi/tree/master/data/v2/csv), BSD-3-Clause (lásd THIRD_PARTY_NOTICES.md). Adatfrissítés: `python3 tools/build_data.py`. A generátor hálózatot igényel; az app nem.

A Pokémon nevek és kapcsolódó védjegyek a megfelelő jogosultak tulajdonát képezik. Független rajongói segédapp.

## Helyi OCR-teszt

Az opcionális referencia-képernyőképeket az `app/src/androidTest/assets/` mappába kell másolni `01-*.png`–`04-*.png` néven. Ezeket a gitignore kizárja. `./gradlew connectedDebugAndroidTest` futtatja a szintetikus képes tesztet és a helyi referenciatesztet. A referenciateszt a beszélgetés négy mintájára van beállítva (Hariyama/Toucannon és Swampert/Lickitung).

## Frissítések aláírása

A helyi APK és a GitHub Actions friss környezetében készült debug APK eltérő aláírókulcsot kaphat. A forrás és build reprodukálható, az aláírás azonosságához külön, tartós signing-keystore és GitHub Secret konfiguráció szükséges. Eltérő kulccsal készült verzió telepítéséhez a korábbi app eltávolítása kell, ami törli a helyi profilokat. Az aláírókulcs nem része a nyilvános repository-nak.

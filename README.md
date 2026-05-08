# Slagalica — Android App

Mobilna aplikacija inspirisana kviz emisijom Slagalica, razvijena u okviru predmeta **Mobilne aplikacije** (Računarstvo i automatika, 2025/26).

Aplikacija omogućava takmičenje dva igrača kroz šest igara: **Ko zna zna**, **Spojnice**, **Asocijacije**, **Skočko**, **Korak po korak** i **Moj broj**.

---

## Tehnologije

- Java · Android SDK
- Firebase Authentication
- Firebase Firestore
- MVVM arhitektura (ViewModel + LiveData + Repository)
- Material Design 3

---

## Pokretanje projekta

### Preduslovi

- Android Studio **Hedgehog** ili noviji
- JDK 17+
- Android uređaj ili emulator sa API level **30+**
- Aktivan Firebase projekat (korak 2)

---

### Korak 1 — Klonirati repozitorijum

```bash
git clone https://github.com/ikabic/e2-ma-tim11-2026.git
cd e2-ma-tim11-2026
```

---

### Korak 2 — Dodati `google-services.json`

Ovaj fajl **nije uključen u repozitorijum** iz bezbednosnih razloga (sadrži API ključeve).

Da biste ga dobili:

1. Otvorite [Firebase Console](https://console.firebase.google.com/)
2. Uđite u projekat **Slagalica** (pristup dobijate od člana tima)
3. Idite na **Project Settings → General → Your apps**
4. Preuzmite `google-services.json`
5. Kopirajte fajl u direktorijum:

```
app/google-services.json
```

---

### Korak 3 — Otvoriti u Android Studiju

1. Pokrenite Android Studio
2. Izaberite **File → Open** i otvorite root folder projekta
3. Sačekajte da Gradle sync završi (može potrajati pri prvom otvaranju)

---

### Korak 4 — Pokrenuti aplikaciju

1. Povežite Android uređaj ili pokrenite emulator
2. Kliknite **Run** ili koristite `Shift + F10`

---



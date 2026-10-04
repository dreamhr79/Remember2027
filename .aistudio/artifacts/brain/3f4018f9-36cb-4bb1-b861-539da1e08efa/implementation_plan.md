# Integracija Google Kalendara i Interaktivna Trajna Dnevna Obavijest

Sveobuhvatna nadogradnja aplikacije Remember koja uvodi podršku za čitanje događaja iz Google Kalendara putem Android Calendar Providera, omogućuje korisniku odabir željenih kalendara, popravlja perzistentnost obavijesti te donosi interaktivnu trajnu obavijest s dnevnim rasporedom (danas/sutra/prekosutra) i 5-dnevnim sažetkom.

## Korisnički pregled i ključne odluke

> [!IMPORTANT]
> Na temelju vaših odgovora u prethodnom koraku potvrđene su sljedeće postavke arhitekture:

- **Izvor kalendara**: Korištenje sistemskog **Android Calendar Providera** (`CalendarContract`). Omogućuje trenutačni pristup svim Google i lokalnim računima na uređaju, radi bez ponovnog OAuth logina i pruža pouzdanu podršku i u offline načinu rada.
- **Postojanost obavijesti (Persistent Notification)**: Ispravak i primjena `setOngoing(true)` uz `setAutoCancel(false)` tako da se obavijest ne može slučajno ukloniti povlačenjem (swipe), već ostaje fiksirana poput TickTick ili Business Calendar aplikacija.
- **Interakcija iz obavijesti**: Omogućeno brzo označavanje zadataka kao riješenih (`Označi kao riješeno`) izravno putem akcijskih gumba obavijesti, bez potrebe za otvaranjem aplikacije.

---

## 1. Pregled i koncept

### Kako trenutno radi Google Tasks u Rememberu?
U trenutnoj arhitekturi aplikacije, Google Tasks radi isključivo kao **alat za jednokratni uvoz (import)**:
1. Povezuje se na korisnikov Google račun preko Google Identity Services (`play-services-auth` i `CredentialManager`) tražeći opseg `tasks.readonly`.
2. Dohvaća popise zadataka s Google Tasks poslužitelja putem REST API-ja.
3. Pretvara preuzete zadatke u lokalne zabilješke i kontrolne popise (checklists) unutar lokalne Room baze.
4. Trenutno **nema** automatske dvosmjerne sinkronizacije u pozadini (izmjene napravljene u aplikaciji ne šalju se natrag na Google Tasks).

### Što donosi nova nadogradnja?
1. **Povezivanje s Google Kalendarom**:
   - Aplikacija traži dopuštenje `READ_CALENDAR`.
   - Korisnik u postavkama dobiva novu sekciju "Kalendar" gdje vidi sve kalendare s uređaja (npr. osobni Google račun, posao, praznici) s prepoznatljivim bojama i može birati koji su aktivni.
   - Prikaz vremenskih intervala (npr. `09:00 - 10:30 Team meeting`) ili cjelodnevnih događaja.
2. **Interaktivna trajna obavijest ("Dnevni raspored")**:
   - Obavijest je trajno prisutna (`ongoing`) u traci obavijesti.
   - Prikazuje datum i dan u tjednu (npr. *Danas, 4. listopada*), broj zadataka i događaja.
   - Sadrži navigacijske strelice `◀ Prethodni` i `Sljedeći ▶` kojima korisnik može listati dane: Danas, Sutra, Prekosutra...
   - Na dnu sadrži indikator/sažetak: *U sljedećih 5 dana: X obveza*.
   - Omogućuje dovršavanje zadataka jednim dodirom.
3. **Popravak perzistentnosti postojećih podsjetnika**:
   - Kod podsjetnika na bilješke i zadatke opcija "Zadrži obavijest dok se ne odradi" sada eksplicitno postavlja `setOngoing(true)`, rješavajući problem nestajanja obavijesti pri swipeu.

---

## 2. Korisničko iskustvo i vizualni dizajn

### Korisnički tokovi (User Flows)
1. **Aktivacija kalendara**:
   - Korisnik otvara *Postavke -> Kalendar i Dnevna obavijest*.
   - Aplikacija prikazuje prekidač za uključivanje kalendara i zahtijeva standardno Android dopuštenje za čitanje kalendara.
   - Prikazuje se lista detektiranih kalendara s kvadratićima u boji kalendara i checkboxovima za odabir.
2. **Prikaz i listanje u traci obavijesti**:
   - Trajna obavijest se pojavljuje sa stilom prilagođenim temi sustava.
   - Glavni naslov: `📅 Danas (3 događaja, 2 zadatka)`
   - Sadržaj: popis stavki za taj dan (npr. `• 10:00 Sastanak`, `• 14:00 Zubarka`, `☐ Kupiti namirnice`).
   - Akcije:
     - `◀ Dan ranije`
     - `Dan kasnije ▶`
     - `✓ Riješi prvi zadatak` (ili otvaranje brzog pregleda)
   - Podnožje: `Sljedećih 5 dana: ukupno 11 događaja i zadataka`.

### Vizualni detalji & Material 3
- Kartice za odabir kalendara unutar postavki u skladu s postojećim M3 dizajnom aplikacije (`M3GroupedList`, tonalne boje, dinamičke ikone).
- Jasna vizualna distinkcija između kalendarskih događaja (vremenska oznaka + ikona kalendara) i lokalnih zadataka (checkbox/kvadratić).

---

## 3. Ključne odluke i tehnički kompromisi

1. **Android Calendar Provider vs. Google Calendar REST API**:
   - *Odluka*: Android `CalendarContract.Instances`.
   - *Zašto*: Nije potreban zaseban Google API ključ niti ponovno prijavljivanje; sinkronizaciju s Google oblakom već obavlja sam Android OS u pozadini; radi pouzdano i bez interneta.
2. **Arhitektura navigacije kroz obavijest**:
   - *Odluka*: `BroadcastReceiver` (`AgendaNotificationReceiver`) koji prima klikove na akcije `PREV_DAY` i `NEXT_DAY`, pomiče lokalni dan u stanju obavijesti i trenutačno ažurira obavijest pomoću `NotificationManagerCompat.notify()`.
   - *Zašto*: Trenutna reakcija na dodir bez pokretanja teških servisa ili usporavanja sučelja.
3. **Prikaz događaja za sljedećih 5 dana**:
   - Upit na `CalendarContract.Instances` u rasponu od `danas u 00:00` do `danas + 5 dana u 23:59`, spojen s lokalnim zadacima s rokom ili podsjetnikom iz Room baze.

---

## 4. Tehnička arhitektura i strategija podataka

```
┌────────────────────────────────────────────────────────────────────────┐
│                          KORISNIČKO SUČELJE                            │
│  Postavke (SettingsScreen)  ◄──►  Odabir kalendara (CalendarSettings)  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                    SLOJ PODATAKA & SINKRONIZACIJE                      │
│                                                                        │
│   ┌───────────────────────────┐      ┌───────────────────────────────┐ │
│   │   CalendarRepository      │      │    NoteRepository (Room)      │ │
│   │   - Upit na CalendarContract│     │    - Zadaci s podsjetnicima   │ │
│   │   - Filtriranje po ID-ju  │      │    - Kontrolni popisi (TODO)  │ │
│   └─────────────┬─────────────┘      └──────────────┬────────────────┘ │
│                 │                                   │                  │
│                 └─────────────────┬─────────────────┘                  │
│                                   │                                    │
│   ┌───────────────────────────────▼────────────────────────────────┐   │
│   │                     AgendaProvider                             │   │
│   │  - Spajanje zadataka i događaja za odabrani datum              │   │
│   │  - Izračun 5-dnevnog sažetka (broj događaja i zadataka)        │   │
│   └───────────────────────────────┬────────────────────────────────┘   │
└───────────────────────────────────┼────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                      SUSTAV OBAVIJESTI (ANDROID)                       │
│                                                                        │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │ AgendaNotificationManager                                      │   │
│   │  - setOngoing(true) [Nemoguće ukloniti swipeom]                │   │
│   │  - Prikaz dana, popisa stavki i 5-dnevnog brojača              │   │
│   └───────────────────────────────┬────────────────────────────────┘   │
│                                   │ (Akcije)                           │
│   ┌───────────────────────────────▼────────────────────────────────┐   │
│   │ AgendaNotificationReceiver (BroadcastReceiver)                 │   │
│   │  - ACTION_PREV_DAY / ACTION_NEXT_DAY -> ažurira obavijest      │   │
│   │  - ACTION_COMPLETE_TASK -> označava zadatak gotovim u bazi     │   │
│   └────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────┘
```

### Novi i ažurirani moduli
1. **`CalendarRepository`**:
   - `fun getAvailableCalendars(): List<CalendarInfo>` (naziv, id, račun, boja).
   - `fun getEventsForRange(startMillis: Long, endMillis: Long, calendarIds: Set<Long>): List<CalendarEvent>`.
2. **`CalendarPrefs`**:
   - Spremanje u DataStore: `enabledCalendarIds`, `isAgendaNotificationEnabled`, `show5DaySummary`.
3. **`AgendaNotificationManager` & `AgendaNotificationReceiver`**:
   - Kreiranje i ažuriranje trajne obavijesti s navigacijom po danima.
   - Prikaz naslova dana, liste obveza (događaji + zadaci) i sažetka za 5 dana.
4. **Ispravak postojećih podsjetnika u `ReminderReceiver`**:
   - Postavljanje `.setOngoing(keepUntilDone)` tako da se pojedinačni podsjetnik na zadatak ne može ukloniti prije nego što se označi kao završen.
5. **Dopuštenja u `AndroidManifest.xml`**:
   - Dodavanje `<uses-permission android:name="android.permission.READ_CALENDAR" />`.

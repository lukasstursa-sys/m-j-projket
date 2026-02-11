# Hlasové Poznámky / Czech Speech Notes

Android aplikace pro přepis mluveného slova v českém jazyce do textových poznámek.

## Funkce

### Přepis řeči
- Rozpoznávání české řeči v reálném čase pomocí Android SpeechRecognizer
- Živý náhled přepisovaného textu během mluvení
- Kontinuální nahrávání - aplikace poslouchá dokud ji nezastavíte
- Automatické restartování po pauzách v řeči

### Správa poznámek
- Vytváření poznámek hlasem i ručně
- Úprava názvu, textu i štítku poznámky
- Štítkování (labeling) poznámek pro organizaci
- Filtrování poznámek podle štítků
- Mazání poznámek s potvrzením

### AI funkce (volitelné)
- **Shrnutí** - AI vytvoří stručné shrnutí poznámky
- **Odrážky** - převod textu na přehledné body
- **Korekce** - oprava gramatiky a interpunkce z přepisu řeči
- Podpora OpenAI API a kompatibilních služeb (Ollama, LM Studio)
- Výsledek AI lze nahradit, připojit na konec, nebo zkopírovat

### Sdílení a export
- Sdílení poznámky přes jakoukoliv aplikaci (email, messenger, atd.)
- Kopírování textu do schránky
- Export poznámky jako .txt soubor do složky Downloads

### Další práce s poznámkami
- Diktování přímo do otevřené poznámky (přidávání textu hlasem)
- Ruční editace textu kdykoliv
- Dlouhý stisk na poznámku v seznamu zobrazí kontextové menu

## Technologie

- **Jazyk:** Kotlin
- **Min SDK:** 26 (Android 8.0)
- **UI:** Material Design 3
- **Databáze:** Room (SQLite)
- **Rozpoznávání řeči:** Android SpeechRecognizer (cs-CZ)
- **AI:** OpenAI-kompatibilní API (volitelné)
- **Architektura:** MVVM (ViewModel + LiveData + Repository)

## Struktura projektu

```
app/src/main/java/cz/stursa/speechnotes/
├── MainActivity.kt          # Hlavní obrazovka se seznamem a nahráváním
├── NoteDetailActivity.kt    # Detail/editace poznámky
├── NoteViewModel.kt         # ViewModel pro správu dat
├── SpeechNotesApp.kt        # Application třída
├── adapter/
│   └── NoteAdapter.kt       # RecyclerView adapter pro seznam poznámek
├── ai/
│   ├── AiTextProcessor.kt   # AI zpracování textu
│   └── AiSettingsManager.kt # Správa nastavení AI API
├── data/
│   ├── Note.kt              # Datová entita poznámky
│   ├── NoteDao.kt           # Data Access Object
│   ├── NoteRepository.kt    # Repository vrstva
│   └── AppDatabase.kt       # Room databáze
└── speech/
    └── CzechSpeechRecognizer.kt  # Wrapper pro SpeechRecognizer
```

## Jak otevřít a sestavit

1. Otevřete projekt v **Android Studio**
2. Synchronizujte Gradle
3. Spusťte na zařízení nebo emulátoru s Android 8.0+
4. Při prvním spuštění povolte přístup k mikrofonu

## Nastavení AI (volitelné)

V detailu poznámky zvolte z menu jednu z AI funkcí. Při prvním použití se zobrazí dialog pro nastavení:
- **API klíč** - váš OpenAI API klíč (nebo kompatibilní)
- **URL API** - výchozí je OpenAI, lze změnit na Ollama/LM Studio
- **Model** - výchozí gpt-3.5-turbo

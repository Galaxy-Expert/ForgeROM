# Translating ForgeROM

All user-facing text lives in Android string resources, so translating needs no coding.

## Easiest way (GitHub repository)
1. Copy `app/strings/values/strings.xml` to `app/strings/values-<code>/strings.xml`
   (for example `values-de` for German, `values-pt-rBR` for Brazilian Portuguese).
2. Translate only the text between the tags. Do not change the `name="..."` parts.
3. Keep placeholders such as `%1$s` and `%2$d` exactly as they are.
4. Escape apostrophes with a backslash: `don\'t`.
5. Commit. The build adds the language to the app and to the in-app language list automatically.

Missing strings fall back to English. Strings in `app/strings` override the ones inside the project zip,
so you can also fix existing translations the same way.
The AI research notes are written in the language the app is currently set to.

## Building locally (Android Studio)
Copy the file to `app/src/main/res/values-<code>/strings.xml`, then add the language tag to
`res/xml/locales_config.xml` and to `res/values/supported_languages.xml`.

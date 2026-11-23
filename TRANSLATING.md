# Translating ForgeROM

All user-facing text lives in Android string resources, so translating needs no coding.

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<code>/strings.xml`
   (for example `values-de` for German, `values-pt-rBR` for Brazilian Portuguese).
2. Translate only the text between the tags. Do not change the `name="..."` parts.
3. Keep placeholders such as `%1$s` and `%2$d` exactly as they are.
4. Escape apostrophes with a backslash: `don\'t`. A value must not start with `?` or `@` (escape it: `\?`).
5. Commit (or open a pull request). The build adds the language to the app and to the in-app language list automatically.

Missing strings fall back to English. You can also fix existing translations the same way.
The AI research notes are written in the language the app is currently set to.

The build checks every strings file and reports mistakes (invalid XML, bare apostrophes, a leading `?`) before compiling.

## Building locally (Android Studio)
Add the language tag to `res/xml/locales_config.xml` and to `res/values/supported_languages.xml`
(the GitHub build regenerates both files from the `values-xx` folders).

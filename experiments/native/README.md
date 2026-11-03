# Java + C без JNI (java.lang.foreign / Panama)

Демонстрация того, как из Java вызывать Win32-функции напрямую — **без JNI, без .dll,
без компилятора C**. Нужен JDK 22+ (в 22 API `java.lang.foreign` вышел из preview).

## Что делает

`NativeDarkMode2.java` включает тёмный заголовок окна (Windows 10 19041+ / Windows 11):

1. создаёт `JFrame` и вызывает `addNotify()` — нативное окно уже есть, но не показано;
2. находит `HWND` через `user32!FindWindowW`;
3. вызывает `dwmapi!DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE=20, 1, 4)`;
4. форсит пересчёт рамки через `user32!SetWindowPos(..., SWP_FRAMECHANGED)`;
5. показывает окно и снимает полосу заголовка скриншотом.

Порядок важен: если применить атрибут **после** `setVisible`, Windows 10 его
принимает (`HRESULT 0x0`), но визуально не перекрашивает. Проверено на Windows 10 22H2
(сборка 19045): «до» — яркость заголовка 229, «после» — 11.

`NativeDarkMode.java` — та же попытка, но с применением после показа (не сработало,
оставлено как контрастный пример).

## Запуск

```bat
javac --release 22 -d build\native experiments\native\NativeDarkMode2.java
java --enable-native-access=ALL-UNNAMED -cp build\native NativeDarkMode2
```

Флаг `--enable-native-access` убирает предупреждение о «restricted method»
(`SymbolLookup.libraryLookup`). Тот же флаг можно прописать в манифесте jar:
`Enable-Native-Access: ALL-UNNAMED`.

## Почему это удобно именно для лаунчера

- Ничего не нужно компилировать под Windows/Linux — вызовы идут в системные библиотеки.
- Нет .dll рядом с jar: раздача остаётся одним файлом.
- Код грузится только на JDK 22+ (иначе класс просто не трогаем и остаётся светлая рамка).

Кандидаты на такое же подключение: Job Objects (гарантированное убийство дерева процессов
Minecraft), DPI-awareness, блокировки файлов, размеры рабочей области.

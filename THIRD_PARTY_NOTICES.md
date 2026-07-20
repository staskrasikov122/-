# Уведомления о стороннем коде

## AndroidLiquidGlass / Backdrop

В интерфейсе GsGit Admin используются библиотека Backdrop и исходные компоненты
из проекта AndroidLiquidGlass:

- автор: Kyant0;
- репозиторий: https://github.com/Kyant0/AndroidLiquidGlass;
- зафиксированный снимок компонентов: `kmp@b18eb0ff12c616546a68c72e7d0097f1ab286c87`;
- библиотека: `io.github.kyant0:backdrop:2.0.0`;
- лицензия: Apache License 2.0.

С сохранением исходной реализации перенесены `LiquidButton`, `LiquidToggle`,
`LiquidBottomTabs`, `LiquidBottomTab`, `InteractiveHighlight`,
`DampedDragAnimation`, `DragGestureInspector` и Android-обёртка `awaitFrame`.
Изменены только namespace/import для обычного Android-модуля.
В качестве фонового изображения интерфейса также используется
`wallpaper_light.webp` из того же зафиксированного снимка проекта.

Copyright Kyant0. Licensed under the Apache License, Version 2.0.

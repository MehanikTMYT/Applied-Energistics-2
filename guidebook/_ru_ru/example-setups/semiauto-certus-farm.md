---
navigation:
  parent: example-setups/example-setups-index.md
  title: Полуавтоматическая ферма истинного кварца
  icon: certus_quartz_crystal
  position: 115
---

# Полуавтоматическая ферма истинного кварца

К сожалению, для полностью автоматической работы [простой фермы истинного кварца](simple-certus-farm.md) нужен <ItemLink id="flawless_budding_quartz" />.
Для этого потребуется либо [пространственный ввод/вывод](../ae2-mechanics/spatial-io.md), либо постройка фермы у [метеорита](../ae2-mechanics/meteorites.md).

Однако AE2 умеет устанавливать и разрушать блоки, поэтому вполне
можно сделать так, чтобы ферма *сама заменяла цветущий истинный кварц*. (Потребуется периодически добавлять
<ItemLink id="flawed_budding_quartz" /> во входную бочку и извлекать <ItemLink id="quartz_block" /> из бочки
для истощённых цветущих блоков)

Полностью автоматический вариант описан в разделе [«Усовершенствованная ферма истинного кварца»](advanced-certus-farm.md).

Эта ферма немного сложнее [простой фермы истинного кварца](simple-certus-farm.md), поскольку объединяет
3 отдельных установки.

Примерная скорость приведена в разделе [«Рост истинного кварца»](../ae2-mechanics/certus-growth.md).

**ЭТО СЛОЖНАЯ КОНСТРУКЦИЯ, ГДЕ ОДНИ ЧАСТИ СКРЫТЫ ЗА ДРУГИМИ. ПОВОРАЧИВАЙТЕ ОБЗОР, ЧТОБЫ РАССМОТРЕТЬ ЕЁ СО ВСЕХ СТОРОН**

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/semiauto_certus_farm.snbt" />

  <BoxAnnotation color="#ddaaaa" min="3.7 2 1" max="4 3 2">
        (1) МЭ плоскость уничтожения #1: интерфейса настройки нет, но можно наложить «Удачу».
  </BoxAnnotation>

  <BoxAnnotation color="#ddaaaa" min="2 2 1" max="2.3 3 2">
        (2) МЭ шина хранения #1: фильтр на кристалл истинного кварца.
        <ItemImage id="certus_quartz_crystal" scale="2" />
  </BoxAnnotation>

  <DiamondAnnotation pos="3 2.5 1.5" color="#ff0000">
    Подсеть разрушения кластеров
  </DiamondAnnotation>

  <BoxAnnotation color="#aaddaa" min="3.7 1 1" max="4 2 2">
        (3) МЭ плоскость уничтожения #2: интерфейса настройки нет, но наложено «Шёлковое касание».
  </BoxAnnotation>

  <BoxAnnotation color="#aaddaa" min="2 1 1" max="2.3 2 2">
        (4) МЭ шина хранения #2: фильтр на блок истинного кварца.
        <BlockImage id="quartz_block" scale="2" />
  </BoxAnnotation>

  <DiamondAnnotation pos="3 1.5 1.5" color="#00ff00">
    Подсеть разрушения блоков истинного кварца
  </DiamondAnnotation>

  <BoxAnnotation color="#ffddaa" min="4 0.7 1" max="5 1 2">
        (5) МЭ плоскость формирования: настройки по умолчанию.
  </BoxAnnotation>

  <BoxAnnotation color="#ffddaa" min="2 0 1" max="2.3 1 2">
        (6) МЭ шина импорта: настройки по умолчанию.
  </BoxAnnotation>

  <DiamondAnnotation pos="3 0.5 1.5" color="#ddcc00">
    Подсеть установки цветущих блоков
  </DiamondAnnotation>

  <BoxAnnotation color="#aaaadd" min="0.7 2 1" max="1 3 2">
        (7) МЭ шина хранения #3: фильтр на кристалл истинного кварца. Приоритет выше, чем у основного хранилища.
        <ItemImage id="certus_quartz_crystal" scale="2" />
  </BoxAnnotation>

    <DiamondAnnotation pos="1.5 0.5 1.5" color="#00ff00">
        Вручную добавляйте несовершенный цветущий блок истинного кварца.
        <BlockImage id="flawed_budding_quartz" scale="2" />
    </DiamondAnnotation>

    <DiamondAnnotation pos="1.5 1.5 1.5" color="#00ff00">
        Вручную извлекайте блок истинного кварца.
        <BlockImage id="quartz_block" scale="2" />
    </DiamondAnnotation>

<DiamondAnnotation pos="0.5 0.5 0" color="#00ff00">
        К основной сети
    </DiamondAnnotation>

  <IsometricCamera yaw="165" pitch="5" />
</GameScene>

## Настройки

### Разрушение кластеров:

* У первой <ItemLink id="annihilation_plane" /> (1) нет интерфейса и возможности настройки, но можно наложить «Удачу».
* У первой <ItemLink id="storage_bus" /> (2) фильтр на <ItemLink id="certus_quartz_crystal" />.

### Разрушение блоков истинного кварца:

* У второй <ItemLink id="annihilation_plane" /> (3) нет интерфейса и возможности настройки, но обязательно должно быть «Шёлковое касание».
* У второй <ItemLink id="storage_bus" /> (4) фильтр на <ItemLink id="quartz_block" />.

### Установка цветущих блоков:

* У <ItemLink id="formation_plane" /> (5) настройки по умолчанию.
* У <ItemLink id="import_bus" /> (6) настройки по умолчанию.

### В основной сети:

* У третьей <ItemLink id="storage_bus" /> (7) фильтр на <ItemLink id="certus_quartz_crystal" />, а её
  [приоритет](../ae2-mechanics/import-export-storage.md#storage-priority) выше, чем у основного хранилища.

## Принцип работы

### Разрушение кластеров:

Подсеть разрушения кластеров работает почти так же, как подсеть в [простой ферме истинного кварца](simple-certus-farm.md).

1. <ItemLink id="annihilation_plane" /> пытается разрушить блок перед собой, но может разрушить только <ItemLink id="quartz_cluster" />,
   поскольку единственное хранилище в подсети — <ItemLink id="storage_bus" /> с фильтром на <ItemLink id="certus_quartz_crystal" />.
2. <ItemLink id="storage_bus" /> сохраняет кристаллы истинного кварца в бочке.

### Разрушение блоков истинного кварца

Подсеть разрушения блоков истинного кварца удаляет истощённый цветущий блок после его превращения в обычный <ItemLink id="quartz_block" />.
Она работает аналогично подсети разрушения кластеров.

1. <ItemLink id="annihilation_plane" /> пытается разрушить блок перед собой, но может разрушить только <ItemLink id="quartz_block" />,
   поскольку единственное хранилище в подсети — <ItemLink id="storage_bus" /> с фильтром на <ItemLink id="quartz_block" />.
   На плоскости должно быть «Шёлковое касание», чтобы цветущий блок не повреждался при разрушении: тогда плоскость не сможет сломать его раньше времени.
2. <ItemLink id="storage_bus" /> сохраняет блок истинного кварца в бочке для истощённых
   цветущих блоков. Для восстановления его придётся вручную бросить в воду вместе с <ItemLink id="charged_certus_quartz_crystal" />.

### Установка цветущих блоков

Подсеть установки цветущих блоков размещает новый <ItemLink id="flawed_budding_quartz" />, когда подсеть разрушения удаляет старый истощённый блок.

1. <ItemLink id="import_bus" /> импортирует цветущий блок из входной бочки.
2. Единственное хранилище в подсети — <ItemLink id="formation_plane" />, которая устанавливает цветущий блок.

### В основной сети

* <ItemLink id="storage_bus" /> даёт основной сети (а также [автоматизированному заряднику](charger-automation.md)) доступ ко всем кристаллам истинного кварца в бочке. У неё
  высокий [приоритет](../ae2-mechanics/import-export-storage.md#storage-priority), чтобы кристаллы истинного кварца в первую очередь
  возвращались в бочку, а не в основное хранилище.
---
navigation:
  parent: example-setups/example-setups-index.md
  title: Продвинутая ферма истинного кварца
  icon: certus_quartz_crystal
  position: 120
---

# Продвинутая ферма истинного кварца

По сути, это [полуавтоматическая ферма истинного кварца](semiauto-certus-farm.md), полностью встроенная в
МЭ-систему.

Вместо того чтобы хранить большой запас цветущих блоков и время от времени заменять их вручную,
эта схема использует [автоматизацию зарядника](charger-automation.md) и [автоматизацию бросания в воду](throw-in-water-automation.md),
чтобы делать это автоматически.

Приблизительная скорость указана в разделе [«Рост истинного кварца»](../ae2-mechanics/certus-growth.md).

**ЭТО СЛОЖНАЯ КОНСТРУКЦИЯ, В КОТОРОЙ ОДНИ ЭЛЕМЕНТЫ СКРЫТЫ ЗА ДРУГИМИ. ПОВОРАЧИВАЙТЕ КАМЕРУ, ЧТОБЫ ОСМОТРЕТЬ ЕЁ СО ВСЕХ СТОРОН**

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/advanced_certus_farm.snbt" />

  <BoxAnnotation color="#ddaaaa" min="3.7 2 1" max="4 3 2">
        (1) МЭ плоскость уничтожения #1: интерфейса настройки нет, но можно зачаровать на «Удачу».
  </BoxAnnotation>

  <BoxAnnotation color="#ddaaaa" min="2 2 1.7" max="3 3 2">
        (2) МЭ шина хранения #1: фильтр настроен на кристалл истинного кварца.
        <ItemImage id="certus_quartz_crystal" scale="2" />
  </BoxAnnotation>

  <DiamondAnnotation pos="3 2.5 1.5" color="#ff0000">
    Подсеть разрушения кластеров
  </DiamondAnnotation>

  <BoxAnnotation color="#aaddaa" min="3.7 1 1" max="4 2 2">
        (3) МЭ плоскость уничтожения #2: интерфейса настройки нет, наложены чары «Шёлковое касание».
  </BoxAnnotation>

  <BoxAnnotation color="#aaddaa" min="2 1 1.7" max="3 2 2">
        (4) МЭ шина хранения #2: фильтр настроен на блок истинного кварца.
        <BlockImage id="quartz_block" scale="2" />
  </BoxAnnotation>

  <DiamondAnnotation pos="3 1.5 1.5" color="#00ff00">
    Подсеть разрушения блоков истинного кварца
  </DiamondAnnotation>

  <BoxAnnotation color="#ffddaa" min="4 0.7 1" max="5 1 2">
        (5) МЭ плоскость формирования: настройки по умолчанию.
  </BoxAnnotation>

  <BoxAnnotation color="#ffddaa" min="2 0.7 2" max="3 1 3">
        (6) МЭ шина импорта: фильтр настроен на несовершенный цветущий блок истинного кварца.
        <BlockImage id="flawed_budding_quartz" scale="2" />
  </BoxAnnotation>

  <DiamondAnnotation pos="3 0.5 1.5" color="#ddcc00">
    Подсеть установки цветущих блоков
  </DiamondAnnotation>

  <BoxAnnotation color="#aaaadd" min="1.7 2 2" max="2 3 3">
        (7) МЭ шина хранения #3: фильтр настроен на кристалл истинного кварца. Приоритет выше, чем у основного хранилища.
        <ItemImage id="certus_quartz_crystal" scale="2" />
  </BoxAnnotation>

  <BoxAnnotation color="#aaaadd" min="2 1 2" max="3 2 3">
        (8) МЭ интерфейс: настроен на поддержание запаса в 1 несовершенный цветущий блок истинного кварца, установлена карта изготовления.
        <Row><BlockImage id="flawed_budding_quartz" scale="2" /> <ItemImage id="crafting_card" scale="2" /></Row>
  </BoxAnnotation>

<DiamondAnnotation pos="1.5 0.5 0" color="#00ff00">
        К основной сети, автоматизации зарядника и автоматизации бросания в воду
        <Row>
        <GameScene zoom="3" background="transparent">
          <ImportStructure src="../assets/assemblies/charger_automation.snbt" />
          <IsometricCamera yaw="195" pitch="30" />
        </GameScene>
        <GameScene zoom="3" background="transparent">
          <ImportStructure src="../assets/assemblies/throw_in_water.snbt" />
          <IsometricCamera yaw="195" pitch="30" />
        </GameScene>
        </Row>
    </DiamondAnnotation>

  <IsometricCamera yaw="165" pitch="5" />
</GameScene>

## Настройки

### Разрушение кластеров:

* Первая <ItemLink id="annihilation_plane" /> (1) не имеет интерфейса настройки, но её можно зачаровать на «Удачу».
* Первая <ItemLink id="storage_bus" /> (2) настроена на фильтрацию <ItemLink id="certus_quartz_crystal" />.

### Разрушение блоков истинного кварца:

* Вторая <ItemLink id="annihilation_plane" /> (3) не имеет интерфейса настройки, но её необходимо зачаровать на «Шёлковое касание».
* Вторая <ItemLink id="storage_bus" /> (4) настроена на фильтрацию <ItemLink id="quartz_block" />.

### Установка цветущих блоков:

* <ItemLink id="formation_plane" /> (5) имеет настройки по умолчанию.
* <ItemLink id="import_bus" /> (6) настроена на фильтрацию <ItemLink id="flawed_budding_quartz" />.

### В основной сети:

* Третья <ItemLink id="storage_bus" /> (7) настроена на фильтрацию <ItemLink id="certus_quartz_crystal" />, а её
  [приоритет](../ae2-mechanics/import-export-storage.md#storage-priority) выше, чем у основного хранилища.
* <ItemLink id="interface" /> (8) настроен на поддержание запаса в 1 несовершенный цветущий блок истинного кварца; в нём установлена <ItemLink id="crafting_card" />.

## Принцип работы

### Разрушение кластеров:

Подсеть разрушения кластеров работает почти так же, как подсеть [простой фермы истинного кварца](simple-certus-farm.md).

1. <ItemLink id="annihilation_plane" /> пытается разрушить блок перед собой, но может разрушать только <ItemLink id="quartz_cluster" />,
   поскольку единственное хранилище подсети — <ItemLink id="storage_bus" />, настроенная на фильтрацию <ItemLink id="certus_quartz_crystal" />.
2. <ItemLink id="storage_bus" /> помещает кристаллы истинного кварца в бочку.

### Разрушение блоков истинного кварца

Подсеть разрушения блоков истинного кварца уничтожает истощённый цветущий блок, когда тот превращается в обычный <ItemLink id="quartz_block" />.
Она работает аналогично подсети разрушения кластеров.

1. <ItemLink id="annihilation_plane" /> пытается разрушить блок перед собой, но может разрушать только <ItemLink id="quartz_block" />,
   поскольку единственное хранилище подсети — <ItemLink id="storage_bus" />, настроенная на фильтрацию <ItemLink id="quartz_block" />.
   На плоскость нужно наложить «Шёлковое касание», чтобы цветущий блок не повреждался при разрушении и плоскость не разрушала его раньше времени.
2. <ItemLink id="storage_bus" /> помещает блок истинного кварца в <ItemLink id="interface" />, позволяя
   [автоматизации бросания в воду](throw-in-water-automation.md) использовать его для получения нового <ItemLink id="flawed_budding_quartz" />.

### Установка цветущих блоков

Подсеть установки цветущих блоков размещает новый <ItemLink id="flawed_budding_quartz" />, когда подсеть разрушения уничтожает старый истощённый блок.

1. <ItemLink id="import_bus" /> переносит цветущий блок из <ItemLink id="interface" /> в [сетевое хранилище](../ae2-mechanics/import-export-storage.md)
2. Единственное хранилище подсети — <ItemLink id="formation_plane" />, которая устанавливает цветущий блок.

### В основной сети

* <ItemLink id="storage_bus" /> даёт основной сети (а также [автоматизации зарядника](charger-automation.md)) доступ ко всем кристаллам истинного кварца в бочке. Ей задан
  высокий [приоритет](../ae2-mechanics/import-export-storage.md#storage-priority), чтобы кристаллы истинного кварца в первую очередь
  возвращались в бочку, а не в основное хранилище.
* <ItemLink id="interface" /> даёт подсети установки цветущих блоков доступ к <ItemLink id="flawed_budding_quartz" /> и
    позволяет подсети разрушения блоков истинного кварца возвращать истощённые блоки в основную сеть.
    <ItemLink id="crafting_card" /> позволяет интерфейсу запрашивать новые цветущие блоки через [автокрафт](../ae2-mechanics/autocrafting.md) основной сети.
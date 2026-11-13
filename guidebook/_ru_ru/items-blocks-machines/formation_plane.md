---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: МЭ плоскость формирования
  icon: formation_plane
  position: 210
categories:
- devices
item_ids:
- ae2:formation_plane
---

# МЭ плоскость формирования

<GameScene zoom="8" background="transparent">
  <ImportStructure src="../assets/blocks/formation_plane.snbt" />
</GameScene>

Плоскость формирования размещает блоки и выбрасывает предметы. Она работает подобно <ItemLink id="storage_bus" />, допускающей только ввод:
размещает или выбрасывает содержимое, когда его «сохраняют» в ней [устройства](../ae2-mechanics/devices.md), добавляющие ресурсы в [сетевое хранилище](../ae2-mechanics/import-export-storage.md),
например <ItemLink id="import_bus" /> и <ItemLink id="interface" />.

<GameScene zoom="8" interactive={true}>
  <ImportStructure src="../assets/assemblies/formation_plane_demonstration.snbt" />
  <IsometricCamera yaw="255" pitch="30" />
</GameScene>

Эти схемы похожи на связки шина импорта -> шина хранения и интерфейс -> шина хранения в [транспортных подсетях](../example-setups/pipe-subnet.md).

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/import_storage_pipe.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/interface_storage_pipe.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Это [устройство](../ae2-mechanics/devices.md) использует те же механики, что и шины хранения, например в [транспортных подсетях](../example-setups/pipe-subnet.md),
и может заменять их в таких схемах, если вместо переноса предметов нужно выбрасывать их или размещать блоки.

Это [компоненты кабеля](../ae2-mechanics/cable-subparts.md).

**НЕ ЗАБУДЬТЕ РАЗРЕШИТЬ ИСКУССТВЕННЫХ ИГРОКОВ В НАСТРОЙКАХ ПРИВАТА ЧАНКА**

## Фильтрация

По умолчанию плоскость размещает или выбрасывает всё подряд. Предметы в её слотах фильтра задают белый список,
разрешающий размещать только указанные предметы.

Предметы и жидкости можно перетаскивать в слоты из JEI/REI, даже если их нет в наличии.

Щёлкните правой кнопкой мыши с ёмкостью для жидкости (например, ведром или резервуаром), чтобы задать фильтр по жидкости, а не по самому ведру или резервуару.

## Приоритет

Приоритет задаётся нажатием на гаечный ключ в правом верхнем углу интерфейса.
Поступающие в сеть предметы сначала направляются в хранилище с наивысшим приоритетом.

## Настройки

*   Плоскость можно настроить на размещение блоков в мире или выбрасывание предметов

## Улучшения

Плоскость формирования поддерживает следующие [улучшения](upgrade_cards.md):

*   <ItemLink id="capacity_card" /> увеличивает число слотов фильтра
*   <ItemLink id="fuzzy_card" /> позволяет плоскости фильтровать по степени износа и/или игнорировать NBT предметов
*   <ItemLink id="inverter_card" /> переключает фильтр с белого списка на чёрный

## Рецепт

<RecipeFor id="formation_plane" />

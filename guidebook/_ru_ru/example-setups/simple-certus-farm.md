---
navigation:
  parent: example-setups/example-setups-index.md
  title: Простая ферма истинного кварца
  icon: certus_quartz_crystal
  position: 110
---

# Простая ферма истинного кварца

Как сказано в разделе [«Рост истинного кварца»](../ae2-mechanics/certus-growth.md), для автоматического сбора <ItemLink id="certus_quartz_crystal" />
используются <ItemLink id="annihilation_plane" /> и <ItemLink id="storage_bus" />.
<ItemLink id="growth_accelerator" /> значительно ускоряют рост почек истинного кварца, после чего плоскости
разрушают полностью выросший <ItemLink id="quartz_cluster" />. Для фильтрации используется удачная особенность: незрелые
почки истинного кварца оставляют <ItemLink id="certus_quartz_dust" />, а не исчезают бесследно.

Эта ферма полностью автоматическая при использовании <ItemLink id="flawless_budding_quartz" />, но несовершенные, потресканные и повреждённые
цветущие блоки истинного кварца придётся заменять вручную. Либо автоматически, как описано в разделах [«Полуавтоматическая ферма истинного кварца»](semiauto-certus-farm.md)
и [«Усовершенствованная ферма истинного кварца»](advanced-certus-farm.md).

Примерная скорость приведена в разделе [«Рост истинного кварца»](../ae2-mechanics/certus-growth.md).

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/simple_certus_farm.snbt" />

  <BoxAnnotation color="#dddddd" min="3.7 1 1" max="4 2 2">
        (1) МЭ плоскость уничтожения: интерфейса настройки нет, но можно наложить «Удачу».
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="3 1 1" max="3.3 2 2">
        (2) МЭ шина хранения #1: фильтр на кристалл истинного кварца.
        <ItemImage id="certus_quartz_crystal" scale="2" />
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="3 1 .7" max="2 2 1">
        (3) МЭ шина хранения #2: фильтр на кристалл истинного кварца. Приоритет выше, чем у основного хранилища.
        <ItemImage id="certus_quartz_crystal" scale="2" />
  </BoxAnnotation>

<DiamondAnnotation pos="1 0.5 0.5" color="#00ff00">
        К основной сети
    </DiamondAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Настройки

* У первой <ItemLink id="annihilation_plane" /> (1) нет интерфейса и возможности настройки, но можно наложить «Удачу».
* У первой <ItemLink id="storage_bus" /> (2) фильтр на <ItemLink id="certus_quartz_crystal" />.
* У второй <ItemLink id="storage_bus" /> (3) фильтр на <ItemLink id="certus_quartz_crystal" />, а её
  [приоритет](../ae2-mechanics/import-export-storage.md#storage-priority) выше, чем у основного хранилища.

## Принцип работы

1. <ItemLink id="annihilation_plane" /> пытается разрушить блок перед собой, но может разрушить только <ItemLink id="quartz_cluster" />,
   поскольку единственное хранилище в подсети — <ItemLink id="storage_bus" /> с фильтром на <ItemLink id="certus_quartz_crystal" />.
4. Первая <ItemLink id="storage_bus" /> сохраняет кристаллы истинного кварца в бочке.
5. Вторая <ItemLink id="storage_bus" /> даёт основной сети доступ ко всем кристаллам истинного кварца в бочке. У неё
   высокий [приоритет](../ae2-mechanics/import-export-storage.md#storage-priority), чтобы кристаллы истинного кварца в первую очередь
   возвращались в бочку, а не в основное хранилище.

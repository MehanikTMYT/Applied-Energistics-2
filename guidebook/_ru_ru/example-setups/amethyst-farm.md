---
navigation:
  parent: example-setups/example-setups-index.md
  title: Аметистовая ферма
  icon: minecraft:amethyst_shard
---

# Выращивание аметиста

Хотя <ItemLink id="growth_accelerator" /> работает с аметистом, обычные способы фильтрации [почек истинного кварца](../items-blocks-machines/budding_certus.md)
с помощью <ItemLink id="annihilation_plane" /> не подходят для аметистовых почек. Из незрелых почек истинного кварца выпадает
<ItemLink id="certus_quartz_dust" />, а из незрелых аметистовых почек не выпадает ничего, поэтому плоскость уничтожения всегда будет их разрушать:
сеть всегда может сохранить «ничего».

Решение — зачаровать плоскость уничтожения на «Шёлковое касание». Тогда из незрелых аметистовых почек *будут* выпадать предметы
(сами блоки почек на разных стадиях роста), и их можно будет фильтровать.

Затем <ItemLink id="minecraft:amethyst_cluster" /> нужно снова установить с помощью <ItemLink id="formation_plane" />, чтобы
разрушить его ещё раз с помощью <ItemLink id="annihilation_plane" /> без «Шёлкового касания» и получить <ItemLink id="minecraft:amethyst_shard" />.

Учтите: друза имеет направление, поэтому прямо напротив плоскости формирования должна находиться сплошная грань блока.

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/amethyst_farm.snbt" />

  <BoxAnnotation color="#dddddd" min="2.7 1 1" max="3 2 2">
        (1) МЭ плоскость уничтожения #1: интерфейса настройки нет, наложены чары «Шёлковое касание».
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="2 1 1" max="2.3 2 2">
        (2) МЭ плоскость формирования: фильтр настроен на аметистовую друзу.
        <ItemImage id="minecraft:amethyst_cluster" scale="2" />
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="1.3 0.7 1" max="2 1 2">
        (3) МЭ плоскость уничтожения #2: интерфейса настройки нет, но можно зачаровать на «Удачу».
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="1 0 1" max="1.3 1 2">
        (4) МЭ шина хранения #1: фильтр настроен на осколок аметиста.
        <ItemImage id="minecraft:amethyst_shard" scale="2" />
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="0 0 .7" max="1 1 1">
        (5) МЭ шина хранения #2: фильтр настроен на осколок аметиста. Приоритет выше, чем у основного хранилища.
        <ItemImage id="minecraft:amethyst_shard" scale="2" />
  </BoxAnnotation>

<DiamondAnnotation pos="0 0.5 0.5" color="#00ff00">
        К основной сети
    </DiamondAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Настройки

* Первая <ItemLink id="annihilation_plane" /> (1) не имеет интерфейса настройки, но её необходимо зачаровать на «Шёлковое касание».
* <ItemLink id="formation_plane" /> (2) настроена на фильтрацию <ItemLink id="minecraft:amethyst_cluster" />.
* Вторая <ItemLink id="annihilation_plane" /> (3) не имеет интерфейса настройки, но её можно зачаровать на «Удачу».
* Первая <ItemLink id="storage_bus" /> (4) настроена на фильтрацию <ItemLink id="minecraft:amethyst_shard" />.
* Вторая <ItemLink id="storage_bus" /> (5) настроена на фильтрацию <ItemLink id="minecraft:amethyst_shard" />, а её
  [приоритет](../ae2-mechanics/import-export-storage.md#storage-priority) выше, чем у основного хранилища.

## Принцип работы

1. Первая <ItemLink id="annihilation_plane" /> пытается разрушить блок перед собой, но может разрушать только <ItemLink id="minecraft:amethyst_cluster" />,
    поскольку единственное хранилище подсети — <ItemLink id="formation_plane" />, настроенная на фильтрацию аметистовой друзы. Это работает лишь потому, что
плоскость зачарована на «Шёлковое касание»: иначе она могла бы разрушать незрелые почки, ведь из них ничего не выпадает.
2. <ItemLink id="formation_plane" /> устанавливает друзу на блок напротив себя.
3. Вторая <ItemLink id="annihilation_plane" /> разрушает друзу, получая <ItemLink id="minecraft:amethyst_shard" />.
4. Первая <ItemLink id="storage_bus" /> помещает осколки в бочку. Строго говоря, фильтр здесь не нужен, поскольку вторая
плоскость уничтожения должна сталкиваться только с полностью выросшими друзами.
5. Вторая <ItemLink id="storage_bus" /> даёт основной сети доступ ко всем осколкам аметиста в бочке. Ей задан
высокий [приоритет](../ae2-mechanics/import-export-storage.md#storage-priority), чтобы осколки аметиста в первую очередь
возвращались в бочку, а не в основное хранилище.

---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: МЭ плоскость уничтожения
  icon: annihilation_plane
  position: 210
categories:
- devices
item_ids:
- ae2:annihilation_plane
---

# МЭ плоскость уничтожения

<GameScene zoom="8" background="transparent">
<ImportStructure src="../assets/blocks/annihilation_plane.snbt" />
</GameScene>

МЭ плоскость уничтожения разрушает блоки и подбирает предметы. Она работает подобно <ItemLink id="import_bus" />, помещая содержимое
в [сетевое хранилище](../ae2-mechanics/import-export-storage.md). Для подбора предмет должен коснуться
лицевой стороны плоскости: предметы в окружающей области она не собирает.

На плоскости уничтожения можно накладывать любые чары для кирки, так что на несколько плоскостей можно наложить «Удачу» невероятного уровня и
[автоматизировать обработку руд](../example-setups/ore-fortuner.md), если сборка это позволяет. «Шёлковое касание» действует
ожидаемым образом, «Эффективность» снижает расход энергии на разрушение блока, а «Прочность» даёт шанс вообще не потратить энергию.

Это [компоненты кабеля](../ae2-mechanics/cable-subparts.md).

**НЕ ЗАБУДЬТЕ РАЗРЕШИТЬ ИСКУССТВЕННЫХ ИГРОКОВ НА ЗАПРИВАЧЕННЫХ ЧАНКАХ**

## Фильтрация

Плоскость уничтожения разрушит блок или подберёт предмет, только если сможет сохранить полученные ресурсы
в своей сети. Поэтому для фильтрации *необходимо ограничить содержимое, которое может храниться в её сети*, обычно подключив
её к [подсети](../ae2-mechanics/subnetworks.md). Для этого <ItemLink id="storage_bus" /> или [ячейку](../items-blocks-machines/storage_cells.md)
можно [разметить](cell_workbench.md).

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/annihilation_filtering.snbt" />

  <DiamondAnnotation pos="1 0.5 0.5" color="#00ff00">
        Фильтр настроен на ресурсы, выпадающие из нужного блока.
  </DiamondAnnotation>

  <DiamondAnnotation pos=".5 0.5 2.5" color="#00ff00">
        Разметка настроена на ресурсы, выпадающие из нужного блока.
  </DiamondAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Ещё раз: фильтрация идёт *по выпадающим предметам*. Например, чтобы фильтровать разрушение <ItemLink id="minecraft:amethyst_cluster" />,
нужна плоскость с «Шёлковым касанием». Иначе на всех предыдущих стадиях роста ничего не выпадает, и плоскость будет разрушать их
в любом случае, ведь сеть всегда может сохранить «ничего».

## Рецепт

<RecipeFor id="annihilation_plane" />

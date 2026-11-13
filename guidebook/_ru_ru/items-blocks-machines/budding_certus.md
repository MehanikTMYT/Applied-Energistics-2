---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: Цветущий блок истинного кварца
  icon: flawless_budding_quartz
  position: 010
categories:
- misc ingredients blocks
item_ids:
- ae2:flawless_budding_quartz
- ae2:flawed_budding_quartz
- ae2:chipped_budding_quartz
- ae2:damaged_budding_quartz
- ae2:small_quartz_bud
- ae2:medium_quartz_bud
- ae2:large_quartz_bud
- ae2:quartz_cluster
---

# Цветущий блок истинного кварца

(см. также [Выращивание истинного кварца](../ae2-mechanics/certus-growth.md))

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/budding_blocks.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

На цветущих блоках истинного кварца появляются почки, как у аметиста. Эти блоки встречаются в [метеоритах](../ae2-mechanics/meteorites.md).
Есть 4 разновидности цветущих блоков истинного кварца: безупречные, несовершенные, потресканные и поврежденные. Проще всего различать их
с помощью модов HWYLA, Jade, The One Probe и подобных (либо экрана F3).

У несовершенного, потресканного и поврежденного цветущего блока истинного кварца при каждом переходе почки на следующую стадию роста есть шанс
ухудшиться на одну ступень, в итоге превратившись в обычный <ItemLink id="quartz_block" />.

Безупречный цветущий блок истинного кварца не ухудшается при росте почек и служит бесконечным источником кварца.

При разрушении обычной киркой цветущий блок истинного кварца ухудшается на 1 ступень. Если кирка
зачарована на «Шёлковое касание», ухудшения не будет, кроме случая с безупречным блоком. **Это означает, что безупречные цветущие блоки истинного кварца нельзя
подобрать и перенести киркой**. Вместо этого [пространственное хранилище](../ae2-mechanics/spatial-io.md) позволяет
вырезать и вставлять участки с безупречными цветущими блоками.

## Рецепты

Несовершенные, потресканные и поврежденные цветущие блоки истинного кварца можно получить, бросив цветущий блок предыдущей ступени (или <ItemLink id="quartz_block" />)
в воду вместе с одним или несколькими <ItemLink id="charged_certus_quartz_crystal" />.

Безупречный цветущий блок истинного кварца нельзя создать: его можно только найти в мире.

<Row>
  <RecipeFor id="damaged_budding_quartz" />

  <RecipeFor id="chipped_budding_quartz" />

  <RecipeFor id="flawed_budding_quartz" />
</Row>

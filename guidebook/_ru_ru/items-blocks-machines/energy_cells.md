---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: Энергохранилища
  icon: energy_cell
  position: 110
categories:
- network infrastructure
item_ids:
- ae2:energy_cell
- ae2:dense_energy_cell
- ae2:creative_energy_cell
---

# Энергохранилища

<Row gap="20">
  <BlockImage id="energy_cell" scale="8" p:fullness="4" />

  <BlockImage id="dense_energy_cell" scale="8" p:fullness="4" />

  <BlockImage id="creative_energy_cell" scale="8" />
</Row>

Энергохранилища увеличивают запас [энергии](../ae2-mechanics/energy.md) в сети. Небольшой буфер сглаживает
скачки потребления при загрузке или извлечении большого числа предметов, а значительный запас энергии
позволяет сети работать без текущей выработки (например, ночью при питании от солнечных панелей) или выдерживать огромный мгновенный
расход энергии [пространственного хранилища](../ae2-mechanics/spatial-io.md).

## Полосы заполнения

<Row>
<BlockImage id="energy_cell" scale="4" p:fullness="0" />
<BlockImage id="energy_cell" scale="4" p:fullness="1" />
<BlockImage id="energy_cell" scale="4" p:fullness="2" />
<BlockImage id="energy_cell" scale="4" p:fullness="3" />
<BlockImage id="energy_cell" scale="4" p:fullness="4" />
</Row>

Полосы на боковой стороне энергохранилища показывают количество энергии в нём.

*   0 при заряде ниже 25%
*   1 при заряде от 25% до 50%
*   2 при заряде от 50% до 75%
*   3 при заряде от 75% до 99%
*   4 при заряде выше 99%

## Типы энергохранилищ

*   <ItemLink id="energy_cell" /> хранит 200k AE. Одного обычно достаточно для большинства задач: оно легко справляется со скачками потребления
    при обычной работе сети.
*   <ItemLink id="dense_energy_cell" /> хранит 1.6M AE и предназначено для работы сети от накопленной энергии либо
    для огромного мгновенного расхода энергии крупных [пространственных хранилищ](../ae2-mechanics/spatial-io.md).
*   <ItemLink id="creative_energy_cell" /> — предмет творческого режима для испытаний, дающий БЕСКОНЕЧНУЮ ЭНЕРГИЮУУУ или что-то вроде того.

## Рецепты

<Row>
  <RecipeFor id="energy_cell" />

  <RecipeFor id="dense_energy_cell" />
</Row>

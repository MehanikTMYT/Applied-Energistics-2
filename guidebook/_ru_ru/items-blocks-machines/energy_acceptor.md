---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: Приёмщик энергии
  icon: energy_acceptor
  position: 110
categories:
- network infrastructure
item_ids:
- ae2:energy_acceptor
---

# Приёмщик энергии

<Row gap="20">
<BlockImage id="energy_acceptor" scale="8" /> 

<GameScene zoom="8" background="transparent">
  <ImportStructure src="../assets/blocks/cable_energy_acceptor.snbt" />
</GameScene>
</Row>

Приёмщик энергии преобразует распространённые виды энергии из других технических модов во внутреннюю [энергию](../ae2-mechanics/energy.md) AE2 —
AE. Хотя <ItemLink id="controller" /> тоже умеет это делать, его грани ценны для других подключений, поэтому часто лучше использовать
приёмщик энергии.

Коэффициенты преобразования Forge Energy и Techreborn Energy:

*   2 FE = 1 AE (Forge)
*   1 E  = 2 AE (Fabric)

Скорость преобразования полностью зависит от того, сколько AE может хранить сеть. Причины объясняются
[на этой странице](../ae2-mechanics/energy.md).

## Варианты

Приёмщик энергии доступен в 2 вариантах: обычном и плоском — [компоненте кабеля](../ae2-mechanics/cable-subparts.md). Это позволяет делать некоторые конструкции компактнее.

Обычный и плоский приёмщики энергии можно превращать друг в друга в сетке изготовления.

## Рецепт

<RecipeFor id="energy_acceptor" />

<RecipeFor id="cable_energy_acceptor" />
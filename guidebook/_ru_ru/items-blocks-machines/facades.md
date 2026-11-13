---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: Кабельные фасады
  icon: facade
  icon_components:
    "ae2:facade_item": "minecraft:stone"
  position: 110
categories:
- network infrastructure
item_ids:
- ae2:facade
---

# Кабельные фасады

Фасады позволяют придать базе более аккуратный вид.
Они закрывают кабели обоих размеров и изготавливаются из множества разных блоков.

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/facades_1.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Хотя фасады могут закрывать кабель со всех сторон, [компоненты кабеля](../ae2-mechanics/cable-subparts.md) и кабельные соединения
могут выступать сквозь них.

<GameScene zoom="6"  interactive={true}>
  <ImportStructure src="../assets/assemblies/facades_2.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Используйте их изобретательно, чтобы улучшить оформление базы или сделать блоки с разными текстурами на каждой стороне.

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../assets/assemblies/facades_3.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Скрытие фасадов

Фасады скрываются, пока в любой руке находится <ItemLink id="network_tool" />.

С блоками за скрытыми фасадами можно взаимодействовать, не снимая фасады.

## Рецепт

Чтобы сделать фасад с текстурой блока, поместите этот блок в центре 4 <ItemLink id="cable_anchor" />.

![Рецепт фасада](../assets/diagrams/facade_recipe.png)
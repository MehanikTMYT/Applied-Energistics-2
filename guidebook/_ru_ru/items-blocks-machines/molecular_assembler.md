---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: Молекулярный сборщик
  icon: molecular_assembler
  position: 310
categories:
- machines
item_ids:
- ae2:molecular_assembler
---

# Молекулярный сборщик

<BlockImage id="molecular_assembler" scale="8" />

Молекулярный сборщик принимает предметы и выполняет операцию, заданную соседним <ItemLink id="pattern_provider" />,
либо установленным <ItemLink id="crafting_pattern" />, <ItemLink id="smithing_table_pattern" /> или <ItemLink id="stonecutting_pattern" />,
после чего выталкивает результат в соседние инвентари.

В этом сборщике установлен шаблон изготовления с рецептом 1 дубовое бревно = 4 дубовые доски. Когда дубовые брёвна поступают в верхнюю воронку,
сборщик изготавливает дубовые доски и выгружает их в нижнюю воронку.

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/standalone_assembler.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Основное применение молекулярного сборщика

Однако основное применение — рядом с <ItemLink id="pattern_provider" />. В этом случае поставщики шаблонов действуют особым образом:
вместе с ингредиентами они передают соседним сборщикам данные нужного шаблона. Поскольку сборщики автоматически выгружают результаты
изготовления в соседние инвентари (в том числе в возвратные слоты поставщика шаблонов), сборщика рядом с поставщиком
достаточно для автоматизации шаблонов изготовления.

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/assembler_tower.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Улучшения

Молекулярный сборщик поддерживает следующие [улучшения](upgrade_cards.md):

*   <ItemLink id="speed_card" />

## Рецепт

<RecipeFor id="molecular_assembler" />

## Примечание

Optifine нарушает функцию «выталкивания в соседние инвентари», поэтому большинство схем изготовления со сборщиками не будет работать.
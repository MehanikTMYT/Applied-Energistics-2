---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: Ускоритель роста
  icon: growth_accelerator
  position: 310
categories:
- machines
item_ids:
- ae2:growth_accelerator
---

# Ускоритель роста

<BlockImage id="growth_accelerator" p:powered="true" scale="8"/>

Ускоритель роста значительно ускоряет [рост](../ae2-mechanics/certus-growth.md) истинного кварца или аметиста, если установлен рядом с цветущим блоком.

Любопытно, что он *также* ускоряет рост различных растений.

Для этого он вызывает «случайные такты» соседних блоков в дополнение к случайным тактам, происходящим естественным образом.
Теоретически  1 ускоритель должен обеспечивать ускорение роста ~90x, а эффекты нескольких ускорителей складываются.

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/growth_accelerator.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Энергию можно подавать сверху или снизу через [кабели](cables.md) AE2 или энергетические кабели других модов. Ускоритель
принимает как энергию AE2 (AE), так и Forge Energy (FE).

Для ручного питания установите <ItemLink id="crank" /> сверху или снизу и щёлкайте по нему правой кнопкой мыши.

Верхнюю и нижнюю стороны можно узнать по розовым флюисовым деталям.

<GameScene zoom="6" background="transparent">
<ImportStructure src="../assets/assemblies/accelerator_connections.snbt" />
<IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Рецепт

<RecipeFor id="growth_accelerator" />

---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: Квантовый сетевой мост
  icon: quantum_ring
  position: 110
categories:
- network infrastructure
item_ids:
- ae2:quantum_link
- ae2:quantum_ring
---

# Квантовый сетевой мост

![Собранный квантовый сетевой мост](../assets/diagrams/quantum_bridge_demonstration.png)

Квантовые сетевые мосты могут расширять [сеть](../ae2-mechanics/me-network-connections.md) на неограниченные расстояния и даже между измерениями.
Всего они передают 32 канала (независимо от подключения кабелей к каждой из сторон), по сути
работая как беспроводной [непрозрачный кабель](cables.md#dense-cable).

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/quantum_bridge_internal_structure_1.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/quantum_bridge_internal_structure_2.snbt" />

  <BoxAnnotation color="#33dd33" min="1 1 1" max="6 2 3">
        Воображаемый кабель между двумя конечными точками
  </BoxAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Учтите: **обе стороны должны быть загружены игрой**, поэтому нужен <ItemLink id="spatial_anchor" /> или другой загрузчик чанков,
если эти 2 стороны находятся далеко друг от друга.

# МЭ квантовое кольцо

<BlockImage id="quantum_ring" scale="8" />

Восемь таких блоков, размещённых вокруг <ItemLink id="quantum_link" />, создают
квантовый сетевой мост. Только 4 блока <ItemLink id="quantum_ring" />, непосредственно соседствующие с
<ItemLink id="quantum_link" />, принимают сетевые подключения;
4 угловых блока не могут подключаться к кабелям.

## Рецепт

<RecipeFor id="quantum_ring" />

# МЭ камера квантовой связи

<BlockImage id="quantum_link" scale="8" />

Один такой блок, окружённый блоками <ItemLink id="quantum_ring" />,
создаёт квантовый сетевой мост. Этот блок не подключается к кабелям и считается
частью сети только после полной сборки моста.

Инвентарь этого блока вмещает только одну <ItemLink id="quantum_entangled_singularity" /> и
доступен для автоматизации.

## Рецепт

<RecipeFor id="quantum_link" />

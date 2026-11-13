---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: МЭ шина переключения
  icon: toggle_bus
  position: 110
categories:
- network infrastructure
item_ids:
- ae2:toggle_bus
- ae2:inverted_toggle_bus
---

# МЭ шина переключения

<GameScene zoom="8" background="transparent">
<ImportStructure src="../assets/assemblies/toggle_bus.snbt" />
<IsometricCamera yaw="195" pitch="30" />
</GameScene>

Шина, работающая подобно <ItemLink id="fluix_glass_cable" /> или другим кабелям, но
позволяющая переключать состояние соединения сигналом редстоуна. Это даёт возможность
отключать часть [МЭ-сети](../ae2-mechanics/me-network-connections.md).

При подаче сигнала редстоуна эта часть кабеля включает соединение, а <ItemLink id="inverted_toggle_bus" /> работает наоборот:
отключает соединение.

Учтите, что переключение этих шин может вызвать перезапуск сети и повторный расчёт подключённых устройств.

Это [части кабеля](../ae2-mechanics/cable-subparts.md).

## Рецепты

<RecipeFor id="toggle_bus" />

<RecipeFor id="inverted_toggle_bus" />

---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: МЭ контроллер
  icon: controller
  position: 110
categories:
- network infrastructure
item_ids:
- ae2:controller
---

# МЭ контроллер

<BlockImage id="controller" p:state="online" scale="8" />

Контроллер — центр маршрутизации [МЭ-сети](../ae2-mechanics/me-network-connections.md).
Без него сеть работает в режиме «ad-hoc» и допускает не более 8 [устройств](../ae2-mechanics/devices.md), использующих каналы, во всей сети.

В одной [МЭ-сети](../ae2-mechanics/me-network-connections.md) не может быть 2 контроллеров.

Контроллер предоставляет по 32 [канала](../ae2-mechanics/channels.md) на каждой грани.

Для работы контроллера требуется 6 AE/t на каждый блок
контроллера. Каждый блок хранит 8000 AE, поэтому крупным сетям может потребоваться дополнительный
запас энергии. Подробнее см. [Энергия](../ae2-mechanics/energy.md).

Многоблочные контроллеры допускают довольно свободную форму.

<GameScene zoom="2" background="transparent">
  <ImportStructure src="../assets/assemblies/controllers.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Однако необходимо соблюдать несколько правил:

1.  Все блоки контроллера в [МЭ-сети](../ae2-mechanics/me-network-connections.md) должны соединяться друг с другом, иначе они станут красными.
2.  Размер контроллера должен укладываться в 7x7x7, иначе он станет красным.
3.  У блока контроллера может быть по 2 соседних блока максимум вдоль 1 оси. Нарушивший это правило блок отключится и станет красным.

<GameScene zoom="2" background="transparent">
  <ImportStructure src="../assets/assemblies/controller_rules.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Если все правила соблюдены и подано питание, контроллер будет светиться
и менять цвета.

Щелчок правой кнопкой по контроллеру открывает тот же интерфейс, что и <ItemLink id="network_tool" />.

## Рецепт

<RecipeFor id="controller" />

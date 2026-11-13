---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: P2P-тоннели
  icon: me_p2p_tunnel
  position: 210
categories:
- devices
item_ids:
- ae2:me_p2p_tunnel
- ae2:redstone_p2p_tunnel
- ae2:item_p2p_tunnel
- ae2:fluid_p2p_tunnel
- ae2:fe_p2p_tunnel
- ae2:light_p2p_tunnel
---

# Тоннели «точка-точка»

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/p2p_tunnels.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

P2P-тоннели позволяют перемещать предметы, жидкости, сигналы редстоуна, энергию, свет и [каналы](../ae2-mechanics/channels.md)
по сети без прямого взаимодействия с самой сетью. Существует множество вариантов P2P-тоннелей, но каждый
передаёт только свой тип ресурсов. По сути, они действуют как порталы, напрямую соединяющие
две грани блоков на расстоянии. Они не двунаправленные: у них есть определённые входы и выходы.

![Портал](../assets/assemblies/p2p_portal.png)

Например, воронка, направленная в предметный P2P-тоннель, будет вести себя так, словно подключена прямо к бочке, и предметы начнут перемещаться.

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/p2p_hopper_barrel.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Однако две бочки рядом друг с другом не будут передавать предметы между собой.

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/p2p_barrel_barrel.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Есть и другие варианты, например редстоуновый P2P-тоннель.

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/p2p_redstone.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

А также МЭ P2P-тоннель, передающий каналы.

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/p2p_channels.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Типы P2P-тоннелей и настройка типа

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/p2p_tunnels.snbt" />
  <IsometricCamera yaw="180" pitch="90" />
</GameScene>

Существует множество типов P2P-тоннелей. Непосредственно изготовить можно только МЭ P2P-тоннель. Остальные получают щелчком правой кнопки мыши по другим
P2P-тоннелям с определёнными предметами:
- МЭ P2P-тоннель выбирается щелчком правой кнопки мыши с любым [кабелем](../items-blocks-machines/cables.md).
- Редстоуновый P2P-тоннель выбирается щелчком правой кнопки мыши с различными редстоуновыми компонентами.
- Предметный P2P-тоннель выбирается щелчком правой кнопки мыши с сундуком или воронкой.
- Жидкостный P2P-тоннель выбирается щелчком правой кнопки мыши с ведром или бутылочкой.
- Энергетический P2P-тоннель выбирается щелчком правой кнопки мыши почти с любым предметом, содержащим энергию.
- Световой P2P-тоннель выбирается щелчком правой кнопки мыши с факелом или светокамнем

У некоторых типов тоннелей есть особенности. Например, каналы МЭ P2P-тоннелей не могут проходить через другие МЭ P2P-тоннели, а
энергетические P2P-тоннели косвенно взимают плату в размере 2.5% от проходящей через них FE, увеличивая собственное
потребление [энергии](../ae2-mechanics/energy.md).

## Самое распространённое применение P2P

Чаще всего P2P-тоннели применяют в виде МЭ P2P-тоннелей для более компактной передачи [каналов](../ae2-mechanics/channels.md).
Вместо пучка непрозрачных кабелей множество каналов можно провести по одному непрозрачному кабелю.

В этом примере 8 входов МЭ P2P принимают 256 каналов (8*32) от <ItemLink id="controller" /> основной сети, а 8 выходов МЭ P2P
выдают их в другом месте. Обратите внимание: каждый вход или выход P2P-тоннеля занимает 1 канал. Так множество каналов можно провести
через тонкий кабель. А поскольку P2P-тоннели находятся в отдельной [подсети](../ae2-mechanics/subnetworks.md), для этого даже
не расходуются каналы основной сети! Также обратите внимание: P2P-тоннели можно устанавливать непосредственно
на контроллер, но между ними можно поставить [непрозрачный умный кабель](../items-blocks-machines/cables.md#smart-cable), чтобы нагляднее видеть каналы.

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../assets/assemblies/p2p_compact_channels.snbt" />

  <BoxAnnotation color="#dddddd" min="1.3 1.3 6.3" max="2 2.7 6.7">
        Кварцевое волокно передаёт энергию между основной сетью и подсетью P2P.
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="4.1 0 5.7" max="5 2.3 6.4">
        Вход тоннеля можно установить прямо на контроллер или подключить к нему кабелем.
  </BoxAnnotation>

  <IsometricCamera yaw="225" pitch="30" />
</GameScene>

Ещё один пример (в том числе использования с [квантовыми мостами](quantum_bridge.md)) показан на этой схеме в MS Paint, которую мне лень
дорабатывать:

![P2P и квантовые мосты](../assets/diagrams/p2p_quantum_network.png)

## Вложенность

Однако таким способом нельзя передать бесконечное число каналов по одному кабелю. Канал МЭ P2P-тоннеля не
проходит через другой МЭ P2P-тоннель, поэтому рекурсивная вложенность невозможна. Обратите внимание: внешний слой МЭ P2P-тоннелей
на красных кабелях отключён. Это касается только МЭ P2P-тоннелей; другие типы P2P-тоннелей могут проходить через МЭ P2P-тоннель,
что видно по исправно работающим редстоуновым P2P-тоннелям.

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/p2p_nesting.snbt" />
  <IsometricCamera yaw="225" pitch="30" />
</GameScene>

## Связывание

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/p2p_linking_frequency.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Концы соединения P2P-тоннеля можно связать с помощью <ItemLink id="memory_card" />. Частота отображается
в виде сетки цветов 2x2 на задней стороне тоннеля.
- Щёлкните правой кнопкой мыши с зажатой Shift, чтобы создать новую частоту связи P2P.
- Щёлкните правой кнопкой мыши, чтобы вставить настройки, карты улучшений или частоту связи.

Тоннель, по которому щёлкнули правой кнопкой мыши с зажатой Shift, станет входом, а тоннель, по которому щёлкнули без Shift, — выходом. Выходов может быть несколько,
но у МЭ P2P-тоннелей входящие каналы распределяются между выходами, поэтому дублировать каналы нельзя.

## Рецепт

<RecipeFor id="me_p2p_tunnel" />
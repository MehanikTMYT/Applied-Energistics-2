---
navigation:
  parent: ae2-mechanics/ae2-mechanics-index.md
  title: Сетевые соединения
  icon: fluix_glass_cable
---

# Сетевые соединения

## Что означает «сеть»?

«Сеть» — это группа [устройств](../ae2-mechanics/devices.md), соединённых блоками, способными передавать [каналы](../ae2-mechanics/channels.md),
например [кабелями](../items-blocks-machines/cables.md), полноблочными машинами и [устройствами](../ae2-mechanics/devices.md). 
(<ItemLink id="charger" />, <ItemLink id="interface" />, <ItemLink id="drive" /> и т. д.)
Строго говоря, даже один кабель уже является сетью.

## О расположении устройств

Для [устройств](../ae2-mechanics/devices.md), выполняющих определённую сетевую функцию (например, <ItemLink id="interface" />,
отправляющий содержимое в [сетевое хранилище](../ae2-mechanics/import-export-storage.md) и извлекающий его оттуда, <ItemLink id="level_emitter" />,
считывающий содержимое сетевого хранилища, <ItemLink id="drive" />, предоставляющий сетевое хранилище, и т. д.),
физическое расположение не имеет значения.

Ещё раз: **физическое расположение устройства не имеет значения**. Важно лишь, подключено ли устройство к сети
(и, разумеется, к какой именно сети оно подключено).

## Сетевые соединения

Простой способ определить, что подключено к сети, — использовать <ItemLink id="network_tool" />. Он покажет все
компоненты сети, поэтому наличие лишних или отсутствие нужных устройств указывает на проблему.

Например, здесь 2 отдельные сети.

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/2_networks_1.snbt" />

  <BoxAnnotation color="#915dcd" min="0 0 0" max="1 2 2">
        Сеть 1
  </BoxAnnotation>

<BoxAnnotation color="#5CA7CD" min="2 0 0" max="3 2 2">
        Сеть 2
  </BoxAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Здесь тоже 2 отдельные сети, потому что <ItemLink id="quartz_fiber" /> передаёт [энергию](../ae2-mechanics/energy.md),
не создавая сетевого соединения.

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/2_networks_2.snbt" />

  <BoxAnnotation color="#915dcd" min="0 0 0" max="1 2 2">
        Сеть 1
  </BoxAnnotation>

  <BoxAnnotation color="#5CA7CD" min="1.3 0 0" max="3 2 2">
        Сеть 2
  </BoxAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

А здесь только 1 сеть, а не 2 отдельные. [Квантовый мост](../items-blocks-machines/quantum_bridge.md) действует как
беспроводной [непрозрачный кабель](../items-blocks-machines/cables.md#dense-cable), поэтому оба конца находятся в одной сети.

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/actually_1_network.snbt" />

  <BoxAnnotation color="#915dcd" min="0 0 0" max="7 3 3">
        Всё это 1 сеть
  </BoxAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Здесь тоже только 1 сеть: цвет [кабеля](../items-blocks-machines/cables.md) не влияет на сетевые соединения, за исключением того, что кабели разных цветов не
соединяются друг с другом. Кабели всех цветов соединяются с флюисовыми (или «неокрашенными») кабелями.

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/actually_1_network_2.snbt" />

  <BoxAnnotation color="#915dcd" min="0 0 0" max="4 2 2">
        Всё это 1 сеть
  </BoxAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Соединения при работе с подсетями

[Подсети](../ae2-mechanics/subnetworks.md) используют сетевые соединения (а точнее, **ОТСУТСТВИЕ** соединения),
чтобы ограничивать доступ [устройств](../ae2-mechanics/devices.md) к другим устройствам.

По сути, подсеть — это просто отдельная сеть.

Рассмотрим, например, [автоматическую добычу руды с «Удачей»](../example-setups/ore-fortuner.md). Здесь 3 отдельные сети,
каждая из которых выполняет определённую задачу в системе.

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/ore_fortuner.snbt" />

  <BoxAnnotation color="#915dcd" min="0 0 2" max="3 1 3">
        Сеть 1: Работает как транспортная подсеть. Ограничивает доступ шины импорта, чтобы та «сохраняла» блоки руды через
        плоскости формирования.
  </BoxAnnotation>

  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 1 1">
        Сеть 2: Работает как ещё одна транспортная подсеть. Ограничивает доступ плоскостей уничтожения, чтобы те помещали
        добытое рудное сырьё в бочку, а не в основную сеть. Кроме того, так они не занимают каналы
        основной сети.
  </BoxAnnotation>

  <BoxAnnotation color="#82CD5C" min="2 0 1" max="4 1 2">
        Сеть 3: Основная сеть со всем хранилищем и системой создания. Здесь она нужна лишь для подачи энергии и специально
        *не* соединена с 2 подсетями.
  </BoxAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Соединения при работе с P2P

Один из вариантов [P2P-тоннеля](../items-blocks-machines/p2p_tunnels.md) передаёт [каналы](channels.md) вместо предметов, жидкостей
или сигнала красного камня, и это порой вызывает путаницу. Сеть, к которой прикреплён тоннель, никак не связана с
сетью, которую он передаёт. Это *может* быть одна и та же сеть, но не обязательно, и обычно это разные сети.

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/p2p_channels_network_connection.snbt" />

  <BoxAnnotation color="#915dcd" min="0 0 0" max="1.98 2 1">
        Сеть 1: Передаваемая сеть (обычно основная)
  </BoxAnnotation>

  <BoxAnnotation color="#5CA7CD" min="2.02 0 0" max="3.98 1 1">
        Сеть 2: Сеть, обеспечивающая работу МЭ P2P-тоннелей (обычно *не* основная)
  </BoxAnnotation>

  <BoxAnnotation color="#915dcd" min="4.02 0 0" max="6 1 1">
        Сеть 1: Передаваемая сеть (обычно основная)
  </BoxAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Менее очевидные соединения

В этом примере всего 1 сеть, потому что <ItemLink id="pattern_provider" />, будучи полноблочным устройством, действует как
кабель, а <ItemLink id="inscriber" /> ведёт себя аналогично. Таким образом, сетевое соединение проходит через
поставщик и высекатель.

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/pattern_provider_network_connection_1.snbt" />

  <BoxAnnotation color="#915dcd" min="0 0 0" max="4 2 2">
        Всё это 1 сеть
  </BoxAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Чтобы этого избежать (что полезно во многих системах автокрафта с [подсетями](../ae2-mechanics/subnetworks.md)),
можно щёлкнуть по поставщику правой кнопкой мыши, держа <ItemLink id="certus_quartz_wrench" />, и сделать его направленным. В таком режиме он
не пропускает каналы через одну из сторон.

<Row gap="40">
<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/pattern_provider_network_connection_2.snbt" />

  <BoxAnnotation color="#915dcd" min="0 0 0" max="1.98 2 2">
        Сеть 1
  </BoxAnnotation>

  <BoxAnnotation color="#5CA7CD" min="2.02 0 0" max="4 2 2">
        Сеть 2
  </BoxAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/pattern_provider_directional_connection.snbt" />

  <BoxAnnotation color="#ee3333" min="1 .3 .3" max="1.3 .7 .7">
        Обратите внимание: кабель не соединяется
  </BoxAnnotation>

  <IsometricCamera yaw="255" pitch="30" />
</GameScene>
</Row>

К другим компонентам, не создающим сетевого соединения в направлении своей рабочей стороны, относится большинство [кабельных](../ae2-mechanics/cable-subparts.md)
[устройств](../ae2-mechanics/devices.md), например <ItemLink id="import_bus" />, <ItemLink id="storage_bus" /> и
<ItemLink id="cable_interface" />.

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/subpart_no_connection.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>
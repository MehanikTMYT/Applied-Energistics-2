---
navigation:
  parent: ae2-mechanics/ae2-mechanics-index.md
  title: Каналы
  icon: controller
---

# Каналы

В Applied Energistics 2 [МЭ-сетям](me-network-connections.md) нужны
каналы для работы [устройств](../ae2-mechanics/devices.md), использующих сетевое хранилище или другие сетевые
службы. Каналы можно представить как USB-кабели к устройствам. У компьютера ограниченное число USB-портов, поэтому он поддерживает
лишь определённое число подключённых устройств. Большинство механизмов, полноразмерных устройств и обычных кабелей пропускают
до 8 каналов. Полноразмерные устройства и обычные кабели можно представить как связку из 8 «проводов каналов». Однако [непрозрачные кабели](../items-blocks-machines/cables.md#dense-cable) поддерживают
до 32 каналов. Единственные другие устройства, способные передавать 32 канала, — <ItemLink id="me_p2p_tunnel" />
и [квантовый сетевой мост](../items-blocks-machines/quantum_bridge.md). Когда устройство занимает канал, представьте, что из связки отводится один USB-«провод»,
который, очевидно, уже недоступен дальше по линии.

<GameScene zoom="7" interactive={true}>
  <ImportStructure src="../assets/assemblies/channel_demonstration_1.snbt" />

  <LineAnnotation color="#33ff33" from="1 .4 .7" to="2.4 .4 .7" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="1 .6 .7" to="2.4 .6 .7" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="1 .4 .6" to="2.6 .4 .6" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="1 .6 .6" to="2.6 .6 .6" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="1 .6 .6" to="2.6 .6 .6" alwaysOnTop={true}/>

  <LineAnnotation color="#33ff33" from="2.4 .6 .7" to="2.4 .6 1.5" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="2.4 .4 .7" to="2.4 .4 1.5" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="2.6 .6 .6" to="2.6 .6 1.5" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="2.6 .4 .6" to="2.6 .4 1.5" alwaysOnTop={true}/>

  <LineAnnotation color="#33ff33" from="2.1 .6 1.5" to="2.4 .6 1.5" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="2.6 .4 1.5" to="2.9 .4 1.5" alwaysOnTop={true}/>

  <LineAnnotation color="#33ff33" from="2.6 .6 1.5" to="2.6 .9 1.5" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="2.4 .1 1.5" to="2.4 .4 1.5" alwaysOnTop={true}/>

  <LineAnnotation color="#33ff33" from="1 .6 .4" to="3.5 .6 .4" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="1 .4 .4" to="3.5 .4 .4" alwaysOnTop={true}/>

  <LineAnnotation color="#33ff33" from="3.5 .6 .4" to="3.5 .9 .4" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="3.5 .1 .4" to="3.5 .4 .4" alwaysOnTop={true}/>

  <LineAnnotation color="#33ff33" from="1 .6 .3" to="1.5 .6 .3" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="1 .4 .3" to="1.5 .4 .3" alwaysOnTop={true}/>

  <LineAnnotation color="#33ff33" from="1.5 .6 .3" to="1.5 .9 .3" alwaysOnTop={true}/>
  <LineAnnotation color="#33ff33" from="1.5 .1 .3" to="1.5 .4 .3" alwaysOnTop={true}/>

  <LineAnnotation color="#ff3333" from="3.5 .5 .5" to="5.5 .5 .5" alwaysOnTop={true}>
  Все 8 каналов кабеля заняты, поэтому накопителю канал не достаётся.  
  </LineAnnotation>

  <LineAnnotation color="#993333" from="1 .5 .5" to="1.25 .5 .5" alwaysOnTop={true}/>
  <LineAnnotation color="#993333" from="1.5 .5 .5" to="1.75 .5 .5" alwaysOnTop={true}/>
  <LineAnnotation color="#993333" from="2 .5 .5" to="2.25 .5 .5" alwaysOnTop={true}/>
  <LineAnnotation color="#993333" from="2.5 .5 .5" to="2.75 .5 .5" alwaysOnTop={true}/>
  <LineAnnotation color="#993333" from="3 .5 .5" to="3.25 .5 .5" alwaysOnTop={true}/>

  <DiamondAnnotation pos="3.6 0.5 0.5" color="#ff0000">
        Все 8 каналов кабеля заняты, поэтому накопителю канал не достаётся.
    </DiamondAnnotation>

  <IsometricCamera yaw="15" pitch="30" />
</GameScene>

Простой способ увидеть использование и маршруты каналов в сети — [умные кабели](../items-blocks-machines/cables.md), которые отображают пути прохождения и занятость каналов.

Каналы потребляют 1⁄128 AE/t на каждый узел, через который проходят. Поэтому
при добавлении <ItemLink id="controller" /> в
сеть с 8 устройствами и более чем 96 узлами потребление энергии может
даже снизиться, поскольку меняется способ распределения каналов.

Обратите внимание: **КАНАЛЫ НИКАК НЕ СВЯЗАНЫ С ЦВЕТОМ КАБЕЛЯ**. Цвет лишь предотвращает соединение кабелей.

## Маршрутизация каналов

При использовании <ItemLink id="controller" />
маршрутизация каналов проходит в 3 этапа. Сначала они идут кратчайшим путём через соседние механизмы к ближайшему [обычному кабелю](../items-blocks-machines/cables.md)
(стеклянному, покрытому или умному). Затем — кратчайшим путём по обычному кабелю к ближайшему [непрозрачному кабелю](../items-blocks-machines/cables.md)
(непрозрачному или непрозрачному умному). Затем — кратчайшим путём по непрозрачному кабелю к <ItemLink id="controller" />.
Если кратчайший путь уже полностью занят, некоторые [устройства](devices.md) могут остаться без необходимых каналов. Используйте
цветные кабели, кабельные якоря и тоннели, чтобы направить каналы по нужному пути.

Например, здесь некоторым накопителям не достаётся каналов: хотя общей пропускной способности кабелей достаточно,
каналы пытаются пройти кратчайшим путём, перегружая одни кабели и оставляя другие пустыми.

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../assets/assemblies/channel_path_length_issue.snbt" />

  <LineAnnotation color="#33ff33" from="3 .5 1.4" to="0.4 0.5 1.4" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#33ff33" from="0.4 .5 1.4" to="0.4 0.5 3.6" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#33ff33" from="0.4 0.5 3.6" to="1.4 0.5 3.6" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#33ff33" from="1.4 0.5 3.6" to="1.4 0.5 5" alwaysOnTop={true} thickness="0.05"/>

  <LineAnnotation color="#33ff33" from="3 0.5 3.6" to="1.6 0.5 3.6" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#33ff33" from="1.6 0.5 3.6" to="1.6 0.5 5" alwaysOnTop={true} thickness="0.05"/>

  <LineAnnotation color="#ff3333" from="3 .5 1.6" to="0.6 .5 1.6" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#ff3333" from="0.6 .5 1.6" to="0.6 .5 3.4" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#ff3333" from="0.6 .5 3.4" to="1.4 .5 3.4" alwaysOnTop={true} thickness="0.05"/>

  <LineAnnotation color="#ff3333" from="3 .5 3.4" to="1.6 .5 3.4" alwaysOnTop={true} thickness="0.05"/>

  <BoxAnnotation color="#dddddd" min="1.2 0.2 3.2" max="1.8 0.8 3.8" alwaysOnTop={true} thickness="0.05">
        Здесь пытаются пройти более 8 каналов, поэтому часть из них обрывается.
  </BoxAnnotation>

  <IsometricCamera yaw="90" pitch="90" />

</GameScene>

Это исправляется более строгим ограничением возможных путей каналов. Сеть должна иметь форму дерева (или куста).
Следует свести к минимуму петли и неоднозначные пути каналов.

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../assets/assemblies/channel_path_length_issue_fix.snbt" />

  <LineAnnotation color="#33ff33" from="3 .5 1.4" to="0.4 0.5 1.4" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#33ff33" from="0.4 .5 1.4" to="0.4 0.5 5.6" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#33ff33" from="0.4 0.5 5.6" to="1 0.5 5.6" alwaysOnTop={true} thickness="0.05"/>

  <LineAnnotation color="#33ff33" from="3 0.5 3.6" to="1.6 0.5 3.6" alwaysOnTop={true} thickness="0.05"/>
  <LineAnnotation color="#33ff33" from="1.6 0.5 3.6" to="1.6 0.5 5" alwaysOnTop={true} thickness="0.05"/>

  <IsometricCamera yaw="90" pitch="90" />

</GameScene>

## Сети без контроллера

Сеть без <ItemLink id="controller" />
считается сетью без контроллера (Ad-Hoc) и поддерживает до 8 устройств, использующих каналы.
При превышении 8 устройств все устройства сети, использующие каналы, отключаются.
Можно либо убрать часть устройств, либо добавить <ItemLink id="controller" />.

В отличие от сетей с контроллером, [умные кабели](../items-blocks-machines/cables.md) в сетях без контроллера показывают число
каналов, занятых во всей сети, а не число каналов, проходящих через конкретный кабель.

В сетях без контроллера каждое устройство
занимает 1 канал во всей сети. Это сильно отличается от распределения каналов через <ItemLink id="controller" /> на основе
кратчайшего пути.

## Проектирование

Как уже говорилось в разделе [маршрутизации каналов](channels.md#channel-routing), лучше строить сеть в форме дерева: непрозрачные кабели отходят от контроллера, обычные кабели
ответвляются от непрозрачных, а [устройства](../ae2-mechanics/devices.md) подключаются к обычным кабелям группами по 8 или меньше.

Пример того, как делать не следует:

Проследим пути каналов:

1. Сразу справа от контроллера пропускная способность сужается до 8 каналов, потому что накопитель работает как обычный кабель.
Но поскольку здесь не используется умный кабель, число занятых каналов не видно. Осталось 8 каналов.
2. Накопитель занимает канал.
Осталось 7 каналов.
3. 2 канала уходят вверх к терминалам.
Осталось 5 каналов.
4. Дальше справа интерфейс занимает ещё один канал.
Осталось 4 канала.
5. 1 канал уходит вверх к поставщику шаблонов.
Осталось 3 канала.
6. Дальше справа 1 канал уходит вверх к шине импорта.
Осталось 2 канала.
7. Группе поставщиков шаблонов, снабжающих сборщики, достаётся только 2 канала, поэтому 2 поставщика остаются без каналов.

В итоге ошибка состоит в создании узкого места и непродуманном распределении каналов.

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../assets/assemblies/bad_network_structure.snbt" />

<LineAnnotation color="#33ff33" from="6.5 .5 1.5" to="6 .5 1.5" alwaysOnTop={true} thickness="0.4">
  32 канала
</LineAnnotation>

<LineAnnotation color="#33ff33" from="6 .5 1.5" to="5.5 .5 1.5" alwaysOnTop={true} thickness="0.2">
  8 каналов
</LineAnnotation>

<LineAnnotation color="#33ff33" from="5.5 .5 1.5" to="5.5 1.5 1.5" alwaysOnTop={true} thickness="0.1">
  2 канала
</LineAnnotation>

<LineAnnotation color="#33ff33" from="5.5 .5 1.5" to="5.5 .3 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="5.5 1.5 1.5" to="5.5 2.5 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="5.5 2.5 1.5" to="5.5 2.5 1.1" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="5.5 .5 1.5" to="4.5 .5 1.5" alwaysOnTop={true} thickness="0.158">
  5 каналов
</LineAnnotation>

<LineAnnotation color="#33ff33" from="4.5 .5 1.5" to="4.5 .3 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="4.5 .5 1.5" to="4.5 1.5 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="4.5 .5 1.5" to="3.5 .5 1.5" alwaysOnTop={true} thickness="0.122">
  3 канала
</LineAnnotation>

<LineAnnotation color="#33ff33" from="3.5 .5 1.5" to="3.5 2.5 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="3.5 2.5 1.5" to="3.7 2.5 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="3.5 .5 1.5" to="1.5 .5 1.5" alwaysOnTop={true} thickness="0.1">
  2 канала
</LineAnnotation>

<LineAnnotation color="#33ff33" from="1.5 0.5 1.5" to="1.5 0.3 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="1.5 0.5 1.5" to="0.5 0.5 1.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#33ff33" from="0.5 0.5 1.5" to="0.5 0.5 0.5" alwaysOnTop={true} thickness="0.071">
  1 канал
</LineAnnotation>

<LineAnnotation color="#ff3333" from="0.5 1.5 1.5" to="0.5 1.3 1.5" alwaysOnTop={true} thickness="0.071">
  нет каналов
</LineAnnotation>

<LineAnnotation color="#ff3333" from="1.5 1.5 0.5" to="1.5 1.3 0.5" alwaysOnTop={true} thickness="0.071">
  нет каналов
</LineAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

---

Пример хорошей структуры:

<GameScene zoom="2.5" interactive={true}>
  <ImportStructure src="../assets/assemblies/treelike_network_structure.snbt" />

    <BoxAnnotation color="#dddddd" min="6.9 0 4.9" max="9.1 4 7.1" thickness="0.05">
        Обратите внимание: поставщики шаблонов разделены на группы по 8.
    </BoxAnnotation>

    <BoxAnnotation color="#dddddd" min="5 4 4" max="8 5 5" thickness="0.05">
        В месте объединения двух обычных кабелей со всеми занятыми каналами нужен непрозрачный кабель.
    </BoxAnnotation>

    <BoxAnnotation color="#dddddd" min="5 0 13" max="8 1 14" thickness="0.05">
        Разные цвета кабелей не дают соседним кабелям соединяться.
    </BoxAnnotation>


  <IsometricCamera yaw="315" pitch="30" />
</GameScene>

## Режимы каналов

В AE2 10.0.0 для Minecraft 1.18 появились новые возможности настройки поведения каналов AE2 в мире.
В общем разделе конфигурации появился параметр (`channels`), управляющий этим поведением, а также новая игровая
команда, позволяющая операторам менять режим и конфигурацию прямо из игры. Для изменения используется `/ae2 channelmode <mode>`,
а для просмотра текущего режима — `/ae2 channelmode`. При смене режима в игре все существующие сети
перезагружаются и сразу переходят на новый режим.

Это возвращает и улучшает возможность, доступную в Minecraft 1.12, и предлагает более удобные варианты для
игроков, которые хотят немного упростить игру, сохранив саму механику.

Следующая таблица перечисляет режимы, доступные в файле конфигурации и команде.

| Настройка    | Описание                                                                                                                                                                                                                               |
| ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `default`  | Стандартный режим с пропускной способностью кабелей и сетей без контроллера, описанной на страницах этого сайта                                                                                                                           |
| `x2`       | Число каналов везде удвоено (16 на обычном кабеле, 64 на непрозрачном кабеле, сети без контроллера поддерживают 16 каналов)                                                                                                                           |
| `x3`       | Число каналов везде утроено (24 на обычном кабеле, 92 на непрозрачном кабеле, сети без контроллера поддерживают 24 канала)                                                                                                                           |
| `x4`       | Число каналов везде увеличено вчетверо (32 на обычном кабеле, 128 на непрозрачном кабеле, сети без контроллера поддерживают 32 канала)                                                                                                                       |
| `infinite` | Все ограничения каналов сняты. Контроллеры по-прежнему *значительно* снижают энергопотребление сетей. Умные кабели отображают только два состояния: полностью выключены (каналы не проходят) или полностью включены (проходит 1 или более каналов). |
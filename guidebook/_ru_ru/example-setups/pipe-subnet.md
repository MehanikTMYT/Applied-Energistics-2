---
navigation:
  parent: example-setups/example-setups-index.md
  title: «Трубная» подсеть для предметов и жидкостей
  icon: storage_bus
---

# «Трубная» подсеть для предметов и жидкостей

Простой способ имитировать предметную и/или жидкостную трубу с помощью [устройств](../ae2-mechanics/devices.md) AE2. Подходит для любых задач, где нужна такая труба.
В том числе для возврата результата изготовления в <ItemLink id="pattern_provider" />.

Обычно это можно реализовать двумя способами:

## Шина импорта -> Шина хранения

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/import_storage_pipe.snbt" />

<BoxAnnotation color="#dddddd" min="3.7 0 0" max="4 1 1">
        (1) МЭ шина импорта: можно настроить фильтр.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="1 0 0" max="1.3 1 1">
        (2) МЭ шина хранения: можно настроить фильтр. Она (и другие шины хранения, используемые как получатели)
        должна быть единственным хранилищем в сети.
  </BoxAnnotation>

<DiamondAnnotation pos="4.5 0.5 0.5" color="#00ff00">
        Источник
    </DiamondAnnotation>

<DiamondAnnotation pos="0.5 0.5 0.5" color="#00ff00">
        Получатель
    </DiamondAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

<ItemLink id="import_bus" /> (1) на исходном инвентаре импортирует предметы или жидкость и пытается поместить их в [сетевое хранилище](../ae2-mechanics/import-export-storage.md).
Поскольку единственное хранилище в сети — <ItemLink id="storage_bus" /> (2) (поэтому используется подсеть, а не основная сеть), предметы или жидкость
попадают в инвентарь получателя, завершая перенос. Энергия подаётся через <ItemLink id="quartz_fiber" />.
На шине импорта и шине хранения можно настроить фильтры; без фильтров установка переносит всё, к чему имеет доступ.
Эта схема работает и с несколькими шинами импорта и хранения.

## Шина хранения -> Шина экспорта

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/storage_export_pipe.snbt" />

<BoxAnnotation color="#dddddd" min="3.7 0 0" max="4 1 1">
        (1) МЭ шина хранения: можно настроить фильтр. Она (и другие шины хранения, используемые как источники)
        должна быть единственным хранилищем в сети.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="1 0 0" max="1.3 1 1">
        (2) МЭ шина экспорта: обязательно нужен фильтр.
  </BoxAnnotation>

<DiamondAnnotation pos="4.5 0.5 0.5" color="#00ff00">
        Источник
    </DiamondAnnotation>

<DiamondAnnotation pos="0.5 0.5 0.5" color="#00ff00">
        Получатель
    </DiamondAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

<ItemLink id="export_bus" /> на инвентаре получателя пытается извлечь указанные в фильтре предметы из [сетевого хранилища](../ae2-mechanics/import-export-storage.md).
Поскольку единственное хранилище в сети — <ItemLink id="storage_bus" /> (поэтому используется подсеть, а не основная сеть), предметы или жидкость
извлекаются из исходного инвентаря, завершая перенос. Энергия подаётся через <ItemLink id="quartz_fiber" />.
Поскольку шинам экспорта для работы нужен фильтр, эта схема работает только после его настройки.
Эта схема работает и с несколькими шинами хранения и экспорта.

## Неработающая схема (Шина импорта -> Шина экспорта)

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/import_export_pipe.snbt" />

<BoxAnnotation color="#dd3333" min="3.7 0 0" max="4 1 1">
        МЭ шина импорта: в сети нет хранилища, поэтому импортировать некуда.
  </BoxAnnotation>

<BoxAnnotation color="#dd3333" min="1 0 0" max="1.3 1 1">
        (2) МЭ шина экспорта: в сети нет хранилища, поэтому экспортировать нечего.
  </BoxAnnotation>

<DiamondAnnotation pos="4.5 0.5 0.5" color="#ff0000">
        Источник
    </DiamondAnnotation>

<DiamondAnnotation pos="0.5 0.5 0.5" color="#ff0000">
        Получатель
    </DiamondAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Схема только с шинами импорта и экспорта не работает. Шина импорта пытается извлечь содержимое исходного инвентаря
и поместить предметы или жидкость в сетевое хранилище. Шина экспорта пытается извлечь содержимое сетевого хранилища и поместить
предметы или жидкость в инвентарь получателя. Но поскольку в этой сети **нет хранилища**, шина импорта не может импортировать,
а шина экспорта — экспортировать, и ничего не происходит.

## Ввод и вывод через 1 грань

Допустим, у машины ввод и извлечение результата доступны через 1 грань. (Например, у <ItemLink id="charger" />)
Можно подавать ингредиенты и извлекать результат, объединив 2 способа построения трубной подсети:

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/import_storage_export_pipe.snbt" />

<BoxAnnotation color="#dddddd" min="4 1 1" max="5 1.3 2">
        (1) МЭ шина импорта: можно настроить фильтр.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="2 1 1" max="3 1.3 2">
        (2) МЭ шина хранения: можно настроить фильтр. Она (и другие шины хранения для подачи и извлечения предметов)
        должна быть единственным хранилищем в сети.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="2 0 1" max="3 1 2">
        (3) Устройство, в которое подаются и из которого извлекаются предметы: в данном случае зарядник.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="0 1 1" max="1 1.3 2">
        (4) МЭ шина экспорта: обязательно нужен фильтр.
  </BoxAnnotation>

<DiamondAnnotation pos="4.5 0.5 1.5" color="#00ff00">
        Источник
    </DiamondAnnotation>

<DiamondAnnotation pos="0.5 0.5 1.5" color="#00ff00">
        Получатель
    </DiamondAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## МЭ интерфейсы

Помимо шин импорта и экспорта существуют и другие [устройства](../ae2-mechanics/devices.md), которые помещают предметы
в [сетевое хранилище](../ae2-mechanics/import-export-storage.md) и извлекают их оттуда!
Здесь пригодится <ItemLink id="interface" />. Если вставить предмет, запас которого интерфейс не настроен поддерживать, он
отправит его в сетевое хранилище. Это можно использовать аналогично трубе «шина импорта -> шина хранения». Если настроить интерфейс на
поддержание запаса предмета, он будет забирать его из сетевого хранилища, как труба «шина хранения -> шина экспорта». Интерфейс может
поддерживать запас одних предметов и не хранить другие, позволяя при необходимости удалённо подавать и извлекать предметы через шины хранения.

<GameScene zoom="6" background="transparent">
<ImportStructure src="../assets/assemblies/interface_pipes.snbt" />

<BoxAnnotation color="#dddddd" min="3.7 0 0" max="4 1 1">
        МЭ интерфейс
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="1 0 0" max="1.3 1 1">
        МЭ шина хранения
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="3.7 0 2" max="4 1 3">
        МЭ шина хранения
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="0 1 2" max="1 1.3 3">
        МЭ интерфейс
  </BoxAnnotation>

<IsometricCamera yaw="195" pitch="30" />
</GameScene>

## От одного к нескольким, от нескольких к одному (и от нескольких к нескольким)

Разумеется, можно использовать больше одной <ItemLink id="import_bus" />, <ItemLink id="export_bus" /> или <ItemLink id="storage_bus" />

<GameScene zoom="3" background="transparent">
<ImportStructure src="../assets/assemblies/many_to_many_pipe.snbt" />

<IsometricCamera yaw="185" pitch="30" />
</GameScene>

## Подача в несколько мест

Из всего этого можно вывести способ передачи ингредиентов с одной грани <ItemLink id="pattern_provider" /> в несколько разных
мест, например в ряд машин или на несколько граней одной машины.

Трубы «импорт -> хранение» и «хранение -> экспорт» не подойдут, потому что <ItemLink id="pattern_provider" /> никогда
не хранит ингредиенты внутри себя. Вместо этого поставщики *выталкивают* ингредиенты в соседние инвентари, поэтому нужен
соседний инвентарь, способный также импортировать предметы.

Для этого подходит… <ItemLink id="interface" />!
Убедитесь, что поставщик работает в направленном режиме или выполнен в виде плоской части и/или интерфейс выполнен в виде плоской части, чтобы они не образовали сетевое
соединение.

<GameScene zoom="6" background="transparent">
<ImportStructure src="../assets/assemblies/provider_interface_storage.snbt" />

<BoxAnnotation color="#dddddd" min="2.7 0 1" max="3 1 2">
        МЭ интерфейс (обязательно плоский, а не полноразмерный блок)
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="1 0 0" max="1.3 1 4">
        МЭ шины хранения
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="0 0 0" max="1 1 4">
        Места подачи ингредиентов по шаблону (несколько машин или несколько граней 1 машины)
  </BoxAnnotation>

<IsometricCamera yaw="185" pitch="30" />
</GameScene>
---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: МЭ интерфейс
  icon: interface
  position: 210
categories:
- devices
item_ids:
- ae2:interface
- ae2:cable_interface
---

# МЭ интерфейс

<Row gap="20">
<BlockImage id="interface" scale="8" />
<GameScene zoom="8" background="transparent">
  <ImportStructure src="../assets/blocks/cable_interface.snbt" />
</GameScene>
</Row>

МЭ интерфейс работает как небольшой сундук и резервуар, который пополняется из [сетевого хранилища](../ae2-mechanics/import-export-storage.md) и возвращает туда содержимое
в зависимости от заданного запаса в его слотах. Он старается завершить это за один игровой такт, поэтому способен принимать
или отдавать до 9 стаков за игровой такт. При наличии быстрых предметных труб это быстрый способ импорта или экспорта.

Ещё одно полезное свойство: большинство резервуаров хранят только 1 тип жидкости, а интерфейс — до 9, помимо предметов.
По сути, это сундуки и резервуары для нескольких жидкостей с дополнительными функциями. Эти функции можно отключить,
не подключая интерфейсы ни к каким сетям.
Поэтому они полезны в отдельных случаях, когда нужно хранить понемногу разных ресурсов.

## Внутреннее устройство интерфейса

Как уже говорилось, интерфейс — по сути сундук или резервуар с очень мощными <ItemLink id="import_bus" /> и
<ItemLink id="export_bus" />, а также набором <ItemLink id="level_emitter" />.

<GameScene zoom="3" interactive={true}>
  <ImportStructure src="../assets/assemblies/interface_internals.snbt" />

  <BoxAnnotation color="#dddddd" min="1.3 0.3 1.3" max="9.7 1 1.7">
        Набор излучателей уровня для управления требуемым запасом
        <GameScene zoom="4" background="transparent">
        <ImportStructure src="../assets/blocks/level_emitter.snbt" />
        </GameScene>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="1.3 4 1.3" max="9.7 4.7 1.7">
        Набор излучателей уровня для управления требуемым запасом
        <GameScene zoom="4" background="transparent">
        <ImportStructure src="../assets/blocks/level_emitter.snbt" />
        </GameScene>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="1.3 1.3 1.3" max="9.7 2 1.7">
        Набор очень мощных шин импорта, способных переносить 1 стак за игровой такт
        <GameScene zoom="4" background="transparent">
        <ImportStructure src="../assets/blocks/import_bus.snbt" />
        </GameScene>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="1.3 3 1.3" max="9.7 3.7 1.7">
        Набор очень мощных шин экспорта, способных переносить 1 стак за игровой такт
        <GameScene zoom="4" background="transparent">
        <ImportStructure src="../assets/blocks/export_bus.snbt" />
        </GameScene>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="1 2 1" max="10 3 2">
        9 отдельных внутренних слотов
  </BoxAnnotation>

  <IsometricCamera yaw="195" pitch="15" />
</GameScene>

## Особые взаимодействия

У интерфейсов также есть особые функции при взаимодействии с другими [устройствами](../ae2-mechanics/devices.md) AE2:

<ItemLink id="storage_bus" />, установленная на ненастроенном интерфейсе, открывает всё [сетевое хранилище](../ae2-mechanics/import-export-storage.md)
его сети для сети шины хранения, словно сеть интерфейса — один большой сундук, на котором установлена шина.
Настройка запаса предмета в слотах фильтра интерфейса отключает это поведение.

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/interface_storage.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Поставщики шаблонов особым образом взаимодействуют с интерфейсами в [подсетях](../ae2-mechanics/subnetworks.md): если интерфейс не настроен,
поставщик полностью обходит его и отправляет ресурсы прямо в [хранилище](../ae2-mechanics/import-export-storage.md) этой подсети,
не заполняя интерфейс порциями ингредиентов рецепта и, что важнее, не отправляя следующую порцию, пока в хранилище не появится место.

<GameScene zoom="6" background="transparent">
<ImportStructure src="../assets/assemblies/provider_interface_storage.snbt" />

<BoxAnnotation color="#dddddd" min="2.7 0 1" max="3 1 2">
        МЭ интерфейс (обязательно плоский, а не полноблочный)
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="1 0 0" max="1.3 1 4">
        Шины хранения
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="0 0 0" max="1 1 4">
        Места подачи ингредиентов по шаблонам (несколько механизмов или несколько сторон 1 механизма)
  </BoxAnnotation>

<IsometricCamera yaw="185" pitch="30" />
</GameScene>

## Варианты

Интерфейсы существуют в 2 вариантах: обычном и плоском/[компоненте кабеля](../ae2-mechanics/cable-subparts.md). От варианта зависит, с каких сторон доступен
их инвентарь и с каких сторон они обеспечивают сетевое соединение.

*   Обычные интерфейсы позволяют подавать, извлекать предметы и обращаться к своему инвентарю со всех сторон и, как большинство механизмов AE2, работают
    как кабель, обеспечивая сетевое соединение со всех сторон.

*   Плоские интерфейсы — [компоненты кабеля](../ae2-mechanics/cable-subparts.md), поэтому на одном кабеле можно разместить несколько, создавая компактные схемы.
    Они позволяют подавать, извлекать предметы и обращаться к инвентарю с лицевой стороны, но не обеспечивают сетевого соединения с этой стороны.

Обычные и плоские интерфейсы можно превращать друг в друга в сетке крафта.

## Настройки

Верхние слоты интерфейса задают ресурсы, запас которых он должен поддерживать. Если поместить туда предмет
или перетащить его из JEI/REI, появится гаечный ключ, позволяющий задать количество.

Щёлкните правой кнопкой мыши с ёмкостью для жидкости (например, ведром или резервуаром), чтобы задать фильтр по жидкости, а не по самому ведру или резервуару.

Если для слота задано поддержание запаса, внешние механизмы также не смогут помещать в него другие ресурсы.

## Улучшения

МЭ интерфейс поддерживает следующие [улучшения](upgrade_cards.md):

*   <ItemLink id="fuzzy_card" /> позволяет шине фильтровать по степени износа и/или игнорировать NBT предметов
*   <ItemLink id="crafting_card" /> позволяет интерфейсу отправлять запросы системе [автокрафта](../ae2-mechanics/autocrafting.md)
    для получения нужных предметов. По возможности он сначала забирает предметы из хранилища и лишь затем запрашивает
    изготовление новых.

## Приоритет

Приоритет задаётся нажатием на гаечный ключ в правом верхнем углу интерфейса. Интерфейсы с более высоким приоритетом получают предметы
раньше интерфейсов с более низким приоритетом,

## Рецепты

<Recipe id="network/blocks/interfaces_interface" />

<RecipeFor id="cable_interface" />

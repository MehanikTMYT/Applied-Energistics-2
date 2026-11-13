---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: Кабели
  icon: fluix_glass_cable
  position: 110
categories:
- network infrastructure
item_ids:
- ae2:white_glass_cable
- ae2:orange_glass_cable
- ae2:magenta_glass_cable
- ae2:light_blue_glass_cable
- ae2:yellow_glass_cable
- ae2:lime_glass_cable
- ae2:pink_glass_cable
- ae2:gray_glass_cable
- ae2:light_gray_glass_cable
- ae2:cyan_glass_cable
- ae2:purple_glass_cable
- ae2:blue_glass_cable
- ae2:brown_glass_cable
- ae2:green_glass_cable
- ae2:red_glass_cable
- ae2:black_glass_cable
- ae2:fluix_glass_cable
- ae2:white_covered_cable
- ae2:orange_covered_cable
- ae2:magenta_covered_cable
- ae2:light_blue_covered_cable
- ae2:yellow_covered_cable
- ae2:lime_covered_cable
- ae2:pink_covered_cable
- ae2:gray_covered_cable
- ae2:light_gray_covered_cable
- ae2:cyan_covered_cable
- ae2:purple_covered_cable
- ae2:blue_covered_cable
- ae2:brown_covered_cable
- ae2:green_covered_cable
- ae2:red_covered_cable
- ae2:black_covered_cable
- ae2:fluix_covered_cable
- ae2:white_covered_dense_cable
- ae2:orange_covered_dense_cable
- ae2:magenta_covered_dense_cable
- ae2:light_blue_covered_dense_cable
- ae2:yellow_covered_dense_cable
- ae2:lime_covered_dense_cable
- ae2:pink_covered_dense_cable
- ae2:gray_covered_dense_cable
- ae2:light_gray_covered_dense_cable
- ae2:cyan_covered_dense_cable
- ae2:purple_covered_dense_cable
- ae2:blue_covered_dense_cable
- ae2:brown_covered_dense_cable
- ae2:green_covered_dense_cable
- ae2:red_covered_dense_cable
- ae2:black_covered_dense_cable
- ae2:fluix_covered_dense_cable
- ae2:white_smart_cable
- ae2:orange_smart_cable
- ae2:magenta_smart_cable
- ae2:light_blue_smart_cable
- ae2:yellow_smart_cable
- ae2:lime_smart_cable
- ae2:pink_smart_cable
- ae2:gray_smart_cable
- ae2:light_gray_smart_cable
- ae2:cyan_smart_cable
- ae2:purple_smart_cable
- ae2:blue_smart_cable
- ae2:brown_smart_cable
- ae2:green_smart_cable
- ae2:red_smart_cable
- ae2:black_smart_cable
- ae2:fluix_smart_cable
- ae2:white_smart_dense_cable
- ae2:orange_smart_dense_cable
- ae2:magenta_smart_dense_cable
- ae2:light_blue_smart_dense_cable
- ae2:yellow_smart_dense_cable
- ae2:lime_smart_dense_cable
- ae2:pink_smart_dense_cable
- ae2:gray_smart_dense_cable
- ae2:light_gray_smart_dense_cable
- ae2:cyan_smart_dense_cable
- ae2:purple_smart_dense_cable
- ae2:blue_smart_dense_cable
- ae2:brown_smart_dense_cable
- ae2:green_smart_dense_cable
- ae2:red_smart_dense_cable
- ae2:black_smart_dense_cable
- ae2:fluix_smart_dense_cable
---

# Кабели

<GameScene zoom="3" background="transparent">
  <ImportStructure src="../assets/assemblies/cables.snbt" />
  <IsometricCamera yaw="180" pitch="30" />
</GameScene>

Хотя соседние машины с поддержкой МЭ тоже образуют МЭ-сеть, кабели — основной способ
расширять МЭ-сеть на большие расстояния.

Кабели разных цветов не соединяются друг с другом, даже находясь рядом,
что позволяет эффективнее распределять [каналы](../ae2-mechanics/channels.md). Цвет кабеля также влияет на цвет подключённых терминалов:
необязательно делать все терминалы фиолетовыми. Флюисовые кабели соединяются с кабелями любого цвета.

Обратите внимание: **КАНАЛЫ НИКАК НЕ СВЯЗАНЫ С ЦВЕТОМ КАБЕЛЯ**. Цвет лишь запрещает соединение кабелей.

## Важное замечание

**Если AE2 и каналы пока незнакомы, используйте умные и непрозрачные умные кабели везде, где возможно.
Они покажут маршруты каналов в сети и помогут понять их поведение.**

## Ещё одно замечание

**Это не трубы для предметов, жидкостей, энергии и прочего.** У них нет внутреннего инвентаря, поставщики шаблонов и машины ничего
в них не «проталкивают»; они лишь соединяют [устройства](../ae2-mechanics/devices.md) AE2 в сеть.

## Стеклянный кабель

<GameScene zoom="6" background="transparent">
<ImportStructure src="../assets/assemblies/fluix_glass_cable.snbt" />
<IsometricCamera yaw="195" pitch="30" />
</GameScene>

<ItemLink id="fluix_glass_cable" /> — самый простой в изготовлении кабель, передающий энергию
и до 8 [каналов](../ae2-mechanics/channels.md). Доступно 17 цветов, по умолчанию
используется флюисовый. Кабель можно окрасить любым из 16 красителей.

Чтобы окрасить кабели, окружите любой краситель 8 кабелями одного
типа (цвет кабелей неважен, но тип должен совпадать:
стеклянные, умные и так далее). Также кабели можно красить прямо в мире любой совместимой с Forge
кистью.

Чтобы удалить краску, объедините любой окрашенный кабель с ведром воды в сетке изготовления.

Кабель можно покрыть шерстью, получив <ItemLink id="fluix_covered_cable" />, а затем создать <ItemLink id="fluix_smart_cable" />, чтобы лучше понимать состояние
[каналов](../ae2-mechanics/channels.md).

<RecipeFor id="fluix_glass_cable" />

<RecipeFor id="blue_glass_cable" />

## Покрытый кабель

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/fluix_covered_cable.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Покрытый кабель не даёт игровых преимуществ по сравнению с <ItemLink id="fluix_glass_cable" />. Однако его можно использовать
для оформления, если такой внешний вид нравится больше.

Окрашивается так же, как <ItemLink id="fluix_glass_cable" />. Объединив четыре <ItemLink id="fluix_covered_cable" />
с редстоуном и светокаменной пылью, можно получить <ItemLink id="fluix_covered_dense_cable" />.

<Recipe id="network/cables/covered_fluix" />

<RecipeFor id="blue_covered_cable" />

## Непрозрачный кабель

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/fluix_covered_dense_cable.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Кабель повышенной пропускной способности. Передаёт 32 канала, тогда как обычные кабели передают только 8.
Однако на нём нельзя размещать шины, поэтому перед установкой шин или панелей необходимо перейти с непрозрачного
на более тонкий кабель (например, <ItemLink id="fluix_glass_cable" /> или <ItemLink id="fluix_smart_cable" />),
а уже затем устанавливать компоненты.

Непрозрачные кабели немного изменяют правило «кратчайшего пути» для каналов: каналы идут кратчайшим путём к
непрозрачному кабелю, а затем кратчайшим путём по нему до контроллера.

<Recipe id="network/cables/dense_covered_fluix" />

<RecipeFor id="blue_covered_dense_cable" />

## Умный кабель

<Row>
<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/fluix_smart_cable.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>
<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/fluix_smart_dense_cable.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>
</Row>

Внешне эти кабели немного похожи на <ItemLink id="fluix_covered_cable" />, но
позволяют диагностировать сеть, показывая использование каналов.
Каналы отображаются светящимися цветными линиями вдоль чёрной полосы
кабеля, позволяя понять, как они используются
в сети. У обычного умного кабеля первые четыре канала показаны линиями цвета
кабеля, следующие четыре — белыми линиями. У непрозрачного умного кабеля каждая полоса обозначает 4 канала.

В сетях с <ItemLink id="controller" /> линии на кабелях показывают точный путь каналов.

В сетях без контроллера умные кабели показывают число используемых каналов во всей сети, а не число каналов, проходящих через конкретный кабель.

Их тоже можно окрашивать так же, как <ItemLink id="fluix_glass_cable" />.

<Recipe id="network/cables/smart_fluix" />

<Recipe id="network/cables/dense_smart_fluix" />

<RecipeFor id="blue_smart_cable" />

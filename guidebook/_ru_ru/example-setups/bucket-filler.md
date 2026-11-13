---
navigation:
  parent: example-setups/example-setups-index.md
  title: Наполнение вёдер
  icon: minecraft:water_bucket
---

# Наполнение вёдер

См. также [«Опорожнение вёдер»](bucket-emptier.md).

Поскольку здесь используется <ItemLink id="pattern_provider" />, эта схема предназначена для включения в систему [автокрафта](../ae2-mechanics/autocrafting.md)
сети.

Иногда нужны вёдра с жидкостью, а не сама жидкость. Порой с этим помогает специальная машина
(например, жидкостный транспозер из Thermal Expansion), но мод с удобным решением есть не всегда. К счастью,
в обычном Minecraft есть чуть менее удобный способ — <ItemLink id="minecraft:dispenser" />.

**Часто эта схема не нужна: замена жидкостей в
[МЭ терминале для закодированных шаблонов](../items-blocks-machines/terminals.md#pattern-encoding-terminal) позволяет использовать в
рецепте изготовления саму жидкость вместо ведра с ней.**

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/bucket_filler.snbt" />

<BoxAnnotation color="#dddddd" min="2 1 0" max="3 2 1">
        (1) МЭ поставщик шаблонов: блокировка создания — «С сигналом редстоуна», установлены соответствующие шаблоны обработки.

        <Row>
        ![Шаблон наполнения](../assets/diagrams/water_fill_pattern_small.png)
        ![Шаблон наполнения](../assets/diagrams/lava_fill_pattern_small.png)
        </Row>
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="3 1.1 0.1" max="3.2 1.9 0.9">
        (2) МЭ интерфейс: настройки по умолчанию.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="3.1 1.1 0.8" max="3.9 1.9 1">
        (3) МЭ шина хранения #1: настройки по умолчанию.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="4.05 1.05 0.8" max="4.95 1.95 1">
        (4) МЭ плоскость формирования: вёдра внесены в чёрный список с помощью карты-инвертера.
        <Row><ItemImage id="minecraft:bucket" scale="2" /><ItemImage id="inverter_card" scale="2" /></Row>
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="3.2 2 1.2" max="3.8 2.2 1.8">
        (5) МЭ шина импорта: вёдра внесены в чёрный список с помощью карты-инвертера.
        <Row><ItemImage id="minecraft:bucket" scale="2" /><ItemImage id="inverter_card" scale="2" /></Row>
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="2.1 2 0.1" max="2.9 2.2 0.9">
        (6) МЭ шина хранения #2: настройки по умолчанию.
  </BoxAnnotation>

<DiamondAnnotation pos="0 1.5 0.5" color="#00ff00">
        К основной сети
    </DiamondAnnotation>

  <IsometricCamera yaw="225" pitch="45" />
</GameScene>

## Настройки

* У <ItemLink id="pattern_provider" /> (1) блокировка создания установлена на «С сигналом редстоуна», установлены соответствующие <ItemLink id="processing_pattern" />.
  
    ![Шаблон зарядника](../assets/diagrams/water_fill_pattern.png)
    ![Шаблон зарядника](../assets/diagrams/lava_fill_pattern.png)

* <ItemLink id="interface" /> (2) имеет настройки по умолчанию.
* Первая <ItemLink id="storage_bus" /> (3) имеет настройки по умолчанию.
* У <ItemLink id="formation_plane" /> (4) вёдра внесены в чёрный список с помощью карты-инвертера.
  <Row><ItemImage id="minecraft:bucket" scale="2" /><ItemImage id="inverter_card" scale="2" /></Row>
* У <ItemLink id="import_bus" /> (5) вёдра внесены в чёрный список с помощью карты-инвертера.
  <Row><ItemImage id="minecraft:bucket" scale="2" /><ItemImage id="inverter_card" scale="2" /></Row>
* Вторая <ItemLink id="storage_bus" /> (6) имеет настройки по умолчанию.

## Принцип работы

1. <ItemLink id="pattern_provider" /> отправляет ингредиенты в <ItemLink id="interface" />.
   (На самом деле для оптимизации он отправляет их прямо через шину хранения и плоскость формирования, словно они продолжают грани поставщика. Предметы вообще не попадают в интерфейс.)
2. Благодаря механизмам, описанным в разделе [«Подсети-трубопроводы»](pipe-subnet.md#providing-to-multiple-places), и <ItemLink id="formation_plane" />,
   ведро попадает в <ItemLink id="minecraft:dispenser" />, а жидкость размещается плоскостью формирования.
3. <ItemLink id="minecraft:comparator" /> обнаруживает ведро в раздатчике и одновременно подаёт сигнал раздатчику и блокирует
   <ItemLink id="pattern_provider" />.
4. Раздатчик зачерпывает жидкость ведром, и внутри него оказывается наполненное ведро.
5. <ItemLink id="import_bus" /> извлекает наполненное ведро из раздатчика и через
   <ItemLink id="storage_bus" /> помещает его в поставщик шаблонов, возвращая в основную сеть.
6. Компаратор обнаруживает, что раздатчик пуст, и разблокирует поставщик.

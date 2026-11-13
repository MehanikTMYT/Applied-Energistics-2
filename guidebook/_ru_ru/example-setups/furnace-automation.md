---
navigation:
  parent: example-setups/example-setups-index.md
  title: Автоматизация печи
  icon: minecraft:furnace
---

# Автоматизация печи

Поскольку здесь используется <ItemLink id="pattern_provider" />, эта схема предназначена для включения в систему [автокрафта](../ae2-mechanics/autocrafting.md)
сети. Для независимой автоматизации печи достаточно воронок, сундуков и подобных средств.

Автоматизировать <ItemLink id="minecraft:furnace" /> немного сложнее, чем более простые машины, например [зарядник](../example-setups/charger-automation.md).
В печь нужно подавать предметы с двух разных сторон, а извлекать с третьей. Предмет для переплавки нужно подавать сверху,
топливо — сбоку, а результат извлекать снизу.

Для этого можно установить <ItemLink id="pattern_provider" />
сверху, <ItemLink id="export_bus" /> сбоку для постоянной подачи топлива и <ItemLink id="import_bus" />
снизу для переноса результатов в сеть. Однако это занимает 3 [канала](../ae2-mechanics/channels.md).

Вот как обойтись всего 1 каналом:

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/furnace_automation.snbt" />

<BoxAnnotation color="#dddddd" min="1 0 0" max="2 1 1">
        (1) МЭ поставщик шаблонов: направленный вариант, полученный с помощью гаечного ключа из истинного кварца, установлены соответствующие шаблоны обработки.

        ![Шаблон железа](../assets/diagrams/furnace_pattern_small.png)
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="1 1 0" max="2 1.3 1">
        (2) МЭ интерфейс: настройки по умолчанию.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="1 1 0" max="1.3 2 1">
        (3) МЭ шина хранения #1: фильтр настроен на уголь.
        <ItemImage id="minecraft:coal" scale="2" />
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="0 2 0" max="1 2.3 1">
        (4) МЭ шина хранения #2: уголь внесён в чёрный список с помощью карты-инвертера.
        <Row><ItemImage id="minecraft:coal" scale="2" /><ItemImage id="inverter_card" scale="2" /></Row>
  </BoxAnnotation>

<DiamondAnnotation pos="4 0.5 0.5" color="#00ff00">
        К основной сети
    </DiamondAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Настройки

* <ItemLink id="pattern_provider" /> (1) имеет настройки по умолчанию, установлены соответствующие <ItemLink id="processing_pattern" />.
    Чтобы сделать его направленным, нужно применить к нему <ItemLink id="certus_quartz_wrench" />.

  ![Шаблон железа](../assets/diagrams/furnace_pattern.png)

* <ItemLink id="interface" /> (2) имеет настройки по умолчанию.
* Первая <ItemLink id="storage_bus" /> (3) настроена на фильтрацию угля или другого выбранного топлива.
* У второй <ItemLink id="storage_bus" /> (4) выбранное топливо внесено в чёрный список с помощью <ItemLink id="inverter_card" />.

## Принцип работы

1. <ItemLink id="pattern_provider" /> отправляет ингредиенты в <ItemLink id="interface" />.
   (На самом деле для оптимизации он отправляет их прямо через шины хранения, словно они продолжают грани поставщика. Предметы вообще не попадают в интерфейс.)
2. Интерфейс не настроен на хранение предметов, поэтому пытается отправить ингредиенты в [сетевое хранилище](../ae2-mechanics/import-export-storage.md).
3. Единственные хранилища зелёной подсети — <ItemLink id="storage_bus" />. Шина с фильтром на уголь помещает его в топливный слот печи через боковую грань.
    Шина, отфильтровывающая всё, КРОМЕ угля, помещает предметы для переплавки в верхний слот через верхнюю грань.
4. Печь переплавляет предметы
5. Воронка извлекает результаты через нижнюю грань печи и помещает их в слоты возврата поставщика, возвращая в основную сеть.

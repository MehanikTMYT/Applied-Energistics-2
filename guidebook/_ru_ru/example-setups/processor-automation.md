---
navigation:
  parent: example-setups/example-setups-index.md
  title: Автоматизация производства процессоров
  icon: logic_processor
---

# Автоматизация производства процессоров

Есть много способов автоматизировать производство [процессоров](../items-blocks-machines/processors.md), и это один из них.

Эту общую схему можно реализовать с любыми предметными трубами, каналами или другими средствами передачи из модов, если
они поддерживают фильтрацию.

![Схема производственного процесса](../assets/diagrams/processor_flow_diagram.png)

Здесь подробно описано, как сделать это средствами AE2 с помощью [«трубных» подсетей](pipe-subnet.md).

Поскольку здесь используется <ItemLink id="pattern_provider" />, схема предназначена для включения в систему [автоматического создания](../ae2-mechanics/autocrafting.md)
предметов. Для автономного производства процессоров замените поставщик шаблонов ещё одной бочкой и кладите ингредиенты прямо в верхнюю бочку.

Схема также обратно совместима
с предыдущими версиями AE2: даже если <ItemLink id="inscriber" /> принимают и выдают предметы только с определённых сторон, трубные подсети всё равно подают и
извлекают их через нужные грани.

## Урок кодирования шаблонов

Нередко нужный [шаблон](../items-blocks-machines/patterns.md) **НЕ СОВПАДАЕТ С ТЕМ, ЧТО ПОКАЗЫВАЕТ JEI**, или с тем, что JEI создаёт по кнопке +.
В этом случае JEI создаёт 2 отдельных шаблона: для печатных компонентов и для окончательной сборки. При этом шаблон печатных
компонентов содержит [пресс](../items-blocks-machines/presses.md). Это не подходит, поскольку установка работает иначе. Нужен 1 шаблон, который
принимает исходные ресурсы и выдаёт готовый процессор. Пресс уже находится в высекателе, поэтому добавлять его в шаблон не следует.

---

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../assets/assemblies/processor_automation.snbt" />

  <BoxAnnotation color="#dddddd" min="5 1 0" max="6 2 1" thickness=".05">
        (1) МЭ поставщик шаблонов: настройки по умолчанию, установлены соответствующие шаблоны обработки.

        <Row>
            ![Шаблон логического процессора](../assets/diagrams/logic_pattern_small.png)
            ![Шаблон вычислительного процессора](../assets/diagrams/calculation_pattern_small.png)
            ![Шаблон инженерного процессора](../assets/diagrams/engineering_pattern_small.png)
        </Row>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="4.7 2 0" max="5 3 1" thickness=".05">
        (2) МЭ шина хранения #1: настройки по умолчанию.
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="4 1 0" max="4.3 2 1" thickness=".05">
        (3) МЭ шина экспорта #1: фильтр на кремний. Установлены 2 карты ускорения
        <Row><ItemImage id="silicon" scale="2" /> <ItemImage id="speed_card" scale="2" /></Row>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="4 4 0" max="4.3 3 1" thickness=".05">
        (4) МЭ шина экспорта #2: фильтр на золотой слиток. Установлены 2 карты ускорения
        <Row><ItemImage id="minecraft:gold_ingot" scale="2" /> <ItemImage id="speed_card" scale="2" /></Row>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="4 5 0" max="4.3 4 1" thickness=".05">
        (5) МЭ шина экспорта #3: фильтр на кристалл истинного кварца. Установлены 2 карты ускорения
        <Row><ItemImage id="certus_quartz_crystal" scale="2" /> <ItemImage id="speed_card" scale="2" /></Row>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="4 6 0" max="4.3 5 1" thickness=".05">
        (6) МЭ шина экспорта #4: фильтр на алмаз. Установлены 2 карты ускорения
        <Row><ItemImage id="minecraft:diamond" scale="2" /> <ItemImage id="speed_card" scale="2" /></Row>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="2.3 3 0" max="2 2 1" thickness=".05">
        (7) МЭ шина экспорта #5: фильтр на редстоуновую пыль. Установлены 2 карты ускорения
        <Row><ItemImage id="minecraft:redstone" scale="2" /> <ItemImage id="speed_card" scale="2" /></Row>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="4 1 0" max="3 2 1" thickness=".05">
        (8) Высекатель #1: настройки по умолчанию. Установлены кремниевый пресс для высекателя и 4 карты ускорения
        <Row><ItemImage id="silicon_press" scale="2" /> <ItemImage id="speed_card" scale="2" /></Row>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="4 3 0" max="3 4 1" thickness=".05">
        (9) Высекатель #2: настройки по умолчанию. Установлены логический пресс для высекателя и 4 карты ускорения
        <Row><ItemImage id="logic_processor_press" scale="2" /> <ItemImage id="speed_card" scale="2" /></Row>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="4 4 0" max="3 5 1" thickness=".05">
        (10) Высекатель #3: настройки по умолчанию. Установлены «Вычислительная пресс для высекателя» и 4 карты ускорения
        <Row><ItemImage id="calculation_processor_press" scale="2" /> <ItemImage id="speed_card" scale="2" /></Row>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="4 5 0" max="3 6 1" thickness=".05">
        (11) Высекатель #4: настройки по умолчанию. Установлены инженерный пресс для высекателя и 4 карты ускорения
        <Row><ItemImage id="engineering_processor_press" scale="2" /> <ItemImage id="speed_card" scale="2" /></Row>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="2 2 0" max="1 3 1" thickness=".05">
        (12) Высекатель #5: настройки по умолчанию. Установлены 4 карты ускорения
        <ItemImage id="speed_card" scale="2" />
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="2.7 2 0" max="3 1 1" thickness=".05">
        (13) МЭ шина импорта #1: настройки по умолчанию. Установлены 2 карты ускорения
        <ItemImage id="speed_card" scale="2" />
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="2.7 4 0" max="3 3 1" thickness=".05">
        (14) МЭ шина импорта #2: настройки по умолчанию. Установлены 2 карты ускорения
        <ItemImage id="speed_card" scale="2" />
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="2.7 5 0" max="3 4 1" thickness=".05">
        (15) МЭ шина импорта #3: настройки по умолчанию. Установлены 2 карты ускорения
        <ItemImage id="speed_card" scale="2" />
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="2.7 6 0" max="3 5 1" thickness=".05">
        (16) МЭ шина импорта #4: настройки по умолчанию. Установлены 2 карты ускорения
        <ItemImage id="speed_card" scale="2" />
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="2 3 0" max="1 3.3 1" thickness=".05">
        (17) МЭ шина хранения #2: настройки по умолчанию.
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="2 1.7 0" max="1 2 1" thickness=".05">
        (18) МЭ шина хранения #3: настройки по умолчанию.
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="1 2 0" max="0.7 3 1" thickness=".05">
        (19) МЭ шина импорта #5: настройки по умолчанию. Установлены 2 карты ускорения
        <ItemImage id="speed_card" scale="2" />
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="5 0.7 0" max="6 1 1" thickness=".05">
        (20) МЭ шина хранения #4: настройки по умолчанию.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="3.3 2.7 0.3" max="3.7 3 0.7" thickness=".05">
        Кварцевое волокно питает все 3 высекателя, поскольку высекатели действуют как кабели и передают энергию
  </BoxAnnotation>

<DiamondAnnotation pos="7 1.5 0.5" color="#00ff00">
        К основной сети
    </DiamondAnnotation>

  <IsometricCamera yaw="185" pitch="5" />
</GameScene>

## Настройки

* У <ItemLink id="pattern_provider" /> (1) настройки по умолчанию, установлены соответствующие <ItemLink id="processing_pattern" />.
  Обратите внимание: шаблоны преобразуют исходные ресурсы сразу в готовый процессор и **НЕ** содержат [пресс](../items-blocks-machines/presses.md).

  ![Шаблон логического процессора](../assets/diagrams/logic_pattern.png)
  ![Шаблон вычислительного процессора](../assets/diagrams/calculation_pattern.png)
  ![Шаблон инженерного процессора](../assets/diagrams/engineering_pattern.png)

* У <ItemLink id="storage_bus" /> (2, 17, 18, 20) настройки по умолчанию.
* У <ItemLink id="export_bus" /> (3–7) фильтры на соответствующие ингредиенты. Установлены 2 <ItemLink id="speed_card" />.
    <Row>
      <ItemImage id="silicon" scale="2" />
      <ItemImage id="minecraft:gold_ingot" scale="2" />
      <ItemImage id="certus_quartz_crystal" scale="2" />
      <ItemImage id="minecraft:diamond" scale="2" />
      <ItemImage id="minecraft:redstone" scale="2" />
    </Row>
* У <ItemLink id="import_bus" /> (13–16, 19) настройки по умолчанию. Установлены 2 <ItemLink id="speed_card" />.
* У <ItemLink id="inscriber" /> настройки по умолчанию. Установлены соответствующий [пресс](../items-blocks-machines/presses.md)
   и 4 <ItemLink id="speed_card" />.
   <Row>
     <ItemImage id="silicon_press" scale="2" />
     <ItemImage id="logic_processor_press" scale="2" />
     <ItemImage id="calculation_processor_press" scale="2" />
     <ItemImage id="engineering_processor_press" scale="2" />
   </Row>

## Принцип работы

1. <ItemLink id="pattern_provider" /> выталкивает ингредиенты в бочку.
2. Первая [трубная подсеть](pipe-subnet.md) (оранжевая) извлекает из бочки кремний, редстоуновую пыль и ингредиент нужного процессора
   (золотой слиток, кристалл истинного кварца или алмаз) и помещает их в соответствующий <ItemLink id="inscriber" />.
3. Первые четыре <ItemLink id="inscriber" /> производят <ItemLink id="printed_silicon" /> и <ItemLink id="printed_logic_processor" />,
   <ItemLink id="printed_calculation_processor" /> или <ItemLink id="printed_engineering_processor" />.
4. Вторая и третья [трубные подсети](pipe-subnet.md) (зелёные) извлекают печатные схемы из первых четырёх <ItemLink id="inscriber" />
    и помещают их в пятый <ItemLink id="inscriber" /> для окончательной сборки.
5. Пятый <ItemLink id="inscriber" /> собирает [процессор](../items-blocks-machines/processors.md).
6. Четвёртая [трубная подсеть](pipe-subnet.md) (фиолетовая) помещает процессор в поставщик шаблонов, возвращая его в основную сеть.
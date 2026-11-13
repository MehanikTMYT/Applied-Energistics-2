---
navigation:
  parent: example-setups/example-setups-index.md
  title: Опорожнение вёдер
  icon: minecraft:bucket
---

# Опорожнение вёдер

См. также [«Наполнение вёдер»](bucket-filler.md).

Поскольку здесь используется <ItemLink id="pattern_provider" />, эта схема предназначена для включения в систему [автокрафта](../ae2-mechanics/autocrafting.md)
сети.

Иногда нужна сама жидкость, но получить её можно только в ведре. Порой с этим помогает специальная машина
(например, жидкостный транспозер из Thermal Expansion), но мод с удобным решением есть не всегда. К счастью,
в обычном Minecraft есть чуть менее удобный способ — <ItemLink id="minecraft:dispenser" />.

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/bucket_emptier.snbt" />

<BoxAnnotation color="#dddddd" min="2 1 0" max="3 2 1">
        (1) МЭ поставщик шаблонов: блокировка создания — «С сигналом редстоуна», режим блокирования включён, установлены соответствующие шаблоны обработки.

        <Row>
        ![Шаблон наполнения](../assets/diagrams/water_empty_pattern_small.png)
        ![Шаблон наполнения](../assets/diagrams/lava_empty_pattern_small.png)
        </Row>
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="2.1 2 0.1" max="2.9 2.2 0.9">
        (2) МЭ интерфейс: настройки по умолчанию.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="3.1 2 1.1" max="3.9 2.2 1.9">
        (3) МЭ шина хранения #1: настройки по умолчанию.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="4.05 1.05 0.8" max="4.95 1.95 1">
        (4) МЭ плоскость уничтожения: интерфейса настройки нет.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="3.2 1.2 0.8" max="3.8 1.8 1">
        (5) МЭ шина импорта: фильтр настроен на вёдра.
        <ItemImage id="minecraft:bucket" scale="2" />
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="3 1.1 0.1" max="3.2 1.9 0.9">
        (6) МЭ шина хранения #2: настройки по умолчанию.
  </BoxAnnotation>

<DiamondAnnotation pos="0 1.5 0.5" color="#00ff00">
        К основной сети
    </DiamondAnnotation>

  <IsometricCamera yaw="225" pitch="45" />
</GameScene>

## Настройки

* У <ItemLink id="pattern_provider" /> (1) блокировка создания установлена на «С сигналом редстоуна», включён режим блокирования,
  установлены соответствующие <ItemLink id="processing_pattern" />.
  
    ![Шаблон зарядника](../assets/diagrams/water_empty_pattern.png)
    ![Шаблон зарядника](../assets/diagrams/lava_empty_pattern.png)

* <ItemLink id="interface" /> (2) имеет настройки по умолчанию.
* Первая <ItemLink id="storage_bus" /> (3) имеет настройки по умолчанию.
* <ItemLink id="annihilation_plane" /> (4) не имеет интерфейса настройки.
* <ItemLink id="import_bus" /> (5) настроена на фильтрацию вёдер.
  <ItemImage id="minecraft:bucket" scale="2" />
* Вторая <ItemLink id="storage_bus" /> (6) имеет настройки по умолчанию.

## Принцип работы

1. <ItemLink id="pattern_provider" /> отправляет ингредиенты в <ItemLink id="interface" />.
   (На самом деле для оптимизации он отправляет их прямо через шину хранения, словно она продолжает грани поставщика. Предметы вообще не попадают в интерфейс.)
2. Благодаря механизмам, описанным в разделе [«Подсети-трубопроводы»](pipe-subnet.md#providing-to-multiple-places),
   ведро попадает в <ItemLink id="minecraft:dispenser" />.
3. <ItemLink id="minecraft:comparator" /> обнаруживает ведро в раздатчике и одновременно подаёт сигнал раздатчику и блокирует
   <ItemLink id="pattern_provider" />.
4. Раздатчик выливает жидкость из ведра, и внутри него остаётся пустое ведро.
5. <ItemLink id="import_bus" /> извлекает пустое ведро из раздатчика и через
   <ItemLink id="storage_bus" /> помещает его в поставщик шаблонов, возвращая в основную сеть.
6. Компаратор обнаруживает, что раздатчик пуст, и разблокирует поставщик.
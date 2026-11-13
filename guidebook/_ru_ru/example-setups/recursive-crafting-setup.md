---
navigation:
  parent: example-setups/example-setups-index.md
  title: Рекурсивное изготовление
  icon: minecraft:netherite_upgrade_smithing_template
---

# Схема рекурсивного изготовления

Как сказано в разделе об [автоматическом создании](../ae2-mechanics/autocrafting.md), алгоритм планирования не поддерживает рецепты,
в которых основной результат одновременно является ингредиентом. Например, он не может клонировать <ItemLink id="minecraft:netherite_upgrade_smithing_template" />.

Один из способов решения — использовать способность <ItemLink id="level_emitter" /> имитировать [шаблон](../items-blocks-machines/patterns.md).

Это позволит включать небольшую установку, непрерывно выполняющую рецепт. Здесь рассмотрим схему
клонирования <ItemLink id="minecraft:netherite_upgrade_smithing_template" />.

<RecipeFor id="minecraft:netherite_upgrade_smithing_template" />

***

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/recursive_recipe_setup.snbt" />

  <BoxAnnotation color="#dddddd" min="1 0 0" max="2 1 1">
        (1) МЭ интерфейс: поддерживает запас дополнительных ингредиентов — алмазов и незеррака.
        <Row><ItemImage id="minecraft:diamond" scale="2" /> <ItemImage id="minecraft:netherrack" scale="2" /></Row>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="2.3 1 0.3" max="2.7 1.3 0.7">
        (2) МЭ излучатель уровня: выбран «кузнечный шаблон незеритового улучшения», включён режим «Выдавать сигнал редстоуна для изготовления предмета».
        <Row><ItemImage id="minecraft:netherite_upgrade_smithing_template" scale="2" /> <ItemImage id="crafting_card" scale="2" /></Row>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="2 0 0" max="2.3 1 1">
        (3) МЭ шина импорта #1: фильтр на предметы, запас которых поддерживает интерфейс. Установлена редстоуновая карта. Режим редстоуна —
        «Активен с сигналом».
        <Row>
        <ItemImage id="minecraft:diamond" scale="2" />
        <ItemImage id="minecraft:netherrack" scale="2" />
        <ItemImage id="redstone_card" scale="2" />
        </Row>
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="3 1 1" max="4 1.3 2">
        (4) МЭ шина хранения #1: приоритет выше, чем у другой шины хранения. ОЧЕНЬ ВАЖНО.
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="3 0 1" max="4 1 2">
        (5) Молекулярный сборщик: установлен шаблон копирования кузнечного шаблона.

        ![Шаблон](../assets/diagrams/smithing_template_pattern_small.png)

        При постройке установки в него также вручную помещается один кузнечный шаблон.
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="2.7 0 1" max="3 1 2">
        (6) МЭ шина импорта #2: настройки по умолчанию.
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="1 0 1" max="2 1 1.3">
        (7) МЭ шина хранения #2: фильтр на «кузнечный шаблон незеритового улучшения». Приоритет ниже, чем у другой шины хранения.
        <ItemImage id="minecraft:netherite_upgrade_smithing_template" scale="2" />
  </BoxAnnotation>

<DiamondAnnotation pos="0 0.5 0.5" color="#00ff00">
        К основной сети
    </DiamondAnnotation>

  <IsometricCamera yaw="15" pitch="30" />
</GameScene>

## Настройки

* <ItemLink id="interface" /> (1) поддерживает запас дополнительных ингредиентов — алмазов и незеррака.
* В <ItemLink id="level_emitter" /> (2) выбран «кузнечный шаблон незеритового улучшения» и режим «Выдавать сигнал редстоуна для изготовления предмета».
* У первой <ItemLink id="import_bus" /> (3) фильтр на предметы, запас которых поддерживает интерфейс. Установлена редстоуновая карта. Выбран режим «Активен с сигналом».
* У первой <ItemLink id="storage_bus" /> (4) [приоритет](../ae2-mechanics/import-export-storage.md#storage-priority) *выше*, чем у второй шины хранения.
* В <ItemLink id="molecular_assembler" /> (5) установлен шаблон копирования кузнечного шаблона и вручную помещён один кузнечный шаблон.

  ![Шаблон](../assets/diagrams/smithing_template_pattern.png)

* У второй <ItemLink id="import_bus" /> (6) настройки по умолчанию.
* У второй <ItemLink id="storage_bus" /> (7) фильтр на «кузнечный шаблон незеритового улучшения». Её [приоритет](../ae2-mechanics/import-export-storage.md#storage-priority) *ниже*, чем у первой шины хранения.

## Принцип работы

1. <ItemLink id="level_emitter" /> имитирует [шаблон](../items-blocks-machines/patterns.md) благодаря установленной
   <ItemLink id="crafting_card" /> и режиму «Выдавать сигнал редстоуна для изготовления предмета». Поэтому «кузнечный шаблон незеритового улучшения» появляется в
   [терминалах](../items-blocks-machines/terminals.md) как предмет, доступный для [автоматического создания](../ae2-mechanics/autocrafting.md).
2. При получении запроса на изготовление этого предмета от игрока или самой системы излучатель уровня включается.
3. Первая <ItemLink id="import_bus" /> активируется излучателем уровня и извлекает ингредиенты, запас которых поддерживает <ItemLink id="interface" />.
4. Единственная <ItemLink id="storage_bus" /> в сети, способная хранить эти ингредиенты, установлена на сборщике.
5. <ItemLink id="molecular_assembler" /> получает ингредиенты (внутри уже есть 1 кузнечный шаблон) и выполняет рецепт, производя 2 кузнечных шаблона.
6. Вторая <ItemLink id="import_bus" /> извлекает 1 кузнечный шаблон.
7. У первой шины хранения приоритет выше, поэтому этот кузнечный шаблон возвращается в сборщик.
8. Вторая <ItemLink id="import_bus" /> извлекает 1 кузнечный шаблон.
9. Сборщик не может принять ещё один кузнечный шаблон, поэтому второй шаблон поступает через шину хранения с меньшим приоритетом в интерфейс.
10. <ItemLink id="interface" /> не настроен на поддержание запаса кузнечных шаблонов и отправляет его в сеть.
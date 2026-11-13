---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: Ячейки хранения
  icon: item_storage_cell_1k
  position: 410
categories:
- tools
item_ids:
- ae2:item_cell_housing
- ae2:fluid_cell_housing
- ae2:cell_component_1k
- ae2:cell_component_4k
- ae2:cell_component_16k
- ae2:cell_component_64k
- ae2:cell_component_256k
- ae2:item_storage_cell_1k
- ae2:item_storage_cell_4k
- ae2:item_storage_cell_16k
- ae2:item_storage_cell_64k
- ae2:item_storage_cell_256k
- ae2:fluid_storage_cell_1k
- ae2:fluid_storage_cell_4k
- ae2:fluid_storage_cell_16k
- ae2:fluid_storage_cell_64k
- ae2:fluid_storage_cell_256k
---

# Ячейки хранения

<Column>
  <Row>
    <ItemImage id="item_storage_cell_1k" scale="4" />

    <ItemImage id="item_storage_cell_4k" scale="4" />

    <ItemImage id="item_storage_cell_16k" scale="4" />

    <ItemImage id="item_storage_cell_64k" scale="4" />

    <ItemImage id="item_storage_cell_256k" scale="4" />
  </Row>

  <Row>
    <ItemImage id="fluid_storage_cell_1k" scale="4" />

    <ItemImage id="fluid_storage_cell_4k" scale="4" />

    <ItemImage id="fluid_storage_cell_16k" scale="4" />

    <ItemImage id="fluid_storage_cell_64k" scale="4" />

    <ItemImage id="fluid_storage_cell_256k" scale="4" />
  </Row>
</Column>

Ячейки хранения — один из основных способов хранения в Applied Energistics. Они устанавливаются в <ItemLink id="drive" />
или <ItemLink id="me_chest" />.

Объяснение вместимости в байтах и типах см. в разделе [«Байты и типы»](../ae2-mechanics/bytes-and-types.md).

Ячейки можно разобрать обратно на компонент хранения и корпус: сначала опустошите ячейку, затем нажмите правую кнопку мыши с зажатым Shift, держа ячейку в руке.

<Row>
    <Recipe id="upgrade/item_storage_cell_1k_to_4k" />

    Ячейку хранения можно улучшить, объединив её с компонентом хранения более высокого уровня в сетке изготовления. Содержимое сохранится, а компонент более низкого уровня вернётся.
</Row>

## Вместимость при разном количестве типов

[Затраты на хранение типов](../ae2-mechanics/bytes-and-types.md) таковы, что ячейка с 1 типом вмещает в 2 раза больше предметов, чем ячейка, в которой используются все 63 типа.

| Ячейка                                     | Полная вместимость ячейки при использовании 1 типа | Полная вместимость ячейки при использовании 63 типов |
| ---------------------------------------- | ----------------------------------------: | ------------------------------------------: |
| <ItemLink id="item_storage_cell_1k" />   |                                     8,128 |                                       4,160 |
| <ItemLink id="item_storage_cell_4k" />   |                                    32,512 |                                      16,640 |
| <ItemLink id="item_storage_cell_16k" />  |                                   130,048 |                                      66,560 |
| <ItemLink id="item_storage_cell_64k" />  |                                   520,192 |                                     266,240 |
| <ItemLink id="item_storage_cell_256k" /> |                                 2,080,768 |                                   1,064,960 |


## Разделение

Для ячеек можно задать фильтр, чтобы они принимали только определённые предметы, аналогично фильтрации в <ItemLink id="storage_bus" />. Это
делается в <ItemLink id="cell_workbench" />.

Предметы можно перетаскивать в слоты из JEI/REI, даже если этих предметов нет в наличии.

## Улучшения

Ячейки хранения поддерживают следующие [улучшения](upgrade_cards.md), устанавливаемые через <ItemLink id="cell_workbench" />:

*   <ItemLink id="fuzzy_card" /> (недоступна для жидкостных ячеек) позволяет задавать фильтр по степени повреждения и/или игнорировать NBT предметов
*   <ItemLink id="inverter_card" /> переключает фильтр с белого списка на чёрный
*   <ItemLink id="equal_distribution_card" /> выделяет каждому типу одинаковое количество байтов, чтобы один тип не мог заполнить всю ячейку
*   <ItemLink id="void_card" /> уничтожает поступающие предметы, если ячейка заполнена (либо заполнено место, выделенное этому типу
    картой равномерного распределения). Помогает избежать переполнения ферм. Обязательно настройте фильтр!
*   В переносные ячейки можно установить <ItemLink id="energy_card" /> для увеличения ёмкости аккумулятора

## Окрашивание

Переносные предметные и жидкостные ячейки можно окрашивать подобно кожаной броне, объединяя их с красителями в сетке изготовления.

# Корпуса

Ячейки можно изготовить из компонента хранения и корпуса либо расположить ингредиенты рецепта корпуса вокруг компонента хранения:

<Row>
  <Recipe id="network/cells/item_storage_cell_1k" />

  <Recipe id="network/cells/item_storage_cell_1k_storage" />
</Row>

Сами корпуса изготавливаются так:

<Row>
  <RecipeFor id="item_cell_housing" />

  <RecipeFor id="fluid_cell_housing" />
</Row>

# Компоненты хранения

Компоненты хранения — основа всех ячеек AE2, определяющая их вместимость. Каждый следующий уровень увеличивает вместимость
в 4 раза и требует 3 компонента предыдущего уровня.

<Column>
  <Row>
    <RecipeFor id="cell_component_1k" />

    <RecipeFor id="cell_component_4k" />

    <RecipeFor id="cell_component_16k" />
  </Row>

  <Row>
    <RecipeFor id="cell_component_64k" />

    <RecipeFor id="cell_component_256k" />
  </Row>
</Column>

# Предметные ячейки хранения

Предметные ячейки хранения вмещают до 63 различных типов предметов и доступны во всех стандартных вариантах вместимости.

<Column>
  <Row>
    <Recipe id="network/cells/item_storage_cell_1k_storage" />

    <Recipe id="network/cells/item_storage_cell_4k_storage" />

    <Recipe id="network/cells/item_storage_cell_16k_storage" />
  </Row>

  <Row>
    <Recipe id="network/cells/item_storage_cell_64k_storage" />

    <Recipe id="network/cells/item_storage_cell_256k_storage" />
  </Row>
</Column>

## Переносное предметное хранилище

Работают как маленький <ItemLink id="me_chest" /> в кармане или своего рода рюкзак. Их можно заряжать в <ItemLink id="charger" />

В отличие от стандартных ячеек хранения, у них с увеличением объёма в байтах число доступных типов *уменьшается*, а общий
объём в байтах вдвое меньше.

Помимо карт улучшения, доступных всем ячейкам, они также принимают <ItemLink id="energy_card" /> для увеличения ёмкости встроенного аккумулятора.

<Column>
  <Row>
    <RecipeFor id="portable_item_cell_1k" />

    <RecipeFor id="portable_item_cell_4k" />

    <RecipeFor id="portable_item_cell_16k" />
  </Row>

  <Row>
    <RecipeFor id="portable_item_cell_64k" />

    <RecipeFor id="portable_item_cell_256k" />
  </Row>
</Column>

# Жидкостные ячейки хранения

Жидкостные ячейки хранения вмещают до 5 различных типов жидкостей и доступны во всех стандартных вариантах вместимости.

<Column>
  <Row>
    <Recipe id="network/cells/fluid_storage_cell_1k_storage" />

    <Recipe id="network/cells/fluid_storage_cell_4k_storage" />

    <Recipe id="network/cells/fluid_storage_cell_16k_storage" />
  </Row>

  <Row>
    <Recipe id="network/cells/fluid_storage_cell_64k_storage" />

    <Recipe id="network/cells/fluid_storage_cell_256k_storage" />
  </Row>
</Column>

## Переносное жидкостное хранилище

Работают как маленький <ItemLink id="me_chest" /> в кармане или своего рода рюкзак. Их можно заряжать в <ItemLink id="charger" />

В отличие от стандартных ячеек хранения, у них с увеличением объёма в байтах число доступных типов *уменьшается*, а общий
объём в байтах вдвое меньше.

Помимо карт улучшения, доступных всем ячейкам, они также принимают <ItemLink id="energy_card" /> для увеличения ёмкости встроенного аккумулятора.

<Column>
  <Row>
    <RecipeFor id="portable_fluid_cell_1k" />

    <RecipeFor id="portable_fluid_cell_4k" />

    <RecipeFor id="portable_fluid_cell_16k" />
  </Row>

  <Row>
    <RecipeFor id="portable_fluid_cell_64k" />

    <RecipeFor id="portable_fluid_cell_256k" />
  </Row>
</Column>

# Творческая МЭ ячейка хранения

<Row>
  <ItemImage id="creative_storage_cell" scale="2" />
</Row>

Творческие ячейки **не предоставляют бесконечное хранилище**. Они работают как бесконечные источники и приёмники
предметов или жидкостей, выбранных при [настройке фильтра](cell_workbench.md).

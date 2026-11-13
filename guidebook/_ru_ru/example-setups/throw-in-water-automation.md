---
navigation:
  parent: example-setups/example-setups-index.md
  title: Автоматизация рецептов в воде
  icon: fluix_crystal
---

# Автоматизация рецептов с бросанием предметов в воду

Поскольку здесь используется <ItemLink id="pattern_provider" />, схема предназначена для включения в систему [автоматического создания](../ae2-mechanics/autocrafting.md)
предметов.

Некоторые рецепты требуют бросать предметы в воду (похожую схему можно использовать и для бросания в другие места).
Это можно автоматизировать с помощью <ItemLink id="formation_plane" />, <ItemLink id="annihilation_plane" /> и вспомогательных
устройств (по сути, это 2 изменённые [трубные подсети](pipe-subnet.md)).

Эту схему предполагается использовать вместе с [автоматизированным зарядником](charger-automation.md), который поставляет <ItemLink id="charged_certus_quartz_crystal" />.

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/throw_in_water.snbt" />

<BoxAnnotation color="#dddddd" min="2 0 1" max="3 1 2">
        (1) МЭ поставщик шаблонов: настройки по умолчанию, установлены соответствующие шаблоны обработки.

        ![Шаблон флюисового кристалла](../assets/diagrams/fluix_pattern_small.png) ![Шаблон несовершенного цветущего блока](../assets/diagrams/flawed_budding_pattern_small.png)
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="1.7 0 1" max="2 1 2">
        (2) МЭ интерфейс: настройки по умолчанию.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="1 .7 1" max="2 1 2">
        (3) МЭ плоскость формирования: настроена на выбрасывание входящих предметов.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="1 2 1" max="2 2.3 2">
        (4) МЭ плоскость уничтожения: интерфейса настройки нет.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="2 1 1" max="3 1.3 2">
        (5) МЭ шина хранения: фильтр на результаты шаблонов
        <Row><ItemImage id="fluix_crystal" scale="2" /><BlockImage id="flawless_budding_quartz" scale="2" /></Row>
  </BoxAnnotation>

<DiamondAnnotation pos="3.9 0.5 1.5" color="#00ff00">
        К основной сети и автоматизированному заряднику
        <GameScene zoom="3" background="transparent">
          <ImportStructure src="../assets/assemblies/charger_automation.snbt" />
          <IsometricCamera yaw="195" pitch="30" />
        </GameScene>
    </DiamondAnnotation>

  <IsometricCamera yaw="180" pitch="0" />
</GameScene>

## Настройки и шаблоны

* У <ItemLink id="pattern_provider" /> (1) настройки по умолчанию, установлены соответствующие <ItemLink id="processing_pattern" />
  * Для <ItemLink id="fluix_crystal" /> подходит стандартный рецепт из JEI/REI:

    ![Шаблон флюисового кристалла](../assets/diagrams/fluix_pattern.png)

  * <ItemLink id="flawed_budding_quartz" /> лучше создавать непосредственно из <ItemLink id="quartz_block" />,
    чтобы избежать ситуации, когда ингредиент одного рецепта является результатом другого и шина хранения не может отфильтровать их:

    ![Шаблон несовершенного цветущего блока](../assets/diagrams/flawed_budding_pattern.png)

* У <ItemLink id="interface" /> (2) настройки по умолчанию.
* <ItemLink id="formation_plane" /> (3) настроена на выбрасывание входящих предметов.
* У <ItemLink id="annihilation_plane" /> (4) нет интерфейса и возможности настройки.
* У <ItemLink id="storage_bus" /> (5) фильтр на результаты шаблонов.

## Принцип работы

1.  <ItemLink id="pattern_provider" /> выталкивает ингредиенты в расположенный сбоку <ItemLink id="interface" /> зелёной подсети
2.  Интерфейс (по умолчанию не настроенный на хранение чего-либо) пытается отправить содержимое в [сетевое хранилище](../ae2-mechanics/import-export-storage.md)
3.  Единственное хранилище в зелёной подсети — <ItemLink id="formation_plane" />, которая выбрасывает полученные предметы в воду
4.  <ItemLink id="annihilation_plane" /> в оранжевой подсети пытается подобрать выброшенные предметы, но не может, поскольку
    у <ItemLink id="storage_bus" /> над поставщиком шаблонов (единственного хранилища в оранжевой подсети) фильтр настроен только на результаты возможных рецептов
5.  Предметы преобразуются непосредственно в мире.
6.  Теперь плоскость уничтожения может подобрать предметы перед собой, поскольку шине хранения разрешено их сохранять.
7.  Шина хранения помещает полученные предметы в поставщик шаблонов, возвращая их в сеть.

---
navigation:
  parent: example-setups/example-setups-index.md
  title: Автоматизация зарядника
  icon: charger
---

# Автоматизация зарядника

Поскольку здесь используется <ItemLink id="pattern_provider" />, эта схема предназначена для включения в систему [автокрафта](../ae2-mechanics/autocrafting.md)
сети. Для независимой автоматизации <ItemLink id="charger" /> достаточно воронок, сундуков и подобных средств.

Автоматизировать <ItemLink id="charger" /> довольно просто. <ItemLink id="pattern_provider" /> отправляет ингредиент в зарядник, затем [подсеть-трубопровод](pipe-subnet.md)
или другая предметная труба возвращает результат в поставщик.

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/charger_automation.snbt" />

<BoxAnnotation color="#dddddd" min="1 0 0" max="2 1 1">
        (1) МЭ поставщик шаблонов: настройки по умолчанию, установлены соответствующие шаблоны обработки. Также снабжает зарядник энергией.

        ![Шаблон зарядника](../assets/diagrams/charger_pattern_small.png)
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="0 1 0" max="1 1.3 1">
        (2) МЭ шина импорта: настройки по умолчанию.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="1 1 0" max="2 1.3 1">
        (3) МЭ шина хранения: настройки по умолчанию.
  </BoxAnnotation>

<DiamondAnnotation pos="4 0.5 0.5" color="#00ff00">
        К основной сети
    </DiamondAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Настройки

* <ItemLink id="pattern_provider" /> (1) имеет настройки по умолчанию, установлены соответствующие <ItemLink id="processing_pattern" />.
  Он также снабжает <ItemLink id="charger" /> [энергией](../ae2-mechanics/energy.md), поскольку действует как [кабель](../items-blocks-machines/cables.md).
  
    ![Шаблон зарядника](../assets/diagrams/charger_pattern.png)

* <ItemLink id="import_bus" /> (2) имеет настройки по умолчанию.
* <ItemLink id="storage_bus" /> (3) имеет настройки по умолчанию.

## Принцип работы

1. <ItemLink id="pattern_provider" /> отправляет ингредиенты в <ItemLink id="charger" />.
2. Зарядник заряжает предмет.
3. <ItemLink id="import_bus" /> в зелёной подсети извлекает результат из зарядника и пытается поместить его в
   [сетевое хранилище](../ae2-mechanics/import-export-storage.md).
4. Единственное хранилище зелёной подсети — <ItemLink id="storage_bus" />, которая помещает полученные предметы в поставщик шаблонов, возвращая их в основную сеть.

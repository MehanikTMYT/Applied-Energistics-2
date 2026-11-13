---
navigation:
  parent: example-setups/example-setups-index.md
  title: Специализированное локальное хранилище
  icon: drive
---

# Специализированное локальное хранилище

Благодаря одному из [особых свойств МЭ интерфейса](../items-blocks-machines/interface.md#special-interactions)
[подсеть](../ae2-mechanics/subnetworks.md) может предоставлять основной сети содержимое своего хранилища, не получая при этом
доступа к хранилищу основной сети и занимая всего 1 [канал](../ae2-mechanics/channels.md).

Это удобно для локального хранилища фермы: её продукция не будет переполнять основное хранилище.

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/local_storage.snbt" />

<BoxAnnotation color="#dddddd" min="4 0 0" max="5 2 1">
        (1) Любое средство импорта предметов (в данном случае МЭ интерфейс)
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="3 0 0" max="4 1 1">
        (2) МЭ накопитель: установлены ячейки. В ячейках должны быть фильтры на продукцию фермы.
        В ячейки можно установить карты равномерного распределения и пустотные карты.
        <Row><ItemImage id="item_storage_cell_4k" scale="2" /> <ItemImage id="equal_distribution_card" scale="2" /> <ItemImage id="void_card" scale="2" /></Row>
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="3 1 0" max="4 2 0.3">
        (3) МЭ терминал изготовления: видит содержимое накопителя в подсети, но не хранилище основной сети.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="2 0 0" max="2.3 1 1">
        (4) МЭ интерфейс #2: настройки по умолчанию.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="1.7 0 0" max="2 1 1">
        (5) МЭ шина хранения: приоритет выше, чем у основного хранилища; можно настроить фильтр на продукцию фермы.
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="1 1 0" max="2 2 0.3">
        МЭ терминал изготовления: видит содержимое хранилища основной сети *и* подсети.
  </BoxAnnotation>

<DiamondAnnotation pos="0 0.5 0.5" color="#00ff00">
        К основной сети
    </DiamondAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Настройки

* Первый <ItemLink id="interface" /> (1) просто принимает предметы с фермы и отправляет их в подсеть.
* В <ItemLink id="drive" /> (2) установлены [ячейки](../items-blocks-machines/storage_cells.md). Их следует
  [разметить](../items-blocks-machines/cell_workbench.md) под продукцию фермы.
  В ячейки можно установить <ItemLink id="equal_distribution_card" /> и <ItemLink id="void_card" />.
* У второго <ItemLink id="interface" /> (4) настройки по умолчанию.
* У <ItemLink id="storage_bus" /> [приоритет](../ae2-mechanics/import-export-storage.md#storage-priority)
  выше, чем у основного хранилища. Можно настроить фильтр на продукцию фермы.

## Принцип работы

* <ItemLink id="interface" /> в подсети показывает <ItemLink id="storage_bus" /> основной сети содержимое
<ItemLink id="drive" />. Это позволяет шине хранения напрямую извлекать предметы из ячеек накопителя и помещать их туда.
* У шины хранения высокий [приоритет](../ae2-mechanics/import-export-storage.md#storage-priority), чтобы предметы в первую очередь
  возвращались в подсеть, а не в основное хранилище.
* Если ячейки подсети заполнятся, предметы не начнут поступать в основную сеть. Если переполнение
мешает работе фермы, для удаления излишков можно использовать <ItemLink id="void_card" />.
* Если ферма производит несколько видов предметов, <ItemLink id="equal_distribution_card" /> не дадут одному виду заполнить все ячейки,
не оставив места для других.
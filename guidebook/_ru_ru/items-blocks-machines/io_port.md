---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: МЭ порт ввода/вывода
  icon: io_port
  position: 210
categories:
- devices
item_ids:
- ae2:io_port
---

# МЭ порт ввода/вывода

<BlockImage id="io_port" p:powered="true" scale="8" />

Порт ввода/вывода позволяет быстро заполнять [ячейки хранения](../items-blocks-machines/storage_cells.md) из
[сетевого хранилища](../ae2-mechanics/import-export-storage.md) или выгружать их содержимое в него.

Его можно поворачивать с помощью <ItemLink id="certus_quartz_wrench" />.

## Настройки

*   Порт ввода/вывода можно настроить на перемещение ячейки в выходные слоты, когда она пуста, заполнена или работа завершена.
*   Если установлена <ItemLink id="redstone_card" />, доступны различные условия управления редстоуном
*   Стрелка в центре интерфейса задаёт направление переноса предметов: из ячейки в [сетевое хранилище](../ae2-mechanics/import-export-storage.md)
    или из хранилища в ячейку.

## Улучшения

Порт ввода/вывода поддерживает следующие [улучшения](upgrade_cards.md):

*   <ItemLink id="speed_card" /> увеличивает количество ресурсов, переносимых за операцию
*   <ItemLink id="redstone_card" /> добавляет управление редстоуном: работа при высоком сигнале, низком сигнале или однократно на каждый импульс

## Рецепт

<RecipeFor id="io_port" />

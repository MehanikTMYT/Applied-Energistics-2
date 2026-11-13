---
navigation:
  parent: example-setups/example-setups-index.md
  title: Поддержание запасов с помощью интерфейса
  icon: interface
---

# Поддержание запасов с помощью интерфейса

Может возникнуть вопрос: «Как поддерживать определённый запас разных предметов, изготавливая новые по мере необходимости?»

Один из способов — использовать <ItemLink id="interface" /> и <ItemLink id="crafting_card" /> для автоматического запроса новых предметов
через [автокрафт](../ae2-mechanics/autocrafting.md) сети. Эта схема лучше подходит для поддержания небольшого запаса множества
разных предметов.

Демонстрационная схема сокращена, чтобы не быть слишком широкой. Вероятно, оптимальнее использовать 4 <ItemLink id="interface" /> и 4 <ItemLink id="storage_bus" />,
чтобы задействовать все 8 [каналов](../ae2-mechanics/channels.md) обычного [кабеля](../items-blocks-machines/cables.md).

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/interface_autostocking.snbt" />

<BoxAnnotation color="#dddddd" min="0 0 0" max="2 1 1">
        (1) МЭ интерфейсы: настроены на поддержание запаса нужных предметов внутри себя. Установлены карты изготовления.
        <ItemImage id="crafting_card" scale="2" />
  </BoxAnnotation>

<BoxAnnotation color="#dddddd" min="0 1 0" max="2 1.3 1">
        (2) МЭ шины хранения: «Режим ввода/вывода» установлен на «Только извлечение».
  </BoxAnnotation>

<DiamondAnnotation pos="4 0.5 0.5" color="#00ff00">
        К основной сети
    </DiamondAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Настройки

* <ItemLink id="interface" /> (1) настроены на поддержание запаса нужных предметов: поместите образцы в
   верхние слоты щелчком или перетащите их туда из JEI, затем нажмите значок гаечного ключа над слотами, чтобы задать количество. В них установлены <ItemLink id="crafting_card" />.
* У <ItemLink id="storage_bus" /> (2) «Режим ввода/вывода» установлен на «Только извлечение».

## Принцип работы

1. Если <ItemLink id="interface" /> не может получить достаточно указанного предмета из [сетевого хранилища](../ae2-mechanics/import-export-storage.md)
   (и в нём установлена <ItemLink id="crafting_card" />), он запрашивает изготовление дополнительных предметов через [автокрафт](../ae2-mechanics/autocrafting.md) сети.
2. <ItemLink id="storage_bus" /> дают сети доступ к содержимому интерфейсов.
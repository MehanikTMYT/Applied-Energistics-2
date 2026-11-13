---
navigation:
  parent: example-setups/example-setups-index.md
  title: Автоматическая добыча руды с «Удачей»
  icon: minecraft:raw_iron
---

# Автоматизация добычи руды с «Удачей»

На <ItemLink id="annihilation_plane" /> можно наложить любые чары для кирки, включая «Удачу», поэтому очевидное применение —
зачаровать несколько таких плоскостей на «Удачу» и с помощью <ItemLink id="formation_plane" /> и <ItemLink id="annihilation_plane" /> быстро устанавливать и
разрушать руду.

Поскольку <ItemLink id="import_bus" /> постепенно набирают скорость, установка сначала работает медленно, а через несколько секунд выходит на полную мощность.

<GameScene zoom="6" interactive={true}>
  <ImportStructure src="../assets/assemblies/ore_fortuner.snbt" />

  <BoxAnnotation color="#dddddd" min="2.7 0 2" max="3 1 3">
        (1) МЭ шина импорта: установлено несколько карт ускорения.
        <ItemImage id="speed_card" scale="2" />
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="0 0 2" max="2 1 2.3">
        (2) МЭ плоскости формирования: настройки по умолчанию.
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="0 0 0.7" max="2 1 1">
        (3) МЭ плоскости уничтожения: интерфейса настройки нет, но наложены чары «Удача».
  </BoxAnnotation>

  <BoxAnnotation color="#dddddd" min="2.7 0 0" max="3 1 1">
        (4) МЭ шина хранения: настройки по умолчанию.
  </BoxAnnotation>

<DiamondAnnotation pos="3.5 0.5 2.5" color="#00ff00">
        Вход
    </DiamondAnnotation>

<DiamondAnnotation pos="3.5 0.5 0.5" color="#00ff00">
        Выход
    </DiamondAnnotation>

<DiamondAnnotation pos="4 0.5 1.5" color="#00ff00">
        К основной сети
    </DiamondAnnotation>

  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Настройки

*   В <ItemLink id="import_bus" /> (1) установлено несколько <ItemLink id="speed_card" />. Чем больше плоскостей формирования
    в массиве, тем больше нужно карт, поскольку они позволяют шине импорта забирать больше предметов за раз.
*   У <ItemLink id="formation_plane" /> (2) настройки по умолчанию.
*   У <ItemLink id="annihilation_plane" /> (3) нет интерфейса и возможности настройки, но наложены чары «Удача».
*   У <ItemLink id="storage_bus" /> (4) настройки по умолчанию.

## Принцип работы

1.  <ItemLink id="import_bus" /> в зелёной подсети импортирует блоки из первой бочки в [сетевое хранилище](../ae2-mechanics/import-export-storage.md)
2.  Единственное хранилище в зелёной подсети — <ItemLink id="formation_plane" />, которая устанавливает блоки.
3.  <ItemLink id="annihilation_plane" /> в оранжевой подсети разрушает блоки с действием чар «Удача».
4.  <ItemLink id="storage_bus" /> в оранжевой подсети сохраняет добытые предметы во второй бочке.

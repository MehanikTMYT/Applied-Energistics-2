---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: Зарядник
  icon: charger
  position: 310
categories:
- machines
item_ids:
- ae2:charger
---

# Зарядник

<BlockImage id="charger" scale="8" />

Зарядник позволяет заряжать
совместимые инструменты и <ItemLink id="certus_quartz_crystal" />.

Энергию можно подавать сверху или снизу по [кабелям](cables.md) AE2 или энергетическим кабелям других модов. Он
принимает как энергию AE2 (AE), так и Forge Energy (FE). Предметы можно подавать и извлекать с любой стороны. Извлекаются только готовые результаты,
поэтому фильтры, предотвращающие извлечение обычных кристаллов вместо заряженных, не нужны. Для удобства автоматизации зарядник можно поворачивать
с помощью <ItemLink id="certus_quartz_wrench" />.

Позволяет получать <ItemLink id="charged_certus_quartz_crystal" />
из <ItemLink id="certus_quartz_crystal" />, а <ItemLink id="meteorite_compass" /> — из <ItemLink id="minecraft:compass" />.

Для ручного питания установите <ItemLink id="crank" /> сверху или снизу и нажимайте на неё правой кнопкой, пока предмет не зарядится.

Также служит рабочим местом [исследователя флюиса](fluix_researcher.md).

## Простая автоматизация

Например, возможность поворота позволяет частично автоматизировать зарядники следующим образом:

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/charger_hopper.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Рецепт

<RecipeFor id="charger" />

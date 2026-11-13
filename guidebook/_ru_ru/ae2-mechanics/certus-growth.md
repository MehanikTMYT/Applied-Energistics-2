---
navigation:
  parent: ae2-mechanics/ae2-mechanics-index.md
  title: Рост истинного кварца
  icon: quartz_cluster
---

# Рост истинного кварца

## По сути, просто скопировано со страницы первых шагов

<GameScene zoom="6" background="transparent">
<ImportStructure src="../assets/assemblies/budding_certus_1.snbt" />
</GameScene>

Почки истинного кварца появляются на [цветущих блоках истинного кварца](../items-blocks-machines/budding_certus.md), подобно аметисту. Если сломать ещё не выросшую
почку, выпадет одна единица <ItemLink id="certus_quartz_dust" />, и «Удача» на это не влияет. Из полностью выросшей друзы выпадут четыре
кристалла <ItemLink id="certus_quartz_crystal" />, а «Удача» увеличит их количество.

Есть 4 ступени цветущих блоков истинного кварца: безупречный, несовершенный, потресканный и поврежденный. Впервые они
встречаются в [метеоритах](../ae2-mechanics/meteorites.md).

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/budding_blocks.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Каждый раз, когда почка переходит на следующую стадию роста, цветущий блок может ухудшиться на одну ступень и в итоге превратиться
в обычный блок истинного кварца. Его можно восстановить (или создать новый цветущий блок), бросив цветущий блок (или
блок истинного кварца) в воду вместе с одним или несколькими <ItemLink id="charged_certus_quartz_crystal" />.

<RecipeFor id="damaged_budding_quartz" />

Безупречные цветущие блоки истинного кварца не портятся и производят кварц бесконечно. Однако их нельзя создать или перенести
киркой, даже с «Шёлковым касанием». (Но их *можно* перемещать с помощью [пространственного хранения](../ae2-mechanics/spatial-io.md).)

Сами по себе почки истинного кварца растут очень медленно. К счастью, <ItemLink id="growth_accelerator" /> значительно
ускоряет этот процесс, если поставить его рядом с цветущим блоком. В первую очередь стоит создать несколько таких устройств.

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/budding_certus_2.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Из-за взаимодействия механик каждая закрытая грань цветущего блока снижает его суммарную скорость роста,
и в итоге это перевешивает пользу дополнительных ускорителей. Практические испытания показывают следующее:

![Предметы/мин при разном соотношении](../assets/diagrams/certus_farm_speed_chart_1.png)

![Распространённые установки](../assets/diagrams/certus_farm_speed_chart_2.png)

Если кварца не хватает ещё и на <ItemLink id="energy_acceptor" /> или <ItemLink id="vibration_chamber" />,
можно создать <ItemLink id="crank" /> и прикрепить её к торцу ускорителя.

Автоматический сбор истинного кварца [описан здесь](../example-setups/simple-certus-farm.md).
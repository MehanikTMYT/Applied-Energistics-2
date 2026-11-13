---
navigation:
  parent: items-blocks-machines/items-blocks-machines-index.md
  title: МЭ беспроводная точка доступа
  icon: wireless_access_point
  position: 210
categories:
- devices
item_ids:
- ae2:wireless_booster
- ae2:wireless_access_point
---

# МЭ беспроводная точка доступа

<BlockImage id="wireless_access_point" p:state="has_channel" scale="8" />

Предоставляет беспроводной доступ через <ItemLink id="wireless_terminal" />.
Дальность и потребление энергии зависят от количества установленных <ItemLink id="wireless_booster" />.

В сети может быть любое количество беспроводных точек доступа с любым количеством
<ItemLink id="wireless_booster" /> в каждой, что позволяет подбирать оптимальные потребление энергии
и зону покрытия, меняя расположение и оснащение точек.

Требует [канал](../ae2-mechanics/channels.md).

Также используется для привязки [беспроводных терминалов](wireless_terminals.md)

# Беспроводной усилитель

<ItemImage id="wireless_booster" scale="2" />

Увеличивает дальность действия беспроводной точки доступа.

## Рецепты

<RecipeFor id="wireless_access_point" />

<RecipeFor id="wireless_booster" />

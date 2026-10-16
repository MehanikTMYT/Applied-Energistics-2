/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.definitions;

import static appeng.core.definitions.AEItems.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import appeng.api.ids.AEPartIds;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.util.AEColor;
import appeng.core.AppEng;
import appeng.items.parts.ColoredPartItem;
import appeng.items.parts.PartItem;
import appeng.parts.automation.AnnihilationPlanePart;
import appeng.parts.automation.AnnihilationPlanePartItem;
import appeng.parts.automation.EnergyLevelEmitterPart;
import appeng.parts.automation.ExportBusPart;
import appeng.parts.automation.FormationPlanePart;
import appeng.parts.automation.ImportBusPart;
import appeng.parts.automation.StorageLevelEmitterPart;
import appeng.parts.crafting.PatternProviderPart;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import appeng.parts.misc.CableAnchorPart;
import appeng.parts.misc.InterfacePart;
import appeng.parts.misc.InvertedToggleBusPart;
import appeng.parts.misc.ToggleBusPart;
import appeng.parts.networking.CosmicSmartCablePart;
import appeng.parts.networking.CosmicSmartDenseCablePart;
import appeng.parts.networking.CoveredCablePart;
import appeng.parts.networking.CoveredDenseCablePart;
import appeng.parts.networking.EnergyAcceptorPart;
import appeng.parts.networking.GlassCablePart;
import appeng.parts.networking.QuantumSmartCablePart;
import appeng.parts.networking.QuantumSmartDenseCablePart;
import appeng.parts.networking.QuartzFiberPart;
import appeng.parts.networking.SingularitySmartCablePart;
import appeng.parts.networking.SingularitySmartDenseCablePart;
import appeng.parts.networking.SmartCablePart;
import appeng.parts.networking.SmartDenseCablePart;
import appeng.parts.p2p.CosmicMEP2PTunnelPart;
import appeng.parts.p2p.FEP2PTunnelPart;
import appeng.parts.p2p.FluidP2PTunnelPart;
import appeng.parts.p2p.ItemP2PTunnelPart;
import appeng.parts.p2p.LightP2PTunnelPart;
import appeng.parts.p2p.MEP2PTunnelPart;
import appeng.parts.p2p.QuantumMEP2PTunnelPart;
import appeng.parts.p2p.RedstoneP2PTunnelPart;
import appeng.parts.p2p.SingularityMEP2PTunnelPart;
import appeng.parts.reporting.ConversionMonitorPart;
import appeng.parts.reporting.CraftingTerminalPart;
import appeng.parts.reporting.DarkPanelPart;
import appeng.parts.reporting.PanelPart;
import appeng.parts.reporting.PatternAccessTerminalPart;
import appeng.parts.reporting.SemiDarkPanelPart;
import appeng.parts.reporting.StorageMonitorPart;
import appeng.parts.reporting.TerminalPart;
import appeng.parts.storagebus.StorageBusPart;

/**
 * Internal implementation for the API parts
 */
public final class AEParts {
    public static final List<ColoredItemDefinition<?>> COLORED_PARTS = new ArrayList<>();

    // spotless:off
    public static final ColoredItemDefinition<ColoredPartItem<SmartCablePart>> SMART_CABLE = constructColoredDefinition("ME Smart Cable", "smart_cable", SmartCablePart.class, SmartCablePart::new);
    public static final ColoredItemDefinition<ColoredPartItem<CoveredCablePart>> COVERED_CABLE = constructColoredDefinition("ME Covered Cable", "covered_cable", CoveredCablePart.class, CoveredCablePart::new);
    public static final ColoredItemDefinition<ColoredPartItem<GlassCablePart>> GLASS_CABLE = constructColoredDefinition("ME Glass Cable", "glass_cable", GlassCablePart.class, GlassCablePart::new);
    public static final ColoredItemDefinition<ColoredPartItem<CoveredDenseCablePart>> COVERED_DENSE_CABLE = constructColoredDefinition("ME Dense Covered Cable", "covered_dense_cable", CoveredDenseCablePart.class, CoveredDenseCablePart::new);
    public static final ColoredItemDefinition<ColoredPartItem<SmartDenseCablePart>> SMART_DENSE_CABLE = constructColoredDefinition("ME Dense Smart Cable", "smart_dense_cable", SmartDenseCablePart.class, SmartDenseCablePart::new);
    // Round 8 ("HIGH-CAPACITY ME INFRASTRUCTURE"): higher CapacityTier Smart/Dense Smart cables. Geometry,
    // AECableType, box collision and item icon assets are all deliberately identical to their Standard-tier
    // counterparts (see the Round 8 report) - only the channel capacity differs.
    public static final ColoredItemDefinition<ColoredPartItem<QuantumSmartCablePart>> QUANTUM_SMART_CABLE = constructColoredDefinition("ME Quantum Smart Cable", "quantum_smart_cable", QuantumSmartCablePart.class, QuantumSmartCablePart::new);
    public static final ColoredItemDefinition<ColoredPartItem<QuantumSmartDenseCablePart>> QUANTUM_SMART_DENSE_CABLE = constructColoredDefinition("ME Quantum Dense Smart Cable", "quantum_smart_dense_cable", QuantumSmartDenseCablePart.class, QuantumSmartDenseCablePart::new);
    public static final ColoredItemDefinition<ColoredPartItem<SingularitySmartCablePart>> SINGULARITY_SMART_CABLE = constructColoredDefinition("ME Singularity Smart Cable", "singularity_smart_cable", SingularitySmartCablePart.class, SingularitySmartCablePart::new);
    public static final ColoredItemDefinition<ColoredPartItem<SingularitySmartDenseCablePart>> SINGULARITY_SMART_DENSE_CABLE = constructColoredDefinition("ME Singularity Dense Smart Cable", "singularity_smart_dense_cable", SingularitySmartDenseCablePart.class, SingularitySmartDenseCablePart::new);
    public static final ColoredItemDefinition<ColoredPartItem<CosmicSmartCablePart>> COSMIC_SMART_CABLE = constructColoredDefinition("ME Cosmic Smart Cable", "cosmic_smart_cable", CosmicSmartCablePart.class, CosmicSmartCablePart::new);
    public static final ColoredItemDefinition<ColoredPartItem<CosmicSmartDenseCablePart>> COSMIC_SMART_DENSE_CABLE = constructColoredDefinition("ME Cosmic Dense Smart Cable", "cosmic_smart_dense_cable", CosmicSmartDenseCablePart.class, CosmicSmartDenseCablePart::new);
    public static final ItemDefinition<PartItem<QuartzFiberPart>> QUARTZ_FIBER = createPart("Quartz Fiber", AEPartIds.QUARTZ_FIBER, QuartzFiberPart.class, QuartzFiberPart::new);
    public static final ItemDefinition<PartItem<ToggleBusPart>> TOGGLE_BUS = createPart("ME Toggle Bus", AEPartIds.TOGGLE_BUS, ToggleBusPart.class, ToggleBusPart::new);
    public static final ItemDefinition<PartItem<InvertedToggleBusPart>> INVERTED_TOGGLE_BUS = createPart("ME Inverted Toggle Bus", AEPartIds.INVERTED_TOGGLE_BUS, InvertedToggleBusPart.class, InvertedToggleBusPart::new);
    public static final ItemDefinition<PartItem<CableAnchorPart>> CABLE_ANCHOR = createPart("Cable Anchor", AEPartIds.CABLE_ANCHOR, CableAnchorPart.class, CableAnchorPart::new);
    public static final ItemDefinition<PartItem<PanelPart>> MONITOR = createPart("Bright Illuminated Panel", AEPartIds.MONITOR, PanelPart.class, PanelPart::new);
    public static final ItemDefinition<PartItem<SemiDarkPanelPart>> SEMI_DARK_MONITOR = createPart("Illuminated Panel", AEPartIds.SEMI_DARK_MONITOR, SemiDarkPanelPart.class, SemiDarkPanelPart::new);
    public static final ItemDefinition<PartItem<DarkPanelPart>> DARK_MONITOR = createPart("Dark Illuminated Panel", AEPartIds.DARK_MONITOR, DarkPanelPart.class, DarkPanelPart::new);
    public static final ItemDefinition<PartItem<StorageBusPart>> STORAGE_BUS = createPart("ME Storage Bus", AEPartIds.STORAGE_BUS, StorageBusPart.class, StorageBusPart::new);
    public static final ItemDefinition<PartItem<ImportBusPart>> IMPORT_BUS = createPart("ME Import Bus", AEPartIds.IMPORT_BUS, ImportBusPart.class, ImportBusPart::new);
    public static final ItemDefinition<PartItem<ExportBusPart>> EXPORT_BUS = createPart("ME Export Bus", AEPartIds.EXPORT_BUS, ExportBusPart.class, ExportBusPart::new);
    public static final ItemDefinition<PartItem<StorageLevelEmitterPart>> LEVEL_EMITTER = createPart("ME Level Emitter", AEPartIds.LEVEL_EMITTER, StorageLevelEmitterPart.class, StorageLevelEmitterPart::new);
    public static final ItemDefinition<PartItem<EnergyLevelEmitterPart>> ENERGY_LEVEL_EMITTER = createPart("ME Energy Level Emitter", AEPartIds.ENERGY_LEVEL_EMITTER, EnergyLevelEmitterPart.class, EnergyLevelEmitterPart::new);
    public static final ItemDefinition<PartItem<AnnihilationPlanePart>> ANNIHILATION_PLANE = createCustomPartItem("ME Annihilation Plane", AEPartIds.ANNIHILATION_PLANE, AnnihilationPlanePart.class, AnnihilationPlanePartItem::new);
    public static final ItemDefinition<PartItem<FormationPlanePart>> FORMATION_PLANE = createPart("ME Formation Plane", AEPartIds.FORMATION_PLANE, FormationPlanePart.class, FormationPlanePart::new);
    public static final ItemDefinition<PartItem<PatternEncodingTerminalPart>> PATTERN_ENCODING_TERMINAL = createPart("ME Pattern Encoding Terminal", AEPartIds.PATTERN_ENCODING_TERMINAL, PatternEncodingTerminalPart.class, PatternEncodingTerminalPart::new);
    public static final ItemDefinition<PartItem<CraftingTerminalPart>> CRAFTING_TERMINAL = createPart("ME Crafting Terminal", AEPartIds.CRAFTING_TERMINAL, CraftingTerminalPart.class, CraftingTerminalPart::new);
    public static final ItemDefinition<PartItem<TerminalPart>> TERMINAL = createPart("ME Terminal", AEPartIds.TERMINAL, TerminalPart.class, TerminalPart::new);
    public static final ItemDefinition<PartItem<StorageMonitorPart>> STORAGE_MONITOR = createPart("ME Storage Monitor", AEPartIds.STORAGE_MONITOR, StorageMonitorPart.class, StorageMonitorPart::new);
    public static final ItemDefinition<PartItem<ConversionMonitorPart>> CONVERSION_MONITOR = createPart("ME Conversion Monitor", AEPartIds.CONVERSION_MONITOR, ConversionMonitorPart.class, ConversionMonitorPart::new);
    public static final ItemDefinition<PartItem<PatternProviderPart>> PATTERN_PROVIDER = createPart("ME Pattern Provider", AEPartIds.PATTERN_PROVIDER, PatternProviderPart.class, PatternProviderPart::new);
    public static final ItemDefinition<PartItem<InterfacePart>> INTERFACE = createPart("ME Interface", AEPartIds.INTERFACE, InterfacePart.class, InterfacePart::new);
    public static final ItemDefinition<PartItem<PatternAccessTerminalPart>> PATTERN_ACCESS_TERMINAL = createPart("ME Pattern Access Terminal", AEPartIds.PATTERN_ACCESS_TERMINAL, PatternAccessTerminalPart.class, PatternAccessTerminalPart::new);
    public static final ItemDefinition<PartItem<EnergyAcceptorPart>> ENERGY_ACCEPTOR = createPart("Energy Acceptor", AEPartIds.ENERGY_ACCEPTOR, EnergyAcceptorPart.class, EnergyAcceptorPart::new);
    public static final ItemDefinition<PartItem<MEP2PTunnelPart>> ME_P2P_TUNNEL = createPart("ME P2P Tunnel", AEPartIds.ME_P2P_TUNNEL, MEP2PTunnelPart.class, MEP2PTunnelPart::new);
    // Round 9 ("HIGH-TIER ME P2P"): higher CapacityTier ME P2P tunnels. Same MEP2PTunnelPart mechanism, geometry and
    // rendering (see the Round 9 report) - only the outer node's channel ceiling differs.
    public static final ItemDefinition<PartItem<QuantumMEP2PTunnelPart>> QUANTUM_ME_P2P_TUNNEL = createPart("ME Quantum P2P Tunnel", AEPartIds.QUANTUM_ME_P2P_TUNNEL, QuantumMEP2PTunnelPart.class, QuantumMEP2PTunnelPart::new);
    public static final ItemDefinition<PartItem<SingularityMEP2PTunnelPart>> SINGULARITY_ME_P2P_TUNNEL = createPart("ME Singularity P2P Tunnel", AEPartIds.SINGULARITY_ME_P2P_TUNNEL, SingularityMEP2PTunnelPart.class, SingularityMEP2PTunnelPart::new);
    public static final ItemDefinition<PartItem<CosmicMEP2PTunnelPart>> COSMIC_ME_P2P_TUNNEL = createPart("ME Cosmic P2P Tunnel", AEPartIds.COSMIC_ME_P2P_TUNNEL, CosmicMEP2PTunnelPart.class, CosmicMEP2PTunnelPart::new);
    public static final ItemDefinition<PartItem<RedstoneP2PTunnelPart>> REDSTONE_P2P_TUNNEL = createPart("Redstone P2P Tunnel", AEPartIds.REDSTONE_P2P_TUNNEL, RedstoneP2PTunnelPart.class, RedstoneP2PTunnelPart::new);
    public static final ItemDefinition<PartItem<ItemP2PTunnelPart>> ITEM_P2P_TUNNEL = createPart("Item P2P Tunnel", AEPartIds.ITEM_P2P_TUNNEL, ItemP2PTunnelPart.class, ItemP2PTunnelPart::new);
    public static final ItemDefinition<PartItem<FluidP2PTunnelPart>> FLUID_P2P_TUNNEL = createPart("Fluid P2P Tunnel", AEPartIds.FLUID_P2P_TUNNEL, FluidP2PTunnelPart.class, FluidP2PTunnelPart::new);
    public static final ItemDefinition<PartItem<FEP2PTunnelPart>> FE_P2P_TUNNEL = createPart("Energy P2P Tunnel", AEPartIds.FE_P2P_TUNNEL, FEP2PTunnelPart.class, FEP2PTunnelPart::new);
    public static final ItemDefinition<PartItem<LightP2PTunnelPart>> LIGHT_P2P_TUNNEL = createPart("Light P2P Tunnel", AEPartIds.LIGHT_P2P_TUNNEL, LightP2PTunnelPart.class, LightP2PTunnelPart::new);
    // spotless:on

    private static <T extends IPart> ItemDefinition<PartItem<T>> createPart(
            String englishName,
            Identifier id,
            Class<T> partClass,
            Function<IPartItem<T>, T> factory) {

        return item(englishName, id, props -> new PartItem<>(props, partClass, factory));
    }

    private static <T extends IPart> ItemDefinition<PartItem<T>> createCustomPartItem(
            String englishName,
            Identifier id,
            Class<T> partClass,
            Function<Item.Properties, PartItem<T>> factory) {

        return item(englishName, id, factory);
    }

    private static <T extends IPart> ColoredItemDefinition<ColoredPartItem<T>> constructColoredDefinition(
            String nameSuffix,
            String idSuffix,
            Class<T> partClass,
            Function<ColoredPartItem<T>, T> factory) {

        var definition = new ColoredItemDefinition<ColoredPartItem<T>>();
        for (AEColor color : AEColor.values()) {
            var id = color.registryPrefix + '_' + idSuffix;
            var name = color.englishName + " " + nameSuffix;

            var itemDef = item(name, AppEng.makeId(id),
                    props -> new ColoredPartItem<>(props, partClass, factory, color));

            definition.add(color, AppEng.makeId(id), itemDef);
        }

        COLORED_PARTS.add(definition);

        return definition;
    }

    // Used to control in which order static constructors are called
    public static void init() {
    }

}

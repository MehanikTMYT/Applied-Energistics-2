/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2026, AlgorithmX2, All rights reserved.
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

package appeng.parts.p2p;

import appeng.api.networking.pathing.CapacityTier;
import appeng.api.parts.IPartItem;

/**
 * Round 9 ("HIGH-TIER ME P2P"): an ME P2P tunnel built on Cosmic-tier transport infrastructure - a transport ceiling of
 * 256 channels instead of the Standard tier's 32 (see {@link CapacityTier#COSMIC}), applied to the tunnel's outer node
 * only (see {@link MEP2PTunnelPart#getOuterNode()}). See {@link QuantumMEP2PTunnelPart} for the full design rationale -
 * identical pattern, different tier.
 */
public class CosmicMEP2PTunnelPart extends MEP2PTunnelPart {
    public CosmicMEP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem);
        getOuterNode().setCapacityTier(CapacityTier.COSMIC);
    }
}

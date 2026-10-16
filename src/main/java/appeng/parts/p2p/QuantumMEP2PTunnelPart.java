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
 * Round 9 ("HIGH-TIER ME P2P"): an ME P2P tunnel built on Quantum-tier transport infrastructure - a transport ceiling
 * of 64 channels instead of the Standard tier's 32 (see {@link CapacityTier#QUANTUM}), applied to the tunnel's outer
 * node only (see {@link MEP2PTunnelPart#getOuterNode()} - P2P is channel transport, never channel generation; the main
 * node stays Standard-tier, it is not the bottleneck). Deliberately reuses {@link MEP2PTunnelPart}'s connection
 * bookkeeping, security, and rendering unchanged - only the outer node's capacity tier differs, exactly the same
 * pattern Round 8 used for tier cable parts. Visuals are a placeholder (reuses the Standard ME P2P tunnel's
 * textures/models) pending dedicated tier art (HUMAN VISUAL DESIGN PENDING).
 */
public class QuantumMEP2PTunnelPart extends MEP2PTunnelPart {
    public QuantumMEP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem);
        getOuterNode().setCapacityTier(CapacityTier.QUANTUM);
    }
}

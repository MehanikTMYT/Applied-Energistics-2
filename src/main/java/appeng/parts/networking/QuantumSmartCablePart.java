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

package appeng.parts.networking;

import appeng.api.networking.pathing.CapacityTier;
import appeng.items.parts.ColoredPartItem;

/**
 * Round 8 ("HIGH-CAPACITY ME INFRASTRUCTURE"): a Smart Cable built on Quantum-tier infrastructure - carries 16 channels
 * instead of the Standard tier's 8 (see {@link CapacityTier#QUANTUM}). Deliberately reuses {@link SmartCablePart}'s
 * geometry, box collision and {@code AECableType.SMART} connection rendering unchanged (see the Round 8 archaeology
 * report: capacity and rendering are fully decoupled in AE2's cable model) - only the capacity tier differs. Visuals
 * are a placeholder (reuses the Standard Smart Cable's textures/models) pending dedicated tier art (HUMAN VISUAL DESIGN
 * PENDING).
 */
public class QuantumSmartCablePart extends SmartCablePart {
    public QuantumSmartCablePart(ColoredPartItem<?> partItem) {
        super(partItem);
        this.getMainNode().setCapacityTier(CapacityTier.QUANTUM);
    }
}

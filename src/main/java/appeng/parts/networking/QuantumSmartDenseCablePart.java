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
 * Round 8: a Dense Smart Cable built on Quantum-tier infrastructure - carries 64 channels (4x its own tier's Smart
 * Cable, see {@link CapacityTier#QUANTUM}). See {@link QuantumSmartCablePart} for the shared design rationale;
 * {@code DenseCablePart} (the superclass of {@link SmartDenseCablePart}) already sets
 * {@link appeng.api.networking.GridFlags#DENSE_CAPACITY}.
 */
public class QuantumSmartDenseCablePart extends SmartDenseCablePart {
    public QuantumSmartDenseCablePart(ColoredPartItem<?> partItem) {
        super(partItem);
        this.getMainNode().setCapacityTier(CapacityTier.QUANTUM);
    }
}

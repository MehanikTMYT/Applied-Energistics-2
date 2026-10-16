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
 * Round 8: a Dense Smart Cable built on Cosmic-tier infrastructure - carries 256 channels, the design ceiling for this
 * round (see {@link CapacityTier#COSMIC}). See {@link QuantumSmartCablePart} for the shared design rationale.
 */
public class CosmicSmartDenseCablePart extends SmartDenseCablePart {
    public CosmicSmartDenseCablePart(ColoredPartItem<?> partItem) {
        super(partItem);
        this.getMainNode().setCapacityTier(CapacityTier.COSMIC);
    }
}

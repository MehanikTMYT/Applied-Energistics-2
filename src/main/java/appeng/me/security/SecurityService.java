/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2026, AlgorithmX2, TeamAppliedEnergistics, All rights reserved.
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

package appeng.me.security;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;

/**
 * Default implementation of {@link ISecurityService}.
 * <p/>
 * <b>The fail-closed grid-merge guard, explained:</b> AE2 never throws or aborts inside grid formation
 * ({@code GridConnection.create}/{@code mergeGrids}) to enforce security - unwinding an already-placed physical
 * block/cable there would be far too invasive (see e.g. {@code CableBusContainer}, {@code MEP2PTunnelPart},
 * {@code QuantumCluster}, {@code ToggleBusPart} which all assume placement always succeeds). Instead, this service
 * piggy-backs on the grid framework's existing, generic per-node persistence contract
 * ({@link IGridServiceProvider#addNode}/{@link IGridServiceProvider#removeNode}/{@link IGridServiceProvider#saveNodeData})
 * which already fires for every node on every merge and split (see {@code GridNode#setGrid}, {@code Grid#add},
 * {@code Grid#remove}).
 * <p/>
 * Every {@link IGridNode} that has ever been part of a {@link SecurityDomainState#SECURED} network carries its own
 * bound {@link SecurityDomainId}, persisted through the same {@code savedData} tag other grid services use for per-node
 * state (this is what already survives {@code BlockEntity} save/load, chunk unload, and server restart - see
 * {@link #saveNodeData}/{@link #addNode}). This service simply counts, per grid, how many <em>distinct</em> non-null
 * domain ids are currently represented among its nodes ({@link #domainCounts}):
 * <ul>
 * <li>zero -&gt; {@link SecurityDomainState#LEGACY} (no domain ever bound - fully open, AE2-compatible)</li>
 * <li>exactly one -&gt; {@link SecurityDomainState#SECURED}, bound to that domain</li>
 * <li>two or more -&gt; {@link SecurityDomainState#CONFLICT} - two previously-independent secured networks got
 * physically merged. Every permission check fails until the connection is undone, which naturally splits the grid back
 * apart via the existing split logic; since each node keeps carrying its own domain id the whole time, both halves
 * recover their own domain state independently with no special-casing needed here.</li>
 * </ul>
 * A brand-new node (never previously part of any grid, {@code savedData == null}) that joins a
 * {@link SecurityDomainState#SECURED} grid automatically inherits that grid's domain id - it is now physically part of
 * a secured network and future merges against it must respect that. This does <strong>not</strong> grant it any
 * permissions; it only affects grid-merge conflict detection. A node joining a {@link SecurityDomainState#LEGACY} or
 * {@link SecurityDomainState#CONFLICT} grid is left unbound.
 */
public class SecurityService implements ISecurityService, IGridServiceProvider {
    private static final String TAG_DOMAIN = "aeSecDomain";

    private final IGrid grid;

    private final Map<IGridNode, SecurityDomainId> nodeDomains = new IdentityHashMap<>();
    private final Multiset<SecurityDomainId> domainCounts = HashMultiset.create();

    public SecurityService(IGrid grid) {
        this.grid = grid;
    }

    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        SecurityDomainId domain = null;

        if (savedData != null) {
            domain = savedData.read(TAG_DOMAIN, SecurityDomainId.CODEC).orElse(null);
        } else if (domainCounts.elementSet().size() == 1) {
            // Brand new node joining an already-secured network: inherit its domain so that future merges
            // against a *different* secured network are correctly detected as conflicts.
            domain = domainCounts.elementSet().iterator().next();
        }

        if (domain != null) {
            nodeDomains.put(node, domain);
            domainCounts.add(domain);
        }
    }

    @Override
    public void removeNode(IGridNode node) {
        var domain = nodeDomains.remove(node);
        if (domain != null) {
            domainCounts.remove(domain);
        }
    }

    @Override
    public void saveNodeData(IGridNode node, CompoundTag savedData) {
        var domain = nodeDomains.get(node);
        if (domain != null) {
            savedData.store(TAG_DOMAIN, SecurityDomainId.CODEC, domain);
        }
    }

    @Override
    public SecurityDomainState getState() {
        return switch (domainCounts.elementSet().size()) {
            case 0 -> SecurityDomainState.LEGACY;
            case 1 -> SecurityDomainState.SECURED;
            default -> SecurityDomainState.CONFLICT;
        };
    }

    @Override
    public Optional<SecurityDomainId> getDomainId() {
        var it = domainCounts.elementSet().iterator();
        return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
    }

    @Nullable
    @Override
    public SecurityDomainId bootstrapDomain(UUID ownerId) {
        var state = getState();
        if (state == SecurityDomainState.CONFLICT) {
            return null;
        }
        if (state == SecurityDomainState.SECURED) {
            return getDomainId().orElse(null);
        }

        var registry = getRegistry();
        if (registry == null) {
            // Client-side or otherwise not backed by a server level; nothing to persist to.
            return null;
        }

        var domain = registry.createDomain(ownerId);
        registry.markDirty();

        for (var node : grid.getNodes()) {
            nodeDomains.put(node, domain.id());
            domainCounts.add(domain.id());
        }

        return domain.id();
    }

    @Override
    public boolean hasPermission(IActionSource source, SecurityPermissions permission) {
        if (source == null) {
            // Defensive fail-closed guard: nothing in the IActionSource contract makes `source` nullable (no
            // caller, including addon mods calling through the public ISecurityService/MEStorage API, is ever
            // supposed to pass null - IActionSource.empty() is the documented way to say "no specific actor"), so
            // this should never happen. But if some call site ever does it anyway, do not let that silently
            // escalate into the trusted-internal bypass below (that would let a caller bug masquerade as a
            // deliberate grant of full access) and do not NPE either. Instead, derive the answer from this method's
            // own existing invariants: LEGACY networks are unconditionally open to any source (see the `state ==
            // LEGACY` branch below), so stay consistent with that; everywhere else (SECURED/CONFLICT), fail closed,
            // mirroring the `registry == null || domainId == null` fail-closed guard further down.
            return getState() == SecurityDomainState.LEGACY;
        }

        // Round 27 (FINDING-1): an actorless source is NOT a trusted-internal bypass any more.
        //
        // It used to `return true` right here, before the state check, on the theory that a source naming neither a
        // player nor a machine could only be AE2's own bookkeeping. Round 26 disproved the premise: `IActionSource` is
        // a public interface with a public `empty()` factory, so *any* caller - notably anything holding a storage
        // block capability - can present an actorless source, and did so to reach a SECURED network with no grant at
        // all. Round 26 also measured that no production AE2 path actually needs the bypass: every internal
        // network-storage mutation and every craft submission carries either a MachineSource or a player source (the
        // two remaining `new BaseActionSource()` sites in production, ConfigInventory#addFilter and
        // ColorApplicatorItem, operate on a config inventory and on a portable cell, neither of which is behind this
        // service). So the bypass is deleted rather than resealed - no trusted bypass is safer than a sealed one.
        //
        // What happens to an actorless source now falls out of the logic below without a special case:
        // LEGACY stays unconditionally open (compatibility preserved), CONFLICT stays closed, and on SECURED
        // `resolvePrincipal` returns null, which lands on the "unidentified" branch - i.e. exactly and only whatever
        // was explicitly granted to EVERYONE. That is the owner's Decision 1A: actorless != root, actorless !=
        // trusted internal, but an explicit public grant keeps working.

        var state = getState();
        if (state == SecurityDomainState.LEGACY) {
            return true;
        }
        if (state == SecurityDomainState.CONFLICT) {
            return false;
        }

        var registry = getRegistry();
        var domainId = getDomainId().orElse(null);
        if (registry == null || domainId == null) {
            // Should not happen while SECURED, but fail closed if it somehow does.
            return false;
        }
        var domain = registry.getDomain(domainId);
        if (domain == null) {
            return false;
        }

        var principal = resolvePrincipal(source);
        if (principal instanceof PrincipalId.Player player && domain.isOwner(player.id())) {
            return true;
        }
        if (principal == null) {
            // Unidentified machine: only what's granted to EVERYONE applies.
            return domain.getPermissions(PrincipalId.EVERYONE).contains(permission);
        }

        return domain.getEffectivePermissions(principal).contains(permission);
    }

    /**
     * Resolves an {@link IActionSource} to the {@link PrincipalId} it should be checked as. Returns {@code null} for a
     * machine action whose source does not expose a stable {@link IDeviceIdentity} - such a source can still act, but
     * only within whatever is granted to {@link PrincipalId#EVERYONE}.
     */
    @Nullable
    private PrincipalId resolvePrincipal(IActionSource source) {
        var player = source.player().orElse(null);
        if (player != null) {
            return PrincipalId.ofPlayer(player.getUUID());
        }

        var machine = source.machine().orElse(null);
        if (machine != null) {
            var deviceId = resolveDeviceId(machine);
            if (deviceId != null) {
                return PrincipalId.ofDevice(deviceId);
            }
        }

        return null;
    }

    /**
     * Resolves the {@link PrincipalId.Device} id of a machine source, or {@code null} if the source does not stand for
     * a real device on <em>this</em> grid.
     * <p/>
     * <b>Round 27 (FINDING-3).</b> This used to start with {@code machine instanceof IDeviceIdentity ->
     * identity.getDeviceId()}, which took the caller's word for it. Round 26 proved that trivially forgeable: both
     * {@link IActionHost} and {@link IDeviceIdentity} are public API, {@code getActionableNode()} is allowed to return
     * {@code null}, and a victim's device id is not a secret either (it is readable off a neighbouring block entity
     * through the same public interface). So any caller could hand in a two-method object claiming a privileged id and
     * be authorized as that device.
     * <p/>
     * The identity is therefore no longer read off the object the caller passed in. It is read off the owner of a real
     * grid node, and that node must be attached to the very grid this security service belongs to:
     * <ol>
     * <li>{@code getActionableNode()} must return a node - a bare claim with no node is not a device. This alone
     * defeats the Round 26 forgery, which returned {@code null}.</li>
     * <li>the id comes from {@link IGridNode#getOwner()}, i.e. from the object AE2 registered as the node's host, not
     * from whatever wrapper was passed to {@code ofMachine(...)}.</li>
     * </ol>
     * A third condition was considered and deliberately <em>not</em> shipped: requiring {@code node.getGrid() == grid},
     * so that a machine on some other network could not act on this one. It is defensible and cheap, but its security
     * value turned out to be modest - a device from another network still needs a grant in <em>this</em> domain to do
     * anything, and it does not stop the residual forgery described below - while validating it would have required
     * rewiring every unit-test fixture into a single shared grid. Shipping a half-validated extra condition on a
     * security path is worse than leaving it out and saying so. All five in-tree device actors keep working unchanged,
     * because for each of them the node's owner <em>is</em> the identity holder: parts own their own node
     * ({@code AEBasePart#createManagedNode(this, ...)}), and the block entities that build a lambda-backed
     * {@code MachineSource(mainNode::getNode)} (Interface, Pattern Provider) are the owners of the node that lambda
     * returns. The same is true of a legitimate third-party device such as the AE2<->MineColonies bridge block entity,
     * which owns a real {@code GridHelper.createManagedNode(this, ...)}.
     * <p/>
     * <b>This is a barrier, not authentication - stated plainly.</b> {@code GridHelper.createManagedNode} is public, so
     * a determined addon can still register a real node whose owner returns some other device's id. What changed is the
     * cost: forgery now requires an actual machine, placed and connected to the target network, instead of a throwaway
     * object constructed anywhere. Closing the remaining gap requires AE2 itself to mint and own device ids instead of
     * accepting them from the host, which changes the public {@link IDeviceIdentity} contract and is an owner-level API
     * decision, not something this round may take unilaterally.
     */
    @Nullable
    private UUID resolveDeviceId(IActionHost machine) {
        var node = machine.getActionableNode();
        if (node == null) {
            return null;
        }

        var owner = node.getOwner();

        // Round 29: where a registry binding exists, it - not the machine - says who this is.
        //
        // The lookup is deliberately pure (brief §46): binding happens once, in the device's own lifecycle, so a
        // permission check never mints anything. It is also keyed by physical position rather than by anything the
        // machine reports, which is what makes a copied NBT claim powerless: the copy sits somewhere else, so it
        // resolves to its own binding or to none.
        //
        // Devices that have not been migrated yet fall through to the pre-Round-29 path below. That is the deliberate
        // shape of a prototype rollout: the Interface is authoritative today, the remaining actors keep working
        // exactly as they did until they are migrated in turn, and nothing silently loses its identity in between.
        // Round 32: physical identity, resolved by AE2, is the only thing that makes a device principal.
        //
        // A resolvable MachineKey means AE2 can actually locate this owner in the world - it is a block entity or a
        // part that was placed. Every one of AE2's seven DEVICE actors is one of those, and so is any addon device
        // registered through the public DeviceRegistration contract. For all of them the registry's answer is final,
        // including "nothing": an unregistered machine simply has no principal and is left with whatever EVERYONE was
        // granted. Its own stored id is never consulted, so a copied NBT, a mutated field or a claim of somebody
        // else's id changes nothing.
        //
        // An owner AE2 cannot locate gets no principal at all. Round 31 still fell back to the host's own claim here,
        // which was the last place a caller could assert an identity; the only things that ever reached it were test
        // doubles, and those now model a real placed device instead. Removing it is what takes
        // HOST_UUID_DIRECT_SECURITY_AUTHORITY_PATHS from 1 to 0.
        var key = MachineKey.of(owner);
        if (key == null) {
            return null;
        }

        var level = node.getLevel();
        if (level == null) {
            return null;
        }

        return registryFor(level).getAuthenticated(key);
    }

    /**
     * The device registry this service consults. Mirrors {@link #getRegistry()}: normally the world's, but overridable
     * so unit tests can exercise the real resolution path against an in-memory registry instead of a running server.
     */
    private DevicePrincipalRegistry registryFor(net.minecraft.server.level.ServerLevel level) {
        return testDeviceRegistryOverride != null ? testDeviceRegistryOverride
                : DevicePrincipalRegistry.get(level);
    }

    @Nullable
    private DevicePrincipalRegistry testDeviceRegistryOverride;

    void setDeviceRegistryForTesting(DevicePrincipalRegistry registry) {
        this.testDeviceRegistryOverride = registry;
    }

    /**
     * Overrides the {@link SecurityDomainRegistry} used by this service, bypassing the normal
     * {@code ServerLevel -> MinecraftServer -> SavedData} lookup. Package-private: exists solely so unit tests can
     * exercise this service's ACL logic against a plain in-memory {@link SecurityDomainRegistry} without needing a
     * running Minecraft server.
     */
    @Nullable
    private SecurityDomainRegistry testRegistryOverride;

    void setRegistryForTesting(SecurityDomainRegistry registry) {
        this.testRegistryOverride = registry;
    }

    @Nullable
    private SecurityDomainRegistry getRegistry() {
        if (testRegistryOverride != null) {
            return testRegistryOverride;
        }

        var pivot = grid.getPivot();
        if (pivot == null) {
            return null;
        }
        var level = pivot.getLevel();
        if (level == null) {
            return null;
        }
        return SecurityDomainRegistry.get(level);
    }
}
